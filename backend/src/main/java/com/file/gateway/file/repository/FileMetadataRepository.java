package com.file.gateway.file.repository;

import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    // 전체 조회 (ADMIN, AUDITOR)
    Page<FileMetadata> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT f FROM FileMetadata f WHERE f.deletedAt IS NULL " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FileMetadata> searchFiles(
            @Param("status") FileStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 업로더별 조회 (END_USER, API_CLIENT)
    Page<FileMetadata> findAllByDeletedAtIsNullAndUploaderName(String uploaderName, Pageable pageable);

    @Query("SELECT f FROM FileMetadata f WHERE f.deletedAt IS NULL " +
           "AND f.uploaderName = :uploaderName " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FileMetadata> searchFilesByUploader(
            @Param("uploaderName") String uploaderName,
            @Param("status") FileStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    Optional<FileMetadata> findByIdAndDeletedAtIsNull(Long id);

    Optional<FileMetadata> findByIdAndDeletedAtIsNullAndUploaderName(Long id, String uploaderName);

    long countByStatusAndDeletedAtIsNull(FileStatus status);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNotNull();

    long countByStatusAndDeletedAtIsNullAndUploaderName(FileStatus status, String uploaderName);

    long countByDeletedAtIsNullAndUploaderName(String uploaderName);
}
