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

@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizeWorker {

    private final FileMetadataRepository fileMetadataRepository;
    private final StorageService storageService;
    private final FileEventService fileEventService;
    private final SimpMessagingTemplate messagingTemplate;
    private final List<OfficeAnalyzer> analyzers;
    private final List<OfficeSanitizer> sanitizers;

    /**
     * 비동기 처리 진입점. sanitizeExecutor 스레드 풀에서 실행.
     */
    @Async("sanitizeExecutor")
    public void processAsync(Long fileId) {
        process(fileId);
    }

    /**
     * CDR 처리 파이프라인: 분석 → 무해화 → 저장 → 상태 업데이트 → WebSocket 알림
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

    private void handleFailure(FileMetadata file, String message) {
        file.updateStatus(FileStatus.FAIL);
        fileMetadataRepository.save(file);  // self-invocation으로 @Transactional 미작동 보완
        fileEventService.saveProcessLog(file, ProcessStep.SANITIZE, ProcessStatus.FAIL, message);
        fileEventService.publishEvent(file, EventType.FAIL, null);
    }

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

    private OfficeAnalyzer findAnalyzer(String ext) {
        return analyzers.stream()
                .filter(a -> a.supports(ext))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                        "지원하지 않는 파일 형식: " + ext));
    }

    private OfficeSanitizer findSanitizer(String ext) {
        return sanitizers.stream()
                .filter(s -> s.supports(ext))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                        "지원하지 않는 파일 형식: " + ext));
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
