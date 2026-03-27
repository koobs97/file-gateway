package com.file.gateway.file.service;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.file.dto.FileDetailResponse;
import com.file.gateway.file.dto.FileEventResponse;
import com.file.gateway.file.dto.FileStatisticsResponse;
import com.file.gateway.file.dto.FileUploadResponse;
import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import com.file.gateway.file.entity.StorageType;
import com.file.gateway.file.repository.FileMetadataRepository;
import com.file.gateway.process.event.FileEventService;
import com.file.gateway.process.event.entity.EventType;
import com.file.gateway.process.event.entity.ProcessStatus;
import com.file.gateway.process.event.entity.ProcessStep;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.process.worker.FileUploadedEvent;
import com.file.gateway.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * FileService
 * - 파일 업로드, 조회, 삭제, 다운로드, 이벤트 이력, 통계 등 핵심 비즈니스 로직을 담당한다.
 * - 파일 저장은 스트리밍 방식으로 처리하여 메모리 적재를 최소화한다.
 * - 업로드 완료 후 Spring ApplicationEvent를 발행하여 CDR 처리를 비동기로 트리거한다.
 * - privileged 플래그(ADMIN·AUDITOR)에 따라 전체 또는 업로더 범위로 접근을 제어한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    /** 파일 메타데이터 CRUD 및 조건 검색을 처리하는 리포지토리 */
    private final FileMetadataRepository fileMetadataRepository;

    /** 파일 이벤트 로그 조회 리포지토리 */
    private final FileEventRepository fileEventRepository;

    /** 파일 저장소 추상 인터페이스 (현재 로컬 구현체 사용) */
    private final StorageService storageService;

    /** 파일 유효성 검사 컴포넌트 */
    private final FileValidator fileValidator;

    /** 파일 이벤트 및 처리 로그 기록 서비스 */
    private final FileEventService fileEventService;

    /** CDR 처리를 비동기로 트리거하기 위한 Spring 이벤트 퍼블리셔 */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 파일을 업로드하고 CDR 처리를 비동기로 트리거한다.
     * 파일 유효성 검사 → 스트리밍 저장 → 메타데이터 저장 → 이벤트 발행 순으로 처리된다.
     * 트랜잭션 커밋 이후 SanitizeEventListener가 CDR 처리를 수신한다.
     *
     * @param file         업로드할 MultipartFile
     * @param uploaderName 업로드를 요청한 사용자명
     * @return 저장된 파일의 ID, 원본명, 크기, 상태를 담은 업로드 응답 DTO
     * @throws IOException 파일 스트리밍 저장 중 I/O 오류 발생 시
     */
    @Transactional
    public FileUploadResponse upload(MultipartFile file, String uploaderName) throws IOException {
        fileValidator.validate(file);

        String ext = fileValidator.extractExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "." + ext;

        // 스트리밍 저장 (메모리 적재 최소화)
        String storagePath = storageService.save(file.getInputStream(), storedName, "original");

        FileMetadata metadata = FileMetadata.builder()
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .storagePath(storagePath)
                .storageType(StorageType.LOCAL)
                .status(FileStatus.PROCESSING)
                .uploaderName(uploaderName)
                .build();
        fileMetadataRepository.save(metadata);

        fileEventService.publishEvent(metadata, EventType.QUEUED, null);
        fileEventService.saveProcessLog(metadata, ProcessStep.UPLOAD, ProcessStatus.SUCCESS, "업로드 완료");

        // 트랜잭션 커밋 후 CDR 처리 트리거 (SanitizeEventListener가 수신)
        eventPublisher.publishEvent(new FileUploadedEvent(metadata.getId(), file.getSize()));

        log.info("파일 업로드 완료: id={}, name={}, status=PROCESSING", metadata.getId(), metadata.getOriginalName());
        return FileUploadResponse.from(metadata);
    }

    /**
     * 파일 목록을 키워드·상태 조건으로 검색하여 페이징 응답을 반환한다.
     * privileged 사용자(ADMIN·AUDITOR)는 전체 파일을, 일반 사용자는 본인 업로드 파일만 조회한다.
     *
     * @param keyword      파일명 검색 키워드 (null이면 전체 조회)
     * @param statusStr    상태 필터 문자열 (null이면 전체, 유효하지 않으면 무시)
     * @param pageable     페이징 및 정렬 정보
     * @param uploaderName 일반 사용자의 경우 본인 파일 필터링에 사용되는 사용자명
     * @param privileged   ADMIN·AUDITOR 여부 (true이면 전체 조회)
     * @return 파일 상세 정보 페이지
     */
    public Page<FileDetailResponse> getFiles(String keyword, String statusStr, Pageable pageable,
                                              String uploaderName, boolean privileged) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = statusStr != null && !statusStr.isBlank();
        FileStatus status = parseStatus(statusStr, hasStatus);

        if (privileged) {
            if (!hasKeyword && !hasStatus) {
                return fileMetadataRepository.findAllByDeletedAtIsNull(pageable).map(FileDetailResponse::from);
            }
            return fileMetadataRepository
                    .searchFiles(status, hasKeyword ? keyword.trim() : null, pageable)
                    .map(FileDetailResponse::from);
        } else {
            if (!hasKeyword && !hasStatus) {
                return fileMetadataRepository
                        .findAllByDeletedAtIsNullAndUploaderName(uploaderName, pageable)
                        .map(FileDetailResponse::from);
            }
            return fileMetadataRepository
                    .searchFilesByUploader(uploaderName, status, hasKeyword ? keyword.trim() : null, pageable)
                    .map(FileDetailResponse::from);
        }
    }

    /**
     * 특정 파일의 상세 정보를 단건 조회한다.
     *
     * @param id           조회할 파일 ID
     * @param uploaderName 일반 사용자의 경우 접근 제어에 사용되는 사용자명
     * @param privileged   ADMIN·AUDITOR 여부
     * @return 파일 상세 정보 DTO
     */
    public FileDetailResponse getFile(Long id, String uploaderName, boolean privileged) {
        return FileDetailResponse.from(findAccessibleFile(id, uploaderName, privileged));
    }

    /**
     * 파일을 논리 삭제(soft delete) 처리한다. deletedAt 시각을 기록하며 물리 파일은 유지된다.
     *
     * @param id 삭제할 파일 ID
     */
    @Transactional
    public void deleteFile(Long id) {
        FileMetadata file = findActiveFile(id);
        file.delete();
        log.info("파일 삭제(soft delete): id={}", id);
    }

    /**
     * 무해화 처리가 완료된 파일의 InputStream 리소스를 반환한다.
     * DONE 상태이고 sanitized 디렉토리에 파일이 존재하는 경우에만 다운로드 가능하다.
     *
     * @param id           다운로드할 파일 ID
     * @param uploaderName 일반 사용자의 경우 접근 제어에 사용되는 사용자명
     * @param privileged   ADMIN·AUDITOR 여부
     * @return 무해화 파일의 InputStream을 감싼 Resource 객체
     * @throws BusinessException 처리 미완료 상태이거나 무해화 파일이 존재하지 않을 경우
     * @throws IOException       파일 스트림 열기 중 I/O 오류 발생 시
     */
    public Resource downloadSanitizedFile(Long id, String uploaderName, boolean privileged) throws IOException {
        FileMetadata file = findAccessibleFile(id, uploaderName, privileged);
        if (file.getStatus() != FileStatus.DONE) {
            throw new BusinessException(ErrorCode.PROCESS_FAILED,
                    "무해화 처리가 완료되지 않았습니다. 현재 상태: " + file.getStatus().name());
        }
        String sanitizedPath = storageService.resolvePath(file.getStoredName(), "sanitized");
        if (!storageService.exists(sanitizedPath)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "무해화 파일을 찾을 수 없습니다.");
        }
        return new InputStreamResource(storageService.load(sanitizedPath));
    }

    /**
     * 특정 파일에 기록된 이벤트 이력을 생성 시각 오름차순으로 조회한다.
     *
     * @param id 이벤트를 조회할 파일 ID
     * @return 파일 이벤트 응답 DTO 목록
     */
    public List<FileEventResponse> getEvents(Long id) {
        findActiveFile(id); // 존재 여부 확인
        return fileEventRepository.findAllByFileIdOrderByCreatedAtAsc(id)
                .stream()
                .map(FileEventResponse::from)
                .toList();
    }

    /**
     * 파일 처리 통계를 집계하여 반환한다.
     * privileged 사용자는 전체 통계를, 일반 사용자는 본인 업로드 파일 기준 통계를 조회한다.
     *
     * @param uploaderName 일반 사용자의 경우 통계 범위를 제한하는 사용자명 (privileged이면 무시)
     * @param privileged   ADMIN·AUDITOR 여부 (true이면 전체 통계 반환)
     * @return 총 건수, 완료/실패/처리 중 건수, 삭제 건수, 성공률을 담은 통계 응답 DTO
     */
    public FileStatisticsResponse getStatistics(String uploaderName, boolean privileged) {
        if (privileged) {
            long done       = fileMetadataRepository.countByStatusAndDeletedAtIsNull(FileStatus.DONE);
            long fail       = fileMetadataRepository.countByStatusAndDeletedAtIsNull(FileStatus.FAIL);
            long processing = fileMetadataRepository.countByStatusAndDeletedAtIsNull(FileStatus.PROCESSING);
            long total      = fileMetadataRepository.countByDeletedAtIsNull();
            long deleted    = fileMetadataRepository.countByDeletedAtIsNotNull();
            return FileStatisticsResponse.of(total, done, fail, processing, deleted);
        } else {
            long done       = fileMetadataRepository.countByStatusAndDeletedAtIsNullAndUploaderName(FileStatus.DONE, uploaderName);
            long fail       = fileMetadataRepository.countByStatusAndDeletedAtIsNullAndUploaderName(FileStatus.FAIL, uploaderName);
            long processing = fileMetadataRepository.countByStatusAndDeletedAtIsNullAndUploaderName(FileStatus.PROCESSING, uploaderName);
            long total      = fileMetadataRepository.countByDeletedAtIsNullAndUploaderName(uploaderName);
            return FileStatisticsResponse.of(total, done, fail, processing, 0);
        }
    }

    /**
     * 논리 삭제되지 않은 파일을 ID로 조회한다.
     *
     * @param id 조회할 파일 ID
     * @return 활성 상태의 FileMetadata 엔티티
     * @throws BusinessException 파일이 존재하지 않거나 삭제된 경우
     */
    private FileMetadata findActiveFile(Long id) {
        return fileMetadataRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * 접근 권한이 있는 파일을 ID로 조회한다.
     * privileged 사용자는 전체 파일에, 일반 사용자는 본인 업로드 파일에만 접근 가능하다.
     *
     * @param id           조회할 파일 ID
     * @param uploaderName 일반 사용자의 경우 소유권 검증에 사용되는 사용자명
     * @param privileged   ADMIN·AUDITOR 여부
     * @return 접근 가능한 FileMetadata 엔티티
     * @throws BusinessException 파일이 존재하지 않거나 접근 권한이 없는 경우
     */
    private FileMetadata findAccessibleFile(Long id, String uploaderName, boolean privileged) {
        if (privileged) {
            return findActiveFile(id);
        }
        return fileMetadataRepository.findByIdAndDeletedAtIsNullAndUploaderName(id, uploaderName)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * 상태 문자열을 FileStatus 열거형으로 변환한다. 유효하지 않은 값이면 null을 반환한다.
     *
     * @param statusStr 변환할 상태 문자열
     * @param hasStatus 상태 필터 사용 여부
     * @return 변환된 FileStatus, 또는 null (hasStatus가 false이거나 변환 실패 시)
     */
    private FileStatus parseStatus(String statusStr, boolean hasStatus) {
        if (!hasStatus) return null;
        try {
            return FileStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
