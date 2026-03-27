package com.file.gateway.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ApiResponse
 * - 모든 REST API 응답에 사용하는 공통 래퍼 레코드
 * - 성공 여부(success), 응답 데이터(data), 에러 정보(error)를 일관된 포맷으로 반환
 * - null 필드는 JSON 직렬화 시 제외(@JsonInclude NON_NULL)
 *
 * @param <T> 응답 데이터 타입
 * @author 구본상
 * @since 2026-03-26
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        /** 요청 처리 성공 여부 */
        boolean success,

        /** 성공 시 반환되는 데이터 페이로드 */
        T data,

        /** 실패 시 반환되는 에러 정보 */
        ErrorResponse error
) {

    /**
     * 데이터를 포함한 성공 응답을 생성한다.
     *
     * @param <T>  데이터 타입
     * @param data 반환할 데이터
     * @return success=true, data 포함 ApiResponse
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 데이터 없이 성공 응답을 생성한다.
     *
     * @param <T> 데이터 타입
     * @return success=true, data=null ApiResponse
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 에러 코드와 메시지를 포함한 실패 응답을 생성한다.
     *
     * @param <T>     데이터 타입
     * @param code    에러 코드 열거값
     * @param message 클라이언트에 전달할 에러 메시지
     * @return success=false, error 포함 ApiResponse
     */
    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code.name(), message));
    }

    /**
     * ErrorResponse
     * - 실패 응답에 포함되는 에러 코드와 메시지를 담는 중첩 레코드
     *
     * @param code    에러 코드 문자열 (ErrorCode enum 이름)
     * @param message 에러 설명 메시지
     */
    public record ErrorResponse(String code, String message) {}
}
