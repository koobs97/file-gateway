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

/**
 * AdminController
 * - 관리자 전용 운영 API를 제공한다.
 * - 현재는 전체 파일 처리 통계 조회 기능을 담당한다.
 * - 향후 사용자 관리, 시스템 설정 등 관리자 기능이 추가될 수 있다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    /** 파일 비즈니스 로직 및 통계 조회를 처리하는 서비스 */
    private final FileService fileService;

    /**
     * 전체 파일 처리 통계를 조회한다.
     * 총 파일 수, 완료/실패/처리 중 건수, 성공률 등을 반환한다.
     *
     * @return 파일 처리 통계 응답
     */
    @Operation(summary = "처리 통계 조회")
    @GetMapping("/statistics")
    public ApiResponse<FileStatisticsResponse> getStatistics() {
        return ApiResponse.ok(fileService.getStatistics(null, true));
    }
}
