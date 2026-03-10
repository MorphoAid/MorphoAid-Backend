package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private CaseImageRepository caseImageRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private S3StorageService s3StorageService;

    private User uploader;
    private Case aCase;
    private MockMultipartFile validFile;

    @BeforeEach
    void setUp() {
        // Inject configuration values manually since we aren't using Spring context
        ReflectionTestUtils.setField(s3StorageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3StorageService, "allowedMimes", "image/jpeg,image/png");
        ReflectionTestUtils.setField(s3StorageService, "awsAccessKey", "real-key");

        uploader = User.builder().id(1L).email("tester@example.com").build();
        aCase = Case.builder().id(100L).uploadedBy(uploader).build();

        validFile = new MockMultipartFile(
                "image",
                "test.jpg",
                "image/jpeg",
                "dummy image content".getBytes());
    }

    // UTC-07-TC-01 Upload file successfully to storage
    @Test
    void uploadCaseImage_Success() {
        // Arrange
        when(caseRepository.findById(100L)).thenReturn(Optional.of(aCase));
        when(caseImageRepository.save(any(CaseImage.class))).thenAnswer(i -> {
            CaseImage img = i.getArgument(0);
            img.setId(200L);
            return img;
        });
        when(caseRepository.saveAndFlush(any(Case.class))).thenReturn(aCase);

        // Act
        CaseImage result = s3StorageService.uploadCaseImage(100L, validFile, uploader);

        // Assert
        assertNotNull(result);
        assertEquals("test-bucket", result.getBucket());
        assertTrue(result.getObjectKey().contains("raw/100/"));
        assertEquals("image/jpeg", result.getMimeType());

        // Verify S3 communication
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(caseImageRepository, times(1)).save(any(CaseImage.class));
        verify(caseRepository, times(1)).saveAndFlush(aCase);
    }

    // UTC-07-TC-02 Handle upload failure when storage provider throws an exception
    @Test
    void uploadCaseImage_S3Exception_ThrowsRuntimeException() {
        // Arrange
        when(caseRepository.findById(100L)).thenReturn(Optional.of(aCase));

        // Simulate S3 failure
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3StorageService.uploadCaseImage(100L, validFile, uploader);
        });

        assertTrue(exception.getMessage().contains("S3 upload failed"));
        verify(caseImageRepository, never()).save(any());
    }

    // Extra: Handle file that exceeds allowed size limit
    @Test
    void uploadCaseImage_SizeExceeded_ThrowsIllegalArgumentException() {
        // Arrange
        byte[] largeContent = new byte[(5 * 1024 * 1024) + 1]; // 5MB + 1 byte
        MockMultipartFile largeFile = new MockMultipartFile(
                "image", "large.jpg", "image/jpeg", largeContent);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            s3StorageService.uploadCaseImage(100L, largeFile, uploader);
        });

        assertEquals("File size exceeds 5MB limit", exception.getMessage());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // Extra: Handle file with invalid MIME type
    @Test
    void uploadCaseImage_InvalidMime_ThrowsIllegalArgumentException() {
        // Arrange
        MockMultipartFile invalidFile = new MockMultipartFile(
                "document", "test.pdf", "application/pdf", "pdf content".getBytes());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            s3StorageService.uploadCaseImage(100L, invalidFile, uploader);
        });

        assertTrue(exception.getMessage().contains("Invalid MIME type"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // UTC-08-TC-01 Resolve stored file successfully from storage
    @Test
    void downloadImageContent_Success() {
        // Arrange
        CaseImage existingImage = CaseImage.builder()
                .id(200L)
                .caseEntity(aCase)
                .bucket("test-bucket")
                .objectKey("raw/100/test.jpg")
                .build();

        when(caseImageRepository.findById(200L)).thenReturn(Optional.of(existingImage));

        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> mockS3Stream = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockS3Stream);

        // Act
        InputStream resultStream = s3StorageService.downloadImageContent(100L, 200L);

        // Assert
        assertNotNull(resultStream);
        assertSame(mockS3Stream, resultStream);
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    }

    // UTC-08-TC-02 Handle missing storage object during file resolution
    @Test
    void downloadImageContent_ImageNotFound_ThrowsIllegalArgumentException() {
        // Arrange
        when(caseImageRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            s3StorageService.downloadImageContent(100L, 999L);
        });

        assertEquals("Image not found", exception.getMessage());
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }
}
