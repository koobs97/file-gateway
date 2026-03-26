package com.file.gateway.audit;

import com.file.gateway.audit.dto.ProcessLogResponse;
import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.file.dto.FileEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Audit", description = "감사 로그 조회 API")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "전체 파일 처리 로그 조회")
    @GetMapping("/files")
    public ResponseEntity<ApiResponse<Page<ProcessLogResponse>>> getProcessLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getProcessLogs(pageable)));
    }

    @Operation(summary = "특정 파일 처리 로그 조회")
    @GetMapping("/files/{id}/logs")
    public ResponseEntity<ApiResponse<List<ProcessLogResponse>>> getFileProcessLogs(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getFileProcessLogs(id)));
    }

    @Operation(summary = "특정 파일 이벤트 이력 조회")
    @GetMapping("/files/{id}/events")
    public ResponseEntity<ApiResponse<List<FileEventResponse>>> getFileEvents(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getFileEvents(id)));
    }
}
