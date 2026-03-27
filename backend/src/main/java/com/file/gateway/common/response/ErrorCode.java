package com.file.gateway.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증/인가 관련
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다."),
    API_CLIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "API 클라이언트를 찾을 수 없습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),

    // 파일 관련
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 초과했습니다."),
    FILE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 삭제된 파일입니다."),

    // 처리 관련
    PROCESS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 처리 중 오류가 발생했습니다."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장소 오류가 발생했습니다."),

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
