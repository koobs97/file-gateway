package com.file.gateway.process.event.repository;

import com.file.gateway.process.event.entity.FileEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FileEventRepository
 * - FileEvent 엔티티에 대한 JPA 데이터 접근 레이어
 * - 특정 파일의 이벤트 이력을 생성 시간 오름차순으로 조회하는 쿼리 메서드를 제공
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface FileEventRepository extends JpaRepository<FileEvent, Long> {

    /**
     * 특정 파일 ID에 해당하는 모든 이벤트를 생성 시간 오름차순으로 조회한다.
     *
     * @param fileId 조회할 파일의 ID
     * @return 생성 시간 오름차순으로 정렬된 FileEvent 목록
     */
    List<FileEvent> findAllByFileIdOrderByCreatedAtAsc(Long fileId);
}
