package com.file.gateway.common.exception;

import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler
 * - 애플리케이션 전역에서 발생하는 예외를 포착하여 표준 ApiResponse 형태로 변환하는 핸들러
 * - BusinessException, 유효성 검사 예외, 접근 거부 예외, 미처리 예외를 일괄 처리
 * - @RestControllerAdvice를 통해 모든 컨트롤러에 적용
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반 예외를 처리한다.
     * ErrorCode에 정의된 HTTP 상태 코드와 메시지를 그대로 응답에 반영한다.
     *
     * @param e 발생한 BusinessException
     * @return ErrorCode에 대응하는 HTTP 상태 코드와 실패 응답 바디
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.fail(code, e.getMessage()));
    }

    /**
     * @Valid/@Validated 유효성 검사 실패 예외를 처리한다.
     * 모든 필드 에러 메시지를 쉼표로 결합하여 단일 메시지로 반환한다.
     *
     * @param e 발생한 MethodArgumentNotValidException
     * @return 400 Bad Request와 유효성 오류 메시지를 포함한 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("ValidationException: {}", message);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, message));
    }

    /**
     * Spring Security의 접근 거부 예외를 처리한다.
     *
     * @param e 발생한 AccessDeniedException
     * @return 403 Forbidden 응답
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage()));
    }

    /**
     * 명시적으로 처리되지 않은 모든 예외를 최종적으로 포착한다.
     * 서버 내부 오류(500)로 응답하며, 스택 트레이스를 에러 레벨로 기록한다.
     *
     * @param e 발생한 Exception
     * @return 500 Internal Server Error 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
