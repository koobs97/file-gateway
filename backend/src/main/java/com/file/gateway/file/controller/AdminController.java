package com.file.gateway.file.controller;

import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.file.dto.FileStatisticsResponse;
import com.file.gateway.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FileService fileService;

    @Operation(summary = "처리 통계 조회")
    @GetMapping("/statistics")
    public ApiResponse<FileStatisticsResponse> getStatistics() {
        return ApiResponse.ok(fileService.getStatistics(null, true));
    }
}
