package com.file.gateway.process.event;

import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.process.event.entity.*;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.process.event.repository.FileProcessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FileEventService
 * - 파일 처리 이벤트 발행 및 처리 단계별 로그 저장을 담당하는 서비스 클래스
 * - CDR 파이프라인의 각 단계(업로드, 분석, 무해화, 저장)에서 호출되어 감사 추적 데이터를 생성
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class FileEventService {

    /** 파일 이벤트 저장소 */
    private final FileEventRepository fileEventRepository;

    /** 파일 처리 단계 로그 저장소 */
    private final FileProcessLogRepository fileProcessLogRepository;

    /**
     * 파일에 대한 이벤트를 발행하고 DB에 저장한다.
     *
     * @param file      이벤트가 발생한 파일 메타데이터
     * @param eventType 발행할 이벤트 유형 (QUEUED, START, DONE, FAIL)
     * @param payload   이벤트에 첨부할 추가 데이터 (없으면 null)
     */
    @Transactional
    public void publishEvent(FileMetadata file, EventType eventType, String payload) {
        fileEventRepository.save(FileEvent.builder()
                .file(file)
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    /**
     * CDR 처리 단계별 결과 로그를 DB에 저장한다.
     *
     * @param file    처리 중인 파일 메타데이터
     * @param step    처리 단계 (UPLOAD, ANALYSIS, SANITIZE, SAVE)
     * @param status  처리 결과 상태 (SUCCESS, FAIL)
     * @param message 단계 결과에 대한 상세 메시지
     */
    @Transactional
    public void saveProcessLog(FileMetadata file, ProcessStep step, ProcessStatus status, String message) {
        fileProcessLogRepository.save(FileProcessLog.builder()
                .file(file)
                .step(step)
                .status(status)
                .message(message)
                .build());
    }
}
