package com.file.gateway.process.worker;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import com.file.gateway.file.repository.FileMetadataRepository;
import com.file.gateway.process.cdr.analyzer.AnalysisResult;
import com.file.gateway.process.cdr.analyzer.OfficeAnalyzer;
import com.file.gateway.process.cdr.sanitizer.OfficeSanitizer;
import com.file.gateway.process.event.FileEventService;
import com.file.gateway.process.event.entity.EventType;
import com.file.gateway.process.event.entity.ProcessStatus;
import com.file.gateway.process.event.entity.ProcessStep;
import com.file.gateway.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SanitizeWorker
 * - CDR(Content Disarm and Reconstruction) 처리 파이프라인을 실행하는 워커 서비스
 * - 분석(OfficeAnalyzer) → 무해화(OfficeSanitizer) → 저장 → 상태 업데이트 → WebSocket 알림 순서로 처리
 * - sanitizeExecutor 스레드 풀에서 비동기로 실행되어 업로드 응답 지연을 방지
 * - 원본 파일은 절대 수정하지 않으며 항상 새로운 무해화 파일을 생성
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizeWorker {

    /** 파일 메타데이터 저장소 */
    private final FileMetadataRepository fileMetadataRepository;

    /** 파일 저장소 (읽기/쓰기) */
    private final StorageService storageService;

    /** 이벤트 발행 및 처리 로그 저장 서비스 */
    private final FileEventService fileEventService;

    /** WebSocket 메시지 전송 템플릿 */
    private final SimpMessagingTemplate messagingTemplate;

    /** 등록된 모든 OfficeAnalyzer 구현체 목록 */
    private final List<OfficeAnalyzer> analyzers;

    /** 등록된 모든 OfficeSanitizer 구현체 목록 */
    private final List<OfficeSanitizer> sanitizers;

    /**
     * 비동기 처리 진입점. sanitizeExecutor 스레드 풀에서 실행.
     *
     * @param fileId 처리할 파일의 ID
     */
    @Async("sanitizeExecutor")
    public void processAsync(Long fileId) {
        process(fileId);
    }

    /**
     * CDR 처리 파이프라인: 분석 → 무해화 → 저장 → 상태 업데이트 → WebSocket 알림
     * 처리 성공 시 파일 상태를 DONE으로, 실패 시 FAIL로 업데이트한다.
     *
     * @param fileId 처리할 파일의 ID
     * @throws BusinessException 파일을 찾을 수 없는 경우
     */
    @Transactional
    public void process(Long fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        log.info("[CDR] 처리 시작: fileId={}, name={}", fileId, file.getOriginalName());
        fileEventService.publishEvent(file, EventType.START, null);

        try {
            String ext = extractExtension(file.getOriginalName());

            // 1단계: 분석
            AnalysisResult analysisResult = analyze(file, ext);

            // 2단계: 무해화
            sanitize(file, ext, analysisResult);

            // 3단계: 완료 처리
            file.updateStatus(FileStatus.DONE);
            fileMetadataRepository.save(file);  // self-invocation으로 @Transactional 미작동 보완
            fileEventService.publishEvent(file, EventType.DONE, null);
            log.info("[CDR] 처리 완료: fileId={}", fileId);

        } catch (BusinessException e) {
            handleFailure(file, e.getMessage());
        } catch (Exception e) {
            log.error("[CDR] 처리 실패: fileId={}", fileId, e);
            handleFailure(file, e.getMessage());
        }

        notifyViaWebSocket(file);
    }

    /**
     * 파일 확장자에 맞는 OfficeAnalyzer를 찾아 위협 요소를 분석한다.
     *
     * @param file 분석할 파일 메타데이터
     * @param ext  파일 확장자 (소문자)
     * @return 분석 결과 (탐지된 위협 목록 포함)
     * @throws Exception 파일 읽기 또는 분석 중 오류 발생 시
     */
    private AnalysisResult analyze(FileMetadata file, String ext) throws Exception {
        OfficeAnalyzer analyzer = findAnalyzer(ext);

        try (InputStream in = storageService.load(file.getStoragePath())) {
            AnalysisResult result = analyzer.analyze(in);
            String summary = result.isSafe()
                    ? "위협 없음"
                    : "위협 탐지: " + String.join(", ", result.detectedThreats());
            fileEventService.saveProcessLog(file, ProcessStep.ANALYSIS, ProcessStatus.SUCCESS, summary);
            log.debug("[CDR] 분석 완료: fileId={}, {}", file.getId(), summary);
            return result;
        }
    }

    /**
     * 파일 확장자에 맞는 OfficeSanitizer를 찾아 무해화 후 새 경로에 저장한다.
     * 원본 파일은 수정하지 않고 무해화된 결과를 별도 파일로 저장한다.
     *
     * @param file           무해화할 파일 메타데이터
     * @param ext            파일 확장자 (소문자)
     * @param analysisResult 사전 분석 결과 (탐지된 위협 수 등)
     * @throws Exception 파일 읽기, 무해화, 저장 중 오류 발생 시
     */
    private void sanitize(FileMetadata file, String ext, AnalysisResult analysisResult) throws Exception {
        OfficeSanitizer sanitizer = findSanitizer(ext);

        // 원본 InputStream → 무해화 → ByteArray → StorageService 저장 (원본 절대 수정 금지)
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = storageService.load(file.getStoragePath())) {
            sanitizer.sanitize(in, buffer);
        }

        String sanitizedPath = storageService.save(
                new ByteArrayInputStream(buffer.toByteArray()),
                file.getStoredName(),
                "sanitized"
        );

        fileEventService.saveProcessLog(file, ProcessStep.SANITIZE, ProcessStatus.SUCCESS,
                "무해화 완료: 제거 항목=" + analysisResult.detectedThreats().size());
        fileEventService.saveProcessLog(file, ProcessStep.SAVE, ProcessStatus.SUCCESS,
                "저장 완료: " + sanitizedPath);
    }

    /**
     * 처리 실패 시 파일 상태를 FAIL로 업데이트하고 실패 이벤트 및 로그를 기록한다.
     *
     * @param file    실패한 파일 메타데이터
     * @param message 실패 원인 메시지
     */
    private void handleFailure(FileMetadata file, String message) {
        file.updateStatus(FileStatus.FAIL);
        fileMetadataRepository.save(file);  // self-invocation으로 @Transactional 미작동 보완
        fileEventService.saveProcessLog(file, ProcessStep.SANITIZE, ProcessStatus.FAIL, message);
        fileEventService.publishEvent(file, EventType.FAIL, null);
    }

    /**
     * 파일 처리 결과를 WebSocket을 통해 해당 파일 구독자에게 실시간으로 전송한다.
     * 전송 실패 시 경고 로그만 기록하고 예외는 전파하지 않는다.
     *
     * @param file 처리 완료된 파일 메타데이터
     */
    private void notifyViaWebSocket(FileMetadata file) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/files/" + file.getId(),
                    new FileStatusNotification(file.getId(), file.getStatus().name(), LocalDateTime.now())
            );
            log.debug("[CDR] WebSocket 알림 전송: fileId={}, status={}", file.getId(), file.getStatus());
        } catch (Exception e) {
            log.warn("[CDR] WebSocket 알림 실패: fileId={}, {}", file.getId(), e.getMessage());
        }
    }

    /**
     * 주어진 확장자를 지원하는 OfficeAnalyzer를 찾아 반환한다.
     *
     * @param ext 파일 확장자 (소문자)
     * @return 해당 확장자를 지원하는 OfficeAnalyzer
     * @throws BusinessException 지원하지 않는 파일 형식인 경우
     */
    private OfficeAnalyzer findAnalyzer(String ext) {
        return analyzers.stream()
                .filter(a -> a.supports(ext))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                        "지원하지 않는 파일 형식: " + ext));
    }

    /**
     * 주어진 확장자를 지원하는 OfficeSanitizer를 찾아 반환한다.
     *
     * @param ext 파일 확장자 (소문자)
     * @return 해당 확장자를 지원하는 OfficeSanitizer
     * @throws BusinessException 지원하지 않는 파일 형식인 경우
     */
    private OfficeSanitizer findSanitizer(String ext) {
        return sanitizers.stream()
                .filter(s -> s.supports(ext))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                        "지원하지 않는 파일 형식: " + ext));
    }

    /**
     * 파일명에서 확장자를 추출하여 소문자로 반환한다.
     *
     * @param filename 원본 파일명
     * @return 소문자 확장자 문자열, 확장자가 없으면 빈 문자열
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
