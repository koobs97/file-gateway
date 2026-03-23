package com.file.gateway.process.event.repository;

import com.file.gateway.process.event.entity.FileProcessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileProcessLogRepository extends JpaRepository<FileProcessLog, Long> {

    List<FileProcessLog> findAllByFileIdOrderByCreatedAtAsc(Long fileId);
}
