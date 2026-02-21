package com.morphoaid.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphoaid.backend.dto.CreateInvitationRequest;
import com.morphoaid.backend.entity.InvitationToken;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.InvitationTokenRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminInvitationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvitationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.morphoaid.backend.repository.CaseRepository caseRepository;

    @Autowired
    private com.morphoaid.backend.repository.AIResultRepository aiResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiResultRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();
        tokenRepository.deleteAll();

        User admin = User.builder()
                .email("admin@test.com")
                .password("encoded_pass")
                .role(Role.ADMIN)
                .build();
        User dataUse = User.builder()
                .email("datause@test.com")
                .password("encoded_pass")
                .role(Role.DATA_USE)
                .build();
        userRepository.save(admin);
        userRepository.save(dataUse);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testAdmin_CreateInvitationToken_Success() throws Exception {
        CreateInvitationRequest request = new CreateInvitationRequest(7);

        mockMvc.perform(post("/admin/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.usedAt").doesNotExist());
    }

    @Test
    @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
    void testDataUse_CreateInvitationToken_Forbidden() throws Exception {
        CreateInvitationRequest request = new CreateInvitationRequest(7);

        mockMvc.perform(post("/admin/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testAdmin_ListInvitationTokens_Success() throws Exception {
        InvitationToken token = InvitationToken.builder()
                .token("TEST_LIST_TOKEN_123")
                .role(Role.DATA_PREP)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();
        tokenRepository.save(token);

        mockMvc.perform(get("/admin/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].token").value("TEST_LIST_TOKEN_123"))
                .andExpect(jsonPath("$[0].expiresAt").exists());
    }

    @Test
    @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
    void testDataUse_ListInvitationTokens_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/invitations"))
                .andExpect(status().isForbidden());
    }
}
