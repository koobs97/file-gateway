package com.file.gateway.file.service;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileValidatorTest {

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        when(storageProperties.getMaxFileSize()).thenReturn(104857600L); // 100MB
        when(storageProperties.getAllowedExtensionList()).thenReturn(Arrays.asList("docx", "xlsx", "pptx"));
        when(storageProperties.getAllowedExtensions()).thenReturn("docx,xlsx,pptx");
    }

    @Test
    @DisplayName("허용된 확장자 파일은 검증을 통과한다")
    void validate_AllowedExtension_DoesNotThrow() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx", "application/octet-stream", "content".getBytes());

        // when & then (예외 없음)
        fileValidator.validate(file);
    }

    @Test
    @DisplayName("허용되지 않는 확장자 파일은 INVALID_FILE_TYPE 예외를 던진다")
    void validate_DisallowedExtension_ThrowsInvalidFileType() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "content".getBytes());

        // when & then
        assertThatThrownBy(() -> fileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    @DisplayName("최대 크기를 초과한 파일은 FILE_TOO_LARGE 예외를 던진다")
    void validate_FileTooLarge_ThrowsFileTooLarge() {
        // given
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(200 * 1024 * 1024L); // 200MB

        // when & then
        assertThatThrownBy(() -> fileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("빈 파일은 INVALID_FILE_TYPE 예외를 던진다")
    void validate_EmptyFile_ThrowsInvalidFileType() {
        // given
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> fileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    @DisplayName("extractExtension은 파일명의 확장자를 소문자로 반환한다")
    void extractExtension_ReturnsLowerCaseExtension() {
        // given
        String filename = "Document.DOCX";

        // when
        String ext = fileValidator.extractExtension(filename);

        // then
        assertThat(ext).isEqualTo("docx");
    }
}
