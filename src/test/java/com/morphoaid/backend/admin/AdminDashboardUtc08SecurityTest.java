package com.morphoaid.backend.admin;

import com.morphoaid.backend.controller.SystemController;
import com.morphoaid.backend.service.SystemStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(com.morphoaid.backend.security.MethodSecurityConfig.class)
public class AdminDashboardUtc08SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemStatusService systemStatusService;

    @MockitoBean
    private com.morphoaid.backend.security.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private com.morphoaid.backend.repository.UserRepository userRepository;

    @Test
    @WithMockUser(roles = "DATA_USE") // Non-admin role
    public void UTC_08_TC_04_toggleAiStatus_forbidden() throws Exception {
        mockMvc.perform(post("/system/status/ai/toggle").with(csrf()))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void UTC_08_TC_03_toggleAiStatus_authorized() throws Exception {
        mockMvc.perform(post("/system/status/ai/toggle").with(csrf()))
               .andExpect(status().isOk());
    }
}
