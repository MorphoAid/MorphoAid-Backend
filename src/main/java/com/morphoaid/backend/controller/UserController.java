package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.UserSummary;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String awsAccessKey;

    @Autowired
    public UserController(UserRepository userRepository, S3Client s3Client) {
        this.userRepository = userRepository;
        this.s3Client = s3Client;
    }

    /**
     * POST /users/me/profile-picture
     * Uploads a profile picture for the currently authenticated user.
     * Stores image to S3 (or skips if mock credentials) and saves the public URL to the user record.
     */
    @PostMapping("/me/profile-picture")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            java.security.Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        // Validate MIME type
        List<String> allowedMimes = Arrays.asList("image/jpeg", "image/png", "image/webp");
        if (!allowedMimes.contains(file.getContentType())) {
            return ResponseEntity.badRequest().body("Invalid file type. Only JPEG, PNG, WebP are allowed.");
        }

        // Validate file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body("File size must not exceed 5MB.");
        }

        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.lastIndexOf(".") > 0) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String objectKey = "profiles/" + user.getId() + "/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
        String profilePictureUrl;

        if ("mock-access-key".equals(awsAccessKey)) {
            // Mock mode: skip actual S3 upload, use a placeholder URL
            logger.info("Mock AWS credentials detected. Skipping actual S3 putObject for profile picture. Key: {}", objectKey);
            profilePictureUrl = "https://via.placeholder.com/150?text=Profile";
        } else {
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build();

                logger.info("Uploading profile picture to S3. Bucket: {}, Key: {}", bucket, objectKey);
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
                logger.info("Profile picture uploaded successfully. Key: {}", objectKey);

                // Build public S3 URL
                profilePictureUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + objectKey;

            } catch (IOException e) {
                logger.error("Failed to read profile picture file", e);
                return ResponseEntity.status(500).body("Failed to read uploaded file.");
            } catch (Exception e) {
                logger.error("S3 upload failed for profile picture: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body("Failed to upload profile picture to storage.");
            }
        }

        user.setProfilePictureUrl(profilePictureUrl);
        userRepository.save(user);
        logger.info("Profile picture URL saved for user ID: {}", user.getId());

        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .profilePictureUrl(buildProfilePictureUrl(user.getProfilePictureUrl(), user.getId()))
                .build();

        return ResponseEntity.ok(summary);
    }

    private String buildProfilePictureUrl(String dbValue, Long userId) {
        if (dbValue == null) return null;
        if (dbValue.contains("via.placeholder.com")) return dbValue;
        try {
            return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/users/{id}/profile-picture")
                .buildAndExpand(userId)
                .toUriString();
        } catch(Exception e) {
            return dbValue;
        }
    }

    @GetMapping("/{id}/profile-picture")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> getProfilePicture(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null || user.getProfilePictureUrl() == null) {
            return ResponseEntity.notFound().build();
        }
        
        String urlOrKey = user.getProfilePictureUrl();
        String prefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        String objectKey;
        if (urlOrKey.startsWith(prefix)) {
            objectKey = urlOrKey.substring(prefix.length());
        } else if (urlOrKey.startsWith("http")) { 
            return ResponseEntity.status(302).header("Location", urlOrKey).build();
        } else {
            objectKey = urlOrKey;
        }

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getReq);
            org.springframework.core.io.InputStreamResource resource = new org.springframework.core.io.InputStreamResource(s3Object);
            
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(s3Object.response().contentType()))
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error fetching profile picture from S3", e);
            return ResponseEntity.status(500).build();
        }
    }
}
