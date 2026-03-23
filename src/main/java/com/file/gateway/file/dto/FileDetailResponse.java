package com.file.gateway.file.dto;

import com.file.gateway.file.entity.FileMetadata;

import java.time.LocalDateTime;

public record FileDetailResponse(
        Long id,
        String originalName,
        Long fileSize,
        String mimeType,
        String storageType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FileDetailResponse from(FileMetadata file) {
        return new FileDetailResponse(
                file.getId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getMimeType(),
                file.getStorageType().name(),
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
