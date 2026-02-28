package com.morphoaid.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.s3.bucket}")
    private String awsBucket;

    @Value("${aws.s3.access-key}")
    private String awsAccessKey;

    @Value("${aws.s3.secret-key}")
    private String awsSecretKey;

    @PostConstruct
    public void init() {
        logger.info("================ Storage Phase Verification ================");
        logger.info("AWS S3 Config Loaded:");
        logger.info(" - Region: {}", awsRegion);
        logger.info(" - Bucket: {}", awsBucket);
        logger.info(" - Access Key Provided: {}", awsAccessKey != null && !awsAccessKey.isEmpty());
        logger.info(" - Secret Key Provided: {}", awsSecretKey != null && !awsSecretKey.isEmpty());
        // To help debug
        logger.info("ENV AWS_ACCESS_KEY_ID present? {}", System.getenv("AWS_ACCESS_KEY_ID") != null);
        logger.info("ENV AWS_SECRET_ACCESS_KEY present? {}", System.getenv("AWS_SECRET_ACCESS_KEY") != null);
        logger.info("===========================================================");
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                .build();
    }
}
