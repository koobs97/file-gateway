package com.file.gateway.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.gateway.admin.dto.UpdateRoleRequest;
import com.file.gateway.admin.dto.UpdateStatusRequest;
import com.file.gateway.admin.dto.UserSummaryResponse;
import com.file.gateway.auth.CustomUserDetailsService;
import com.file.gateway.auth.JwtTokenProvider;
import com.file.gateway.client.repository.ApiClientRepository;
import com.file.gateway.common.config.SecurityConfig;
import com.file.gateway.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

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
    @DisplayName("ADMIN은 사용자 목록을 조회할 수 있다")
    void getUsers_AsAdmin_Returns200() throws Exception {
        UserSummaryResponse user = buildUser(1L, "admin", "ROLE_ADMIN", true);
        when(adminUserService.getUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].username").value("admin"));
    }

    @Test
    @WithMockUser(roles = "END_USER")
    @DisplayName("END_USER는 사용자 목록 조회 시 403을 받는다")
    void getUsers_AsEndUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 사용자 역할을 변경할 수 있다")
    void updateRole_AsAdmin_Returns200() throws Exception {
        UserSummaryResponse updated = buildUser(1L, "user1", "ROLE_AUDITOR", true);
        when(adminUserService.updateRole(eq(1L), eq("ROLE_AUDITOR"))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("ROLE_AUDITOR")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ROLE_AUDITOR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 사용자를 비활성화할 수 있다")
    void updateStatus_Deactivate_Returns200() throws Exception {
        UserSummaryResponse updated = buildUser(1L, "user1", "ROLE_END_USER", false);
        when(adminUserService.updateStatus(eq(1L), eq(false))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStatusRequest(false)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    private UserSummaryResponse buildUser(Long id, String username, String role, boolean active) {
        return new UserSummaryResponse(id, username, role, active, LocalDateTime.now());
    }
}
