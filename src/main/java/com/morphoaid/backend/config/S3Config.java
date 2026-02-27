package com.morphoaid.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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

    @PostConstruct
    public void init() {
        logger.info("================ Storage Phase Verification ================");
        logger.info("AWS S3 Config Loaded:");
        logger.info(" - Region: {}", awsRegion);
        logger.info(" - Bucket: {}", awsBucket);
        logger.info(" - Using: DefaultCredentialsProvider");
        logger.info("===========================================================");
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
