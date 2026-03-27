package com.file.gateway.common.exception;

import com.file.gateway.common.response.ErrorCode;
import lombok.Getter;

/**
 * BusinessException
 * - 도메인 비즈니스 규칙 위반 시 발생하는 최상위 런타임 예외 클래스
 * - ErrorCode 열거형과 결합하여 HTTP 상태 코드 및 메시지를 일관되게 전달
 * - GlobalExceptionHandler에서 포착하여 표준 ApiResponse로 변환된다
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 이 예외에 대응하는 에러 코드 */
    private final ErrorCode errorCode;

    /**
     * ErrorCode의 기본 메시지를 사용하여 예외를 생성한다.
     *
     * @param errorCode 에러 코드 열거값
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode와 별도의 상세 메시지를 지정하여 예외를 생성한다.
     *
     * @param errorCode 에러 코드 열거값
     * @param message   클라이언트에 전달할 상세 에러 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
