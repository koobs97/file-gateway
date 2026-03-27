package com.file.gateway.file.repository;

import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * FileMetadataRepository
 * - FileMetadata 엔티티에 대한 데이터 접근 계층이다.
 * - 논리 삭제(deletedAt IS NULL) 조건을 기본으로 하는 조회 메서드를 제공한다.
 * - ADMIN·AUDITOR용 전체 조회와 업로더별 제한 조회를 분리하여 정의한다.
 * - 키워드·상태 복합 검색은 JPQL 쿼리를 통해 구현한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    // 전체 조회 (ADMIN, AUDITOR)

    /**
     * 논리 삭제되지 않은 전체 파일 목록을 페이징으로 조회한다.
     *
     * @param pageable 페이징 및 정렬 정보
     * @return 활성 파일 페이지
     */
    Page<FileMetadata> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * 상태와 키워드 조건으로 전체 파일을 검색한다. 각 조건이 null이면 해당 필터는 적용되지 않는다.
     *
     * @param status   처리 상태 필터 (null이면 전체 상태 대상)
     * @param keyword  원본 파일명 부분 검색 키워드 (null이면 필터 미적용)
     * @param pageable 페이징 및 정렬 정보
     * @return 조건에 맞는 파일 페이지
     */
    @Query("SELECT f FROM FileMetadata f WHERE f.deletedAt IS NULL " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FileMetadata> searchFiles(
            @Param("status") FileStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 업로더별 조회 (END_USER, API_CLIENT)

    /**
     * 특정 업로더가 업로드한 논리 삭제되지 않은 파일 목록을 페이징으로 조회한다.
     *
     * @param uploaderName 업로더 사용자명
     * @param pageable     페이징 및 정렬 정보
     * @return 업로더의 활성 파일 페이지
     */
    Page<FileMetadata> findAllByDeletedAtIsNullAndUploaderName(String uploaderName, Pageable pageable);

    /**
     * 특정 업로더의 파일을 상태와 키워드 조건으로 검색한다.
     *
     * @param uploaderName 업로더 사용자명
     * @param status       처리 상태 필터 (null이면 전체 상태 대상)
     * @param keyword      원본 파일명 부분 검색 키워드 (null이면 필터 미적용)
     * @param pageable     페이징 및 정렬 정보
     * @return 조건에 맞는 업로더의 파일 페이지
     */
    @Query("SELECT f FROM FileMetadata f WHERE f.deletedAt IS NULL " +
           "AND f.uploaderName = :uploaderName " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FileMetadata> searchFilesByUploader(
            @Param("uploaderName") String uploaderName,
            @Param("status") FileStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * ID로 논리 삭제되지 않은 파일을 조회한다.
     *
     * @param id 파일 ID
     * @return 활성 FileMetadata Optional
     */
    Optional<FileMetadata> findByIdAndDeletedAtIsNull(Long id);

    /**
     * ID와 업로더명으로 논리 삭제되지 않은 파일을 조회한다.
     *
     * @param id           파일 ID
     * @param uploaderName 업로더 사용자명
     * @return 해당 업로더의 활성 FileMetadata Optional
     */
    Optional<FileMetadata> findByIdAndDeletedAtIsNullAndUploaderName(Long id, String uploaderName);

    /**
     * 특정 처리 상태이고 논리 삭제되지 않은 파일 수를 반환한다.
     *
     * @param status 처리 상태
     * @return 해당 상태의 활성 파일 수
     */
    long countByStatusAndDeletedAtIsNull(FileStatus status);

    /**
     * 논리 삭제되지 않은 전체 파일 수를 반환한다.
     *
     * @return 활성 파일 수
     */
    long countByDeletedAtIsNull();

    /**
     * 논리 삭제된 파일 수를 반환한다.
     *
     * @return 삭제된 파일 수
     */
    long countByDeletedAtIsNotNull();

    /**
     * 특정 업로더의 특정 처리 상태이고 논리 삭제되지 않은 파일 수를 반환한다.
     *
     * @param status       처리 상태
     * @param uploaderName 업로더 사용자명
     * @return 해당 업로더의 특정 상태 활성 파일 수
     */
    long countByStatusAndDeletedAtIsNullAndUploaderName(FileStatus status, String uploaderName);

    /**
     * 특정 업로더의 논리 삭제되지 않은 전체 파일 수를 반환한다.
     *
     * @param uploaderName 업로더 사용자명
     * @return 해당 업로더의 활성 파일 수
     */
    long countByDeletedAtIsNullAndUploaderName(String uploaderName);
}
