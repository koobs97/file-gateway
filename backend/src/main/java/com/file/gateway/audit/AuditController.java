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

/**
 * AuditController
 * - ROLE_ADMIN 또는 ROLE_AUDITOR 권한을 가진 사용자 전용 감사 로그 조회 REST API 제공
 * - 전체 파일 처리 로그, 특정 파일의 처리 로그 및 이벤트 이력 조회 기능을 처리
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Tag(name = "Audit", description = "감사 로그 조회 API")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
@RequiredArgsConstructor
public class AuditController {

    /** 감사 로그 조회 비즈니스 로직을 처리하는 서비스 */
    private final AuditService auditService;

    /**
     * 전체 파일 처리 로그를 최신순으로 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (기본값: 크기 20, createdAt 내림차순)
     * @return 페이지 형태의 파일 처리 로그 목록
     */
    @Operation(summary = "전체 파일 처리 로그 조회")
    @GetMapping("/files")
    public ResponseEntity<ApiResponse<Page<ProcessLogResponse>>> getProcessLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getProcessLogs(pageable)));
    }

    /**
     * 특정 파일의 처리 로그를 시간 오름차순으로 조회
     *
     * @param id 조회할 파일의 ID (null 불허)
     * @return 해당 파일의 처리 단계별 로그 목록 (createdAt 오름차순)
     */
    @Operation(summary = "특정 파일 처리 로그 조회")
    @GetMapping("/files/{id}/logs")
    public ResponseEntity<ApiResponse<List<ProcessLogResponse>>> getFileProcessLogs(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getFileProcessLogs(id)));
    }

    /**
     * 특정 파일의 이벤트 이력을 시간 오름차순으로 조회
     *
     * @param id 조회할 파일의 ID (null 불허)
     * @return 해당 파일에 발생한 이벤트 목록 (createdAt 오름차순)
     */
    @Operation(summary = "특정 파일 이벤트 이력 조회")
    @GetMapping("/files/{id}/events")
    public ResponseEntity<ApiResponse<List<FileEventResponse>>> getFileEvents(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getFileEvents(id)));
    }
}
