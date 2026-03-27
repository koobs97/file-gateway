package com.file.gateway.audit;

import com.file.gateway.audit.dto.ProcessLogResponse;
import com.file.gateway.file.dto.FileEventResponse;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.process.event.repository.FileProcessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AuditService
 * - 파일 처리 로그 및 이벤트 이력 조회 비즈니스 로직 처리
 * - 관리자 및 감사자(AUDITOR)가 시스템의 파일 처리 내역을 추적할 수 있도록 지원
 * - 모든 조회 작업은 읽기 전용 트랜잭션으로 수행
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** 파일 처리 단계별 로그 조회를 위한 Repository */
    private final FileProcessLogRepository fileProcessLogRepository;

    /** 파일 이벤트 이력 조회를 위한 Repository */
    private final FileEventRepository fileEventRepository;

    /**
     * 전체 파일 처리 로그를 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (null 불허)
     * @return 페이지 형태의 파일 처리 로그 응답 DTO 목록
     */
    @Transactional(readOnly = true)
    public Page<ProcessLogResponse> getProcessLogs(Pageable pageable) {
        return fileProcessLogRepository.findAll(pageable).map(ProcessLogResponse::from);
    }

    /**
     * 특정 파일의 처리 로그를 생성일시 오름차순으로 조회
     *
     * @param fileId 처리 로그를 조회할 파일의 ID (null 불허)
     * @return 해당 파일의 처리 단계별 로그 응답 DTO 목록 (createdAt 오름차순)
     */
    @Transactional(readOnly = true)
    public List<ProcessLogResponse> getFileProcessLogs(Long fileId) {
        return fileProcessLogRepository.findAllByFileIdOrderByCreatedAtAsc(fileId)
                .stream()
                .map(ProcessLogResponse::from)
                .toList();
    }

    /**
     * 특정 파일의 이벤트 이력을 생성일시 오름차순으로 조회
     *
     * @param fileId 이벤트 이력을 조회할 파일의 ID (null 불허)
     * @return 해당 파일에 발생한 이벤트 응답 DTO 목록 (createdAt 오름차순)
     */
    @Transactional(readOnly = true)
    public List<FileEventResponse> getFileEvents(Long fileId) {
        return fileEventRepository.findAllByFileIdOrderByCreatedAtAsc(fileId)
                .stream()
                .map(FileEventResponse::from)
                .toList();
    }
}
