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

@Tag(name = "Admin - API Clients", description = "API 클라이언트 관리 API")
@RestController
@RequestMapping("/api/v1/admin/api-clients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApiClientController {

    private final AdminApiClientService adminApiClientService;

    @Operation(summary = "API 클라이언트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApiClientResponse>>> getClients(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminApiClientService.getClients(pageable)));
    }

    @Operation(summary = "API 클라이언트 생성", description = "API 키가 자동으로 생성됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ApiClientResponse>> createClient(
            @Valid @RequestBody CreateApiClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(adminApiClientService.createClient(request)));
    }

    @Operation(summary = "API 클라이언트 비활성화")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable("id") Long id) {
        adminApiClientService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
