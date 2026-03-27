package com.file.gateway.file.controller;

import com.file.gateway.auth.CustomUserDetailsService;
import com.file.gateway.auth.JwtTokenProvider;
import com.file.gateway.client.repository.ApiClientRepository;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.file.dto.FileDetailResponse;
import com.file.gateway.file.dto.FileUploadResponse;
import com.file.gateway.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    @SuppressWarnings("unused")
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    @SuppressWarnings("unused")
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    @SuppressWarnings("unused")
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    @SuppressWarnings("unused")
    private ApiClientRepository apiClientRepository;

    // ─── POST /api/v1/files ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "END_USER")
    @DisplayName("유효한 파일 업로드 요청 시 201과 fileId를 반환한다")
    void upload_ValidFile_Returns201WithFileId() throws Exception {
        // given
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "report.docx",
                MediaType.MULTIPART_FORM_DATA_VALUE,
                "dummy".getBytes());

        when(fileService.upload(any(), anyString())).thenReturn(
                new FileUploadResponse(1L, "report.docx", 5L, "UPLOADED"));

        // when & then
        mockMvc.perform(multipart("/api/v1/files").file(mockFile).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(roles = "END_USER")
    @DisplayName("업로드 중 서비스 예외 발생 시 400 응답을 반환한다")
    void upload_ServiceThrowsException_Returns400() throws Exception {
        // given
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "malware.exe",
                MediaType.MULTIPART_FORM_DATA_VALUE,
                "dummy".getBytes());

        when(fileService.upload(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_FILE_TYPE));

        // when & then
        mockMvc.perform(multipart("/api/v1/files").file(mockFile).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
    }

    // ─── GET /api/v1/files ────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("파일 목록 조회 시 200과 페이징 결과를 반환한다")
    void getFiles_Returns200WithPagedResult() throws Exception {
        // given
        FileDetailResponse detail = buildDetail(1L, "test.docx", "UPLOADED");
        when(fileService.getFiles(any(), any(), any(Pageable.class), anyString(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(detail)));

        // when & then
        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].originalName").value("test.docx"));
    }

    // ─── GET /api/v1/files/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("존재하는 파일 ID 조회 시 200과 파일 정보를 반환한다")
    void getFile_ExistingId_Returns200() throws Exception {
        // given
        FileDetailResponse detail = buildDetail(1L, "test.docx", "UPLOADED");
        when(fileService.getFile(eq(1L), anyString(), anyBoolean())).thenReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/files/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.originalName").value("test.docx"));
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 파일 ID 조회 시 404를 반환한다")
    void getFile_NonExistingId_Returns404() throws Exception {
        // given
        when(fileService.getFile(eq(99L), anyString(), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.FILE_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/files/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    // ─── DELETE /api/v1/files/{id} ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("파일 삭제 요청 시 200을 반환한다")
    void deleteFile_ExistingId_Returns200() throws Exception {
        // given
        doNothing().when(fileService).deleteFile(1L);

        // when & then
        mockMvc.perform(delete("/api/v1/files/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private FileDetailResponse buildDetail(Long id, String name, String status) {
        return new FileDetailResponse(id, name, 1024L, "application/octet-stream",
                "LOCAL", status, LocalDateTime.now(), LocalDateTime.now());
    }
}
