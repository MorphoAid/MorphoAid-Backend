package com.morphoaid.backend.service;

import com.morphoaid.backend.config.S3Config;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class S3StorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    private final S3Client s3Client;
    private final CaseImageRepository caseImageRepository;
    private final CaseRepository caseRepository;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${app.storage.allowed-mimes}")
    private String allowedMimes;

    @Value("${aws.s3.access-key}")
    private String awsAccessKey;

    @Autowired
    public S3StorageService(S3Client s3Client, CaseImageRepository caseImageRepository, CaseRepository caseRepository) {
        this.s3Client = s3Client;
        this.caseImageRepository = caseImageRepository;
        this.caseRepository = caseRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public CaseImage uploadCaseImage(Long caseId, MultipartFile file, User uploader) {
        // Validate MIME
        List<String> validMimes = Arrays.asList(allowedMimes.split(","));
        if (!validMimes.contains(file.getContentType())) {
            throw new IllegalArgumentException("Invalid MIME type: " + file.getContentType());
        }

        // Validate Size (5MB) - redundant if set at Spring level, but good for safety
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.lastIndexOf(".") > 0) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String objectKey = "raw/" + caseId + "/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String checksum = hexString.toString();

            if ("mock-access-key".equals(awsAccessKey)) {
                logger.info("Mock AWS credentials detected. Skipping actual S3 putObject for bucket: {}, Key: {}",
                        bucket, objectKey);
            } else {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build();

                logger.info("Attempting S3 Upload -> Bucket: {}, Key: {}", bucket, objectKey);
                s3Client.putObject(putObjectRequest,
                        RequestBody.fromBytes(file.getBytes()));
                logger.info("SUCCESS: Object securely uploaded to S3 -> Key: {}", objectKey);
            }

            String oldKey = null;
            String oldBucket = null;
            if (aCase.getImage() != null) {
                oldKey = aCase.getImage().getObjectKey();
                oldBucket = aCase.getImage().getBucket();
                aCase.replaceImage(null);
                caseRepository.saveAndFlush(aCase);
            }

            CaseImage caseImage = CaseImage.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .size(file.getSize())
                    .mimeType(file.getContentType())
                    .originalFilename(file.getOriginalFilename())
                    .checksum(checksum)
                    .uploadedBy(uploader)
                    .caseEntity(aCase)
                    .build();

            // Save CaseImage explicitly
            caseImageRepository.save(caseImage);

            aCase.replaceImage(caseImage);
            aCase.setImagePath(objectKey); // Ensure cases table column is updated

            // Force flush to ensure associations are committed before returning
            Case updatedCase = caseRepository.saveAndFlush(aCase);

            logger.info("Attachment link confirmed for Case ID: {} and CaseImage ID: {}. Original Filename: {}",
                    aCase.getId(), caseImage.getId(), caseImage.getOriginalFilename());

            if (oldKey != null && !oldKey.equals(objectKey)) {
                try {
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest deleteReq = software.amazon.awssdk.services.s3.model.DeleteObjectRequest
                            .builder()
                            .bucket(oldBucket)
                            .key(oldKey)
                            .build();
                    s3Client.deleteObject(deleteReq);
                    logger.info("Deleted old S3 object for caseId={}, key={}", caseId, oldKey);
                } catch (Exception e) {
                    logger.warn("Failed to delete old S3 object for caseId={}, key={}: {}", caseId, oldKey,
                            e.getMessage());
                }
            }

            return updatedCase.getImage();

        } catch (IOException e) {
            logger.error("Failed to read file input stream", e);
            throw new RuntimeException("Failed to upload image", e);
        } catch (Exception e) {
            logger.error(
                    "S3 upload failed. bucket=" + bucket
                            + ", caseId=" + caseId
                            + ", filename=" + file.getOriginalFilename(),
                    e);

            throw new RuntimeException(
                    "S3 upload failed: " + e.getClass().getSimpleName()
                            + " - " + e.getMessage(),
                    e);
        }
    }

    @Override
    public InputStream downloadImageContent(Long caseId, Long imageId) {
        CaseImage image = caseImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));

        if (!image.getCaseEntity().getId().equals(caseId)) {
            throw new IllegalArgumentException("Image does not belong to the specified case");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(image.getBucket())
                .key(image.getObjectKey())
                .build();

        ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
        return s3Object;
    }
}
