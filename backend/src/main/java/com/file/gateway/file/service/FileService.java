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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileEventRepository fileEventRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final FileEventService fileEventService;
    private final ApplicationEventPublisher eventPublisher;

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

    public FileDetailResponse getFile(Long id, String uploaderName, boolean privileged) {
        return FileDetailResponse.from(findAccessibleFile(id, uploaderName, privileged));
    }

    @Transactional
    public void deleteFile(Long id) {
        FileMetadata file = findActiveFile(id);
        file.delete();
        log.info("파일 삭제(soft delete): id={}", id);
    }

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

    public List<FileEventResponse> getEvents(Long id) {
        findActiveFile(id); // 존재 여부 확인
        return fileEventRepository.findAllByFileIdOrderByCreatedAtAsc(id)
                .stream()
                .map(FileEventResponse::from)
                .toList();
    }

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

    private FileMetadata findActiveFile(Long id) {
        return fileMetadataRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    private FileMetadata findAccessibleFile(Long id, String uploaderName, boolean privileged) {
        if (privileged) {
            return findActiveFile(id);
        }
        return fileMetadataRepository.findByIdAndDeletedAtIsNullAndUploaderName(id, uploaderName)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    private FileStatus parseStatus(String statusStr, boolean hasStatus) {
        if (!hasStatus) return null;
        try {
            return FileStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
