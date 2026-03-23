package com.file.gateway.process.event.repository;

import com.file.gateway.process.event.entity.FileEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileEventRepository extends JpaRepository<FileEvent, Long> {

    List<FileEvent> findAllByFileIdOrderByCreatedAtAsc(Long fileId);
}
