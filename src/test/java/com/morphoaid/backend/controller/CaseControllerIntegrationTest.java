package com.morphoaid.backend.controller;

import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private S3Client s3Client; // so it doesn't try to connect to AWS during context load

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("tech@example.com");
        testUser.setRole(Role.DATA_PREP);

        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        Mockito.when(userRepository.findByEmail("tech@example.com")).thenReturn(Optional.of(testUser));

        Case testCase = new Case();
        testCase.setId(1L);
        testCase.setUploadedBy(testUser);
        Mockito.when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
    }

    @Test
    @WithMockUser(username = "tech@example.com", roles = "DATA_PREP")
    public void testUploadCaseImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "test.png", MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes());

        CaseImage dummyImage = new CaseImage();
        dummyImage.setId(100L);
        dummyImage.setObjectKey("dummy_key.png");
        dummyImage.setBucket("morphoaid-data");

        Mockito.when(storageService.uploadCaseImage(eq(1L), any(), any())).thenReturn(dummyImage);

        mockMvc.perform(multipart("/cases/1/images")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @WithMockUser(username = "tech@example.com", roles = "DATA_USE")
    public void testDownloadImageContent_Success() throws Exception {
        byte[] dummyBytes = "dummy image data".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(dummyBytes);

        Mockito.when(storageService.downloadImageContent(1L, 100L)).thenReturn(is);

        mockMvc.perform(get("/cases/1/images/100/content"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(dummyBytes));
    }

    @Test
    public void testUploadCaseImage_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "test.png", MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes());

        mockMvc.perform(multipart("/cases/1/images")
                .file(file))
                .andExpect(status().isForbidden()); // Method security should block this
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = "DATA_PREP")
    public void testUploadCaseImage_UnauthorizedCaseAccess() throws Exception {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setRole(Role.DATA_PREP);
        Mockito.when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        MockMultipartFile file = new MockMultipartFile("image", "test.png", MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes());

        mockMvc.perform(multipart("/cases/1/images")
                .file(file))
                .andExpect(status().isForbidden()); // AccessDeniedException mapped to 403
    }

    @Test
    @WithMockUser(username = "tech@example.com", roles = "DATA_USE")
    public void testDownloadImageContent_ImageNotBelongingToCase() throws Exception {
        Mockito.when(storageService.downloadImageContent(1L, 999L))
                .thenThrow(new IllegalArgumentException("Image does not belong to the specified case"));

        mockMvc.perform(get("/cases/1/images/999/content"))
                .andExpect(status().isBadRequest());
    }
}
