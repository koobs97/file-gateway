package com.file.gateway.admin;

import com.file.gateway.admin.dto.CreateUserRequest;
import com.file.gateway.admin.dto.UpdateRoleRequest;
import com.file.gateway.admin.dto.UpdateStatusRequest;
import com.file.gateway.admin.dto.UserSummaryResponse;
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
 * AdminUserController
 * - ROLE_ADMIN 권한을 가진 관리자 전용 사용자 관리 REST API 제공
 * - 사용자 생성, 목록 조회, 역할 변경, 활성화/비활성화 기능을 처리
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Tag(name = "Admin - Users", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    /** 사용자 관리 비즈니스 로직을 처리하는 서비스 */
    private final AdminUserService adminUserService;

    /**
     * 새 사용자를 생성하고 초기 비밀번호 변경 요구 플래그를 설정
     *
     * @param request 사용자명, 초기 비밀번호, 역할을 담은 생성 요청 DTO (null 불허)
     * @return HTTP 201 Created와 생성된 사용자 요약 정보
     * @throws com.file.gateway.common.exception.BusinessException 이미 존재하는 사용자명인 경우 (USERNAME_ALREADY_EXISTS)
     */
    @Operation(summary = "사용자 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<UserSummaryResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminUserService.createUser(request)));
    }

    /**
     * 전체 사용자 목록을 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (기본값: 크기 20, createdAt 내림차순)
     * @return 페이지 형태의 사용자 요약 정보 목록
     */
    @Operation(summary = "사용자 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getUsers(pageable)));
    }

    /**
     * 특정 사용자의 역할을 변경
     *
     * @param id      역할을 변경할 사용자의 ID (null 불허)
     * @param request 변경할 역할 값을 담은 요청 DTO (null 불허, 허용값: ROLE_ADMIN/ROLE_AUDITOR/ROLE_END_USER)
     * @return 역할이 변경된 사용자 요약 정보
     * @throws com.file.gateway.common.exception.BusinessException 해당 ID의 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    @Operation(summary = "사용자 역할 변경")
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.updateRole(id, request.role())));
    }

    /**
     * 특정 사용자의 계정 활성화 또는 비활성화 상태를 변경
     *
     * @param id      상태를 변경할 사용자의 ID (null 불허)
     * @param request 활성화 여부(active)를 담은 요청 DTO (null 불허)
     * @return 상태가 변경된 사용자 요약 정보
     * @throws com.file.gateway.common.exception.BusinessException 해당 ID의 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    @Operation(summary = "사용자 활성화/비활성화")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.updateStatus(id, request.active())));
    }
}
