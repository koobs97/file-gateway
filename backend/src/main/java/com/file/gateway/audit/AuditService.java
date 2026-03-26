package com.file.gateway.audit;

import com.file.gateway.audit.dto.ProcessLogResponse;
import com.file.gateway.file.dto.FileEventResponse;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.process.event.repository.FileProcessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final FileProcessLogRepository fileProcessLogRepository;
    private final FileEventRepository fileEventRepository;

    @Transactional(readOnly = true)
    public Page<ProcessLogResponse> getProcessLogs(Pageable pageable) {
        return fileProcessLogRepository.findAll(pageable).map(ProcessLogResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ProcessLogResponse> getFileProcessLogs(Long fileId) {
        return fileProcessLogRepository.findAllByFileIdOrderByCreatedAtAsc(fileId)
                .stream()
                .map(ProcessLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileEventResponse> getFileEvents(Long fileId) {
        return fileEventRepository.findAllByFileIdOrderByCreatedAtAsc(fileId)
                .stream()
                .map(FileEventResponse::from)
                .toList();
    }
}
