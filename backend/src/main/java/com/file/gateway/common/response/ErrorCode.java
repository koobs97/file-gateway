package com.file.gateway.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * ErrorCode
 * - 시스템 전체에서 사용하는 표준 에러 코드 열거형
 * - 각 에러 코드는 HTTP 상태 코드와 기본 메시지를 포함
 * - BusinessException 및 GlobalExceptionHandler에서 참조하여 일관된 오류 응답을 제공
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증/인가 관련
    /** 인증 정보가 없거나 유효하지 않은 요청 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    /** JWT 토큰 형식 또는 서명이 유효하지 않은 경우 */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    /** JWT 토큰이 만료된 경우 */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),

    /** 요청한 사용자를 DB에서 찾을 수 없는 경우 */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    /** 아이디 또는 비밀번호가 일치하지 않는 경우 */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    /** 접근 권한이 없는 리소스에 접근한 경우 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    /** X-API-Key 헤더의 API 키가 유효하지 않은 경우 */
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다."),

    /** 요청한 API 클라이언트를 찾을 수 없는 경우 */
    API_CLIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "API 클라이언트를 찾을 수 없습니다."),

    /** 현재 비밀번호 확인 실패 */
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),

    /** 이미 사용 중인 사용자명으로 가입 시도한 경우 */
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),

    // 파일 관련
    /** 요청한 파일을 DB 또는 스토리지에서 찾을 수 없는 경우 */
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),

    /** 허용되지 않는 MIME 타입 또는 확장자의 파일을 업로드한 경우 */
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),

    /** 업로드 파일이 허용된 최대 크기를 초과한 경우 */
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 초과했습니다."),

    /** 이미 소프트 삭제된 파일에 대한 작업을 요청한 경우 */
    FILE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 삭제된 파일입니다."),

    // 처리 관련
    /** CDR 처리 파이프라인에서 오류가 발생한 경우 */
    PROCESS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 처리 중 오류가 발생했습니다."),

    /** 파일 저장소(로컬/클라우드) 접근 중 오류가 발생한 경우 */
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장소 오류가 발생했습니다."),

    // 공통
    /** 요청 파라미터 또는 바디 유효성 검사 실패 */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    /** 처리되지 않은 서버 내부 오류 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    /** 에러에 대응하는 HTTP 상태 코드 */
    private final HttpStatus httpStatus;

    /** 클라이언트에 전달되는 기본 에러 메시지 */
    private final String defaultMessage;
}
