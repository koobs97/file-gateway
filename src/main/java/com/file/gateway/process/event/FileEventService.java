package com.file.gateway.process.event;

import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.process.event.entity.*;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.process.event.repository.FileProcessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileEventService {

    private final FileEventRepository fileEventRepository;
    private final FileProcessLogRepository fileProcessLogRepository;

    @Transactional
    public void publishEvent(FileMetadata file, EventType eventType, String payload) {
        fileEventRepository.save(FileEvent.builder()
                .file(file)
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    @Transactional
    public void saveProcessLog(FileMetadata file, ProcessStep step, ProcessStatus status, String message) {
        fileProcessLogRepository.save(FileProcessLog.builder()
                .file(file)
                .step(step)
                .status(status)
                .message(message)
                .build());
    }
}
