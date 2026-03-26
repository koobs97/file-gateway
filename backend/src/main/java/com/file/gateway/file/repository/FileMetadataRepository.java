package com.file.gateway.file.repository;

import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<FileMetadata> findByIdAndDeletedAtIsNull(Long id);

    long countByStatusAndDeletedAtIsNull(FileStatus status);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNotNull();
}
