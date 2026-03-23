package com.file.gateway.file.dto;

import com.file.gateway.file.entity.FileMetadata;

public record FileUploadResponse(
        Long fileId,
        String originalName,
        Long fileSize,
        String status
) {
    public static FileUploadResponse from(FileMetadata file) {
        return new FileUploadResponse(
                file.getId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getStatus().name()
        );
    }
}
