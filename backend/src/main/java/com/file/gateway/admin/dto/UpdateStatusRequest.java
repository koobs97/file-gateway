package com.file.gateway.admin.dto;

/**
 * UpdateStatusRequest
 * - 사용자 계정 활성화/비활성화 API 요청 본문을 담는 DTO 레코드
 * - active 값이 true이면 활성화, false이면 비활성화
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record UpdateStatusRequest(
        /** 변경할 계정 활성화 여부 (true: 활성화, false: 비활성화) */
        boolean active
) {
}
