package com.perimity.campus.storage;

import java.io.InputStream;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Real S3. Active only when perimity.storage.type=s3.
 *
 * NO CREDENTIALS ANYWHERE IN THIS CLASS, and that is the point. The SDK's
 * default provider chain finds them from environment variables locally and
 * from the EC2 instance role in production. An access key written into a
 * properties file is an access key that ends up in git.
 *
 * The bucket stays PRIVATE. Nothing is ever made public-read. Reads go through
 * short-lived presigned URLs, so a link that leaks stops working in minutes
 * rather than never.
 */
@Service
@ConditionalOnProperty(name = "perimity.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageService(@Value("${perimity.storage.bucket}") String bucket,
                            @Value("${perimity.storage.region}") String region) {
        this.bucket = bucket;
        Region r = Region.of(region);
        this.s3 = S3Client.builder().region(r).build();
        this.presigner = S3Presigner.builder().region(r).build();
        log.info("Storage is S3: bucket={} region={}", bucket, region);
    }

    @Override
    public StoredObject put(String key, InputStream content, long sizeBytes, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            // Server-side encryption at rest. One line, and it
                            // is the difference between a lost bucket being an
                            // incident and being a headline.
                            .serverSideEncryption(
                                    software.amazon.awssdk.services.s3.model.ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromInputStream(content, sizeBytes));

            return new StoredObject(key, contentType, sizeBytes);

        } catch (RuntimeException e) {
            throw new StorageException("Could not upload " + key + " to S3", e);
        }
    }

    @Override
    public String presignedReadUrl(String key, Duration validFor) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build()).url().toString();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Could not delete {} from S3: {}", key, e.getMessage());
        }
    }
}
