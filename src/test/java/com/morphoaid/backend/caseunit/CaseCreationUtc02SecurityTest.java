package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.ClinicalCaseController;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ClinicalCaseService;
import com.morphoaid.backend.security.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.morphoaid.backend.security.JwtService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClinicalCaseController.class)
@Import({MethodSecurityConfig.class})
public class CaseCreationUtc02SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicalCaseService clinicalCaseService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        com.morphoaid.backend.entity.User user = com.morphoaid.backend.entity.User.builder()
                .email("user")
                .role(com.morphoaid.backend.entity.Role.DATA_USE)
                .build();
        org.mockito.Mockito.when(userRepository.findByEmail("user")).thenReturn(java.util.Optional.of(user));
    }

    @Test
    @WithMockUser(roles = "DATA_USE")
    public void UTC_02_TC_08_uploadCase_roleDataUse_success() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart("/clinical/upload")
                .file(image)
                .param("provinceCode", "10")
                .param("provinceName", "Bangkok")
                .param("consent", "true")
                .with(csrf())) // CSRF usually required for multipart in security context
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    public void UTC_02_TC_08_uploadCase_unauthorizedRole_forbidden() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart("/clinical/upload")
                .file(image)
                .param("provinceCode", "10")
                .param("provinceName", "Bangkok")
                .param("consent", "true")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void UTC_02_TC_08_uploadCase_unauthenticated_unauthorized() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart("/clinical/upload")
                .file(image)
                .param("provinceCode", "10")
                .param("provinceName", "Bangkok")
                .param("consent", "true")
                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
