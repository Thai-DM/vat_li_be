package com.vatly1.example.configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@Getter
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket-name:vatly1-bucket}")
    private String bucketName;

    @Value("${minio.url-prefix:http://localhost:9000/vatly1-bucket}")
    private String urlPrefix;

    @Value("${minio.enabled:true}")
    private boolean enabled;

    @Bean
    @ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
    public MinioClient minioClient() {
        try {
            log.info("Initializing MinIO Client for endpoint: {}", endpoint);
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            initBucket(client);
            return client;
        } catch (Exception e) {
            log.warn("Could not connect to MinIO on startup: {}", e.getMessage());
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }
    }

    public void initBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                log.info("MinIO bucket '{}' does not exist. Creating bucket...", bucketName);
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO bucket '{}' created successfully.", bucketName);

                // Set public read policy for direct asset/media access
                String policy = """
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Effect": "Allow",
                                "Principal": {"AWS": ["*"]},
                                "Action": ["s3:GetObject"],
                                "Resource": ["arn:aws:s3:::%s/*"]
                            }
                        ]
                    }
                    """.formatted(bucketName);
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
                log.info("Public read policy configured on bucket '{}'.", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket auto-check deferred: {}", e.getMessage());
        }
    }
}
