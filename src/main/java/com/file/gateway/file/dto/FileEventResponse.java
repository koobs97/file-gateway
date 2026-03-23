package com.file.gateway.file.dto;

import com.file.gateway.process.event.entity.FileEvent;

import java.time.LocalDateTime;

public record FileEventResponse(
        Long id,
        String eventType,
        String payload,
        LocalDateTime createdAt
) {
    public static FileEventResponse from(FileEvent event) {
        return new FileEventResponse(
                event.getId(),
                event.getEventType().name(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}
