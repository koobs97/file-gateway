package com.file.gateway.audit.dto;

import com.file.gateway.process.event.entity.FileProcessLog;

import java.time.LocalDateTime;

public record ProcessLogResponse(
        Long id,
        Long fileId,
        String step,
        String status,
        String message,
        LocalDateTime createdAt
) {
    public static ProcessLogResponse from(FileProcessLog log) {
        return new ProcessLogResponse(
                log.getId(),
                log.getFile().getId(),
                log.getStep().name(),
                log.getStatus().name(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
