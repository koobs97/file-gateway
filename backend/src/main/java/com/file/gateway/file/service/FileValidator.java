package com.file.gateway.file.service;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileValidator {

    private final StorageProperties storageProperties;

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

    public String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
