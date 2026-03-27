package com.file.gateway.process.event.repository;

import com.file.gateway.process.event.entity.FileProcessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FileProcessLogRepository
 * - FileProcessLog 엔티티에 대한 JPA 데이터 접근 레이어
 * - 특정 파일의 처리 단계별 로그를 생성 시간 오름차순으로 조회하는 쿼리 메서드를 제공
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface FileProcessLogRepository extends JpaRepository<FileProcessLog, Long> {

    /**
     * 특정 파일 ID에 해당하는 모든 처리 로그를 생성 시간 오름차순으로 조회한다.
     *
     * @param fileId 조회할 파일의 ID
     * @return 생성 시간 오름차순으로 정렬된 FileProcessLog 목록
     */
    List<FileProcessLog> findAllByFileIdOrderByCreatedAtAsc(Long fileId);
}
