package com.file.gateway.admin;

import com.file.gateway.admin.dto.ApiClientResponse;
import com.file.gateway.admin.dto.CreateApiClientRequest;
import com.file.gateway.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AdminApiClientController
 * - ROLE_ADMIN 권한을 가진 관리자 전용 API 클라이언트 관리 REST API 제공
 * - API 클라이언트 목록 조회, 생성, 비활성화 기능을 처리
 * - API 키는 클라이언트 생성 시 UUID로 자동 생성됨
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Tag(name = "Admin - API Clients", description = "API 클라이언트 관리 API")
@RestController
@RequestMapping("/api/v1/admin/api-clients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApiClientController {

    /** API 클라이언트 관리 비즈니스 로직을 처리하는 서비스 */
    private final AdminApiClientService adminApiClientService;

    /**
     * 전체 API 클라이언트 목록을 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (기본값: 크기 20, createdAt 내림차순)
     * @return 페이지 형태의 API 클라이언트 정보 목록
     */
    @Operation(summary = "API 클라이언트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApiClientResponse>>> getClients(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminApiClientService.getClients(pageable)));
    }

    /**
     * 새 API 클라이언트를 생성하고 UUID 기반 API 키를 자동 발급
     *
     * @param request 클라이언트 이름을 담은 생성 요청 DTO (null 불허)
     * @return HTTP 201 Created와 생성된 API 클라이언트 정보 (API 키 포함)
     */
    @Operation(summary = "API 클라이언트 생성", description = "API 키가 자동으로 생성됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ApiClientResponse>> createClient(
            @Valid @RequestBody CreateApiClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminApiClientService.createClient(request)));
    }

    /**
     * 특정 API 클라이언트를 비활성화 처리
     *
     * @param id 비활성화할 API 클라이언트의 ID (null 불허)
     * @return 빈 성공 응답
     * @throws com.file.gateway.common.exception.BusinessException 해당 ID의 클라이언트가 존재하지 않는 경우 (API_CLIENT_NOT_FOUND)
     */
    @Operation(summary = "API 클라이언트 비활성화")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable("id") Long id) {
        adminApiClientService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
