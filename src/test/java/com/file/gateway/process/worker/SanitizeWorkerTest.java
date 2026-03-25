package com.file.gateway.process.worker;

import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.file.entity.FileMetadata;
import com.file.gateway.file.entity.FileStatus;
import com.file.gateway.file.entity.StorageType;
import com.file.gateway.file.repository.FileMetadataRepository;
import com.file.gateway.process.cdr.analyzer.AnalysisResult;
import com.file.gateway.process.cdr.analyzer.OfficeAnalyzer;
import com.file.gateway.process.cdr.sanitizer.OfficeSanitizer;
import com.file.gateway.process.event.FileEventService;
import com.file.gateway.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SanitizeWorkerTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private StorageService storageService;
    @Mock private FileEventService fileEventService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private OfficeAnalyzer mockAnalyzer;
    private OfficeSanitizer mockSanitizer;
    private SanitizeWorker worker;

    @BeforeEach
    void setUp() {
        mockAnalyzer = mock(OfficeAnalyzer.class);
        mockSanitizer = mock(OfficeSanitizer.class);
        worker = new SanitizeWorker(
                fileMetadataRepository, storageService, fileEventService,
                messagingTemplate, List.of(mockAnalyzer), List.of(mockSanitizer)
        );
    }

    // ─── process 성공 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 파일 처리 시 status=DONE으로 업데이트되고 WebSocket 알림이 전송된다")
    void process_Success_UpdatesStatusToDoneAndNotifies() throws Exception {
        // given
        FileMetadata file = buildFile(1L, "report.docx", FileStatus.PROCESSING);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
        when(storageService.load(anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream(new byte[0])); // analyze용, sanitize용
        when(mockAnalyzer.supports("docx")).thenReturn(true);
        when(mockAnalyzer.analyze(any())).thenReturn(AnalysisResult.safe());
        when(mockSanitizer.supports("docx")).thenReturn(true);
        when(storageService.save(any(), anyString(), eq("sanitized")))
                .thenReturn("/uploads/sanitized/uuid.docx");

        // when
        worker.process(1L);

        // then
        assertThat(file.getStatus()).isEqualTo(FileStatus.DONE);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/files/1"), any(FileStatusNotification.class));
    }

    @Test
    @DisplayName("위협이 탐지된 파일도 무해화 성공 시 status=DONE이 된다")
    void process_WithThreat_SanitizesAndUpdatesDone() throws Exception {
        // given
        FileMetadata file = buildFile(1L, "macro.docx", FileStatus.PROCESSING);
        AnalysisResult threatened = new AnalysisResult(true, false, false, List.of("VBA 매크로 탐지: word/vbaProject.bin"));

        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
        when(storageService.load(anyString())).thenAnswer(inv -> new ByteArrayInputStream(new byte[0]));
        when(mockAnalyzer.supports("docx")).thenReturn(true);
        when(mockAnalyzer.analyze(any())).thenReturn(threatened);
        when(mockSanitizer.supports("docx")).thenReturn(true);
        when(storageService.save(any(), anyString(), eq("sanitized")))
                .thenReturn("/uploads/sanitized/uuid.docx");

        // when
        worker.process(1L);

        // then
        assertThat(file.getStatus()).isEqualTo(FileStatus.DONE);
        verify(mockSanitizer).sanitize(any(), any());
    }

    // ─── process 실패 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 fileId 처리 시 FILE_NOT_FOUND 예외를 던진다")
    void process_FileNotFound_ThrowsBusinessException() {
        // given
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> worker.process(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_NOT_FOUND));
    }

    @Test
    @DisplayName("무해화 중 예외 발생 시 status=FAIL로 업데이트되고 WebSocket 알림이 전송된다")
    void process_SanitizeThrowsException_UpdatesStatusToFail() throws Exception {
        // given
        FileMetadata file = buildFile(1L, "report.docx", FileStatus.PROCESSING);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
        when(storageService.load(anyString())).thenAnswer(inv -> new ByteArrayInputStream(new byte[0]));
        when(mockAnalyzer.supports("docx")).thenReturn(true);
        when(mockAnalyzer.analyze(any())).thenReturn(AnalysisResult.safe());
        when(mockSanitizer.supports("docx")).thenReturn(true);
        doThrow(new IOException("ZIP 처리 오류")).when(mockSanitizer).sanitize(any(), any());

        // when
        worker.process(1L);

        // then
        assertThat(file.getStatus()).isEqualTo(FileStatus.FAIL);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/files/1"), any(FileStatusNotification.class));
    }

    @Test
    @DisplayName("지원하지 않는 확장자 파일은 status=FAIL로 처리된다")
    void process_UnsupportedExtension_UpdatesStatusToFail() {
        // given
        FileMetadata file = buildFile(1L, "test.unknown", FileStatus.PROCESSING);
        when(fileMetadataRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
        when(mockAnalyzer.supports("unknown")).thenReturn(false);

        // when
        worker.process(1L);

        // then
        assertThat(file.getStatus()).isEqualTo(FileStatus.FAIL);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/files/1"), any(FileStatusNotification.class));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private FileMetadata buildFile(Long id, String name, FileStatus status) {
        FileMetadata file = FileMetadata.builder()
                .originalName(name)
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
