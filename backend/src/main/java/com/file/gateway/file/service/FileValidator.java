package com.file.gateway.file.service;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * FileValidator
 * - 업로드 파일의 유효성을 검사하는 컴포넌트이다.
 * - 빈 파일 여부, 최대 파일 크기 초과 여부, 허용 확장자 여부를 순서대로 검증한다.
 * - 검증 실패 시 BusinessException을 발생시켜 컨트롤러로 전파한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Component
@RequiredArgsConstructor
public class FileValidator {

    /** 허용 확장자, 최대 파일 크기 등 저장소 관련 설정 프로퍼티 */
    private final StorageProperties storageProperties;

    /**
     * 업로드 파일의 유효성을 검사한다.
     * 빈 파일 여부 → 크기 초과 여부 → 허용 확장자 여부 순으로 검증하며,
     * 조건 위반 시 즉시 BusinessException을 발생시킨다.
     *
     * @param file 검사할 MultipartFile
     * @throws BusinessException 파일이 비어있거나, 크기 초과이거나, 허용되지 않는 확장자인 경우
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "파일이 비어있습니다.");
        }
        if (file.getSize() > storageProperties.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!storageProperties.getAllowedExtensionList().contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "허용되지 않는 파일 형식입니다. 허용 형식: " + storageProperties.getAllowedExtensions());
        }
    }

    /**
     * 파일명에서 소문자 확장자를 추출한다.
     * 파일명이 null이거나 점(.)이 없으면 빈 문자열을 반환한다.
     *
     * @param filename 원본 파일명
     * @return 소문자로 변환된 확장자 (예: "docx"), 또는 빈 문자열
     */
    public String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
