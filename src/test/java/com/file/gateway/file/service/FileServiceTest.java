package com.file.gateway.file.service;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.file.dto.FileDetailResponse;
import com.file.gateway.file.dto.FileUploadResponse;
import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import com.file.gateway.file.entity.StorageType;
import com.file.gateway.file.repository.FileMetadataRepository;
import com.file.gateway.process.event.FileEventService;
import com.file.gateway.process.event.repository.FileEventRepository;
import com.file.gateway.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private FileEventRepository fileEventRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private FileEventService fileEventService;

    @InjectMocks
    private FileService fileService;

    // ─── upload ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 파일 업로드 시 UPLOADED 상태로 저장하고 응답을 반환한다")
    void upload_ValidFile_ReturnsUploadedResponse() throws IOException {
        // given
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "dummy content".getBytes());

        when(fileValidator.extractExtension("report.docx")).thenReturn("docx");
        when(storageService.save(any(), anyString(), eq("original")))
                .thenReturn("/uploads/original/uuid.docx");
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> {
            FileMetadata saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        // when
        FileUploadResponse result = fileService.upload(mockFile);

        // then
        assertThat(result.fileId()).isEqualTo(1L);
        assertThat(result.originalName()).isEqualTo("report.docx");
        assertThat(result.status()).isEqualTo("UPLOADED");
        verify(fileEventService).publishEvent(any(), any(), any());
        verify(fileEventService).saveProcessLog(any(), any(), any(), anyString());
    }

    // ─── getFile ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 파일 ID 조회 시 FileDetailResponse를 반환한다")
    void getFile_ExistingId_ReturnsDetail() {
        // given
        FileMetadata file = buildFile(1L, FileStatus.UPLOADED);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));

        // when
        FileDetailResponse result = fileService.getFile(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo("UPLOADED");
    }

    @Test
    @DisplayName("존재하지 않는 파일 ID 조회 시 FILE_NOT_FOUND 예외를 던진다")
    void getFile_NonExistingId_ThrowsFileNotFound() {
        // given
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> fileService.getFile(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_NOT_FOUND));
    }

    // ─── deleteFile ────────────────────────────────────────────────────────

    @Test
    @DisplayName("파일 삭제 시 soft delete가 수행된다")
    void deleteFile_ExistingId_SetsDeletedAt() {
        // given
        FileMetadata file = buildFile(1L, FileStatus.UPLOADED);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));

        // when
        fileService.deleteFile(1L);

        // then
        assertThat(file.isDeleted()).isTrue();
        assertThat(file.getDeletedAt()).isNotNull();
    }

    // ─── downloadSanitizedFile ─────────────────────────────────────────────

    @Test
    @DisplayName("DONE 상태가 아닌 파일 다운로드 요청 시 PROCESS_FAILED 예외를 던진다")
    void downloadSanitizedFile_NotDoneStatus_ThrowsProcessFailed() {
        // given
        FileMetadata file = buildFile(1L, FileStatus.UPLOADED);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));

        // when & then
        assertThatThrownBy(() -> fileService.downloadSanitizedFile(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PROCESS_FAILED));
    }

    @Test
    @DisplayName("DONE 상태지만 무해화 파일이 없으면 FILE_NOT_FOUND 예외를 던진다")
    void downloadSanitizedFile_FileNotExists_ThrowsFileNotFound() {
        // given
        FileMetadata file = buildFile(1L, FileStatus.DONE);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
        when(storageService.resolvePath(anyString(), eq("sanitized"))).thenReturn("/uploads/sanitized/uuid.docx");
        when(storageService.exists("/uploads/sanitized/uuid.docx")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> fileService.downloadSanitizedFile(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_NOT_FOUND));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private FileMetadata buildFile(Long id, FileStatus status) {
        FileMetadata file = FileMetadata.builder()
                .originalName("test.docx")
                .storedName("uuid.docx")
                .fileSize(1024L)
                .mimeType("application/octet-stream")
                .storagePath("/uploads/original/uuid.docx")
                .storageType(StorageType.LOCAL)
                .status(status)
                .build();
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }
}
