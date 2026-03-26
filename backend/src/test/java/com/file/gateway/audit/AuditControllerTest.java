package com.file.gateway.audit;

import com.file.gateway.audit.dto.ProcessLogResponse;
import com.file.gateway.auth.CustomUserDetailsService;
import com.file.gateway.auth.JwtTokenProvider;
import com.file.gateway.client.repository.ApiClientRepository;
import com.file.gateway.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

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

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 전체 처리 로그를 조회할 수 있다")
    void getProcessLogs_AsAdmin_Returns200() throws Exception {
        ProcessLogResponse log = buildLog(1L, 10L);
        when(auditService.getProcessLogs(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get("/api/v1/audit/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].fileId").value(10));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    @DisplayName("AUDITOR는 전체 처리 로그를 조회할 수 있다")
    void getProcessLogs_AsAuditor_Returns200() throws Exception {
        when(auditService.getProcessLogs(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/audit/files"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "END_USER")
    @DisplayName("END_USER는 감사 로그 조회 시 403을 받는다")
    void getProcessLogs_AsEndUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit/files"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 특정 파일 처리 로그를 조회할 수 있다")
    void getFileProcessLogs_AsAdmin_Returns200() throws Exception {
        when(auditService.getFileProcessLogs(1L))
                .thenReturn(List.of(buildLog(1L, 1L)));

        mockMvc.perform(get("/api/v1/audit/files/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    private ProcessLogResponse buildLog(Long id, Long fileId) {
        return new ProcessLogResponse(id, fileId, "UPLOAD", "SUCCESS", "처리 완료", LocalDateTime.now());
    }
}
