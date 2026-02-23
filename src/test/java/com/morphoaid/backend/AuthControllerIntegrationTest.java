package com.morphoaid.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphoaid.backend.dto.RegisterDataPrepRequest;
import com.morphoaid.backend.dto.LoginRequest;
import com.morphoaid.backend.dto.RegisterDataUseRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private InvitationTokenRepository tokenRepository;

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
        }

        private RegisterDataUseRequest createValidDataUseRequest() {
                RegisterDataUseRequest req = new RegisterDataUseRequest();
                req.setUsername("validuser");
                req.setFirstName("John");
                req.setLastName("Doe");
                req.setEmail("validuser@example.com");
                req.setPassword("Password123!");
                req.setConfirmPassword("Password123!");
                req.setAgreeTerms(true);
                return req;
        }

        @Test
        void testPublicRegistration_Success() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.user.email").value("validuser@example.com"))
                                .andExpect(jsonPath("$.user.role").value("DATA_USE"));
        }

        @Test
        void testPublicRegistration_UsernameTooShort_Returns400() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                request.setUsername("ab"); // Too short
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.username").exists());
        }

        @Test
        void testPublicRegistration_InvalidFirstName_Returns400() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                request.setFirstName("John123"); // Invalid format
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.firstName").exists());
        }

        @Test
        void testPublicRegistration_ConfirmPasswordMismatch_Returns400() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                request.setConfirmPassword("Mismatch123!"); // Mismatch
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.confirmPassword").exists());
        }

        @Test
        void testPublicRegistration_AgreeTermsFalse_Returns400() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                request.setAgreeTerms(false); // False
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.agreeTerms").exists());
        }

        @Test
        void testDataPrepRegistration_Success() throws Exception {
                InvitationToken token = InvitationToken.builder()
                                .token("VALID_TOKEN")
                                .role(Role.DATA_PREP)
                                .expiresAt(LocalDateTime.now().plusDays(1))
                                .build();
                tokenRepository.save(token);

                RegisterDataPrepRequest request = new RegisterDataPrepRequest();
                request.setUsername("dataprep");
                request.setFirstName("Jane");
                request.setLastName("Smith");
                request.setEmail("dataprep@example.com");
                request.setPassword("Password123!");
                request.setConfirmPassword("Password123!");
                request.setAgreeTerms(true);
                request.setInvitationToken("VALID_TOKEN");

                mockMvc.perform(post("/auth/register/dataprep")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.user.role").value("DATA_PREP"));
        }

        @Test
        void testDataPrepRegistration_WithoutToken_Returns400() throws Exception {
                RegisterDataPrepRequest request = new RegisterDataPrepRequest();
                request.setUsername("dataprep2");
                request.setFirstName("Jane");
                request.setLastName("Smith");
                request.setEmail("dataprep2@example.com");
                request.setPassword("Password123!");
                request.setConfirmPassword("Password123!");
                request.setAgreeTerms(true);
                request.setInvitationToken(null); // Missing token

                mockMvc.perform(post("/auth/register/dataprep")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.invitationToken").exists());
        }

        @Test
        void testLogin_Success() throws Exception {
                RegisterDataUseRequest request = createValidDataUseRequest();
                request.setEmail("loginuser@example.com");
                request.setUsername("loguser");

                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("loginuser@example.com");
                loginRequest.setPassword("Password123!");

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists());
        }

        @Test
        @org.springframework.security.test.context.support.WithMockUser(username = "loginuser@example.com", roles = "DATA_USE")
        void testGetMe_Authenticated_Success() throws Exception {
                User targetUser = User.builder()
                                .email("loginuser@example.com")
                                .username("loginuser")
                                .password("password123")
                                .role(Role.DATA_USE)
                                .build();
                userRepository.save(targetUser);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value("loginuser@example.com"))
                                .andExpect(jsonPath("$.role").value("DATA_USE"));
        }

        @Test
        void testGetMe_Unauthenticated_Unauthorized() throws Exception {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me"))
                                .andExpect(status().isForbidden());
        }
}
