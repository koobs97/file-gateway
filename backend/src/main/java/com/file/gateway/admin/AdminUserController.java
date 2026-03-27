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

@Tag(name = "Admin - Users", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "사용자 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<UserSummaryResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminUserService.createUser(request)));
    }

    @Operation(summary = "사용자 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getUsers(pageable)));
    }

    @Operation(summary = "사용자 역할 변경")
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.updateRole(id, request.role())));
    }

    @Operation(summary = "사용자 활성화/비활성화")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.updateStatus(id, request.active())));
    }
}
