package com.arash.fpllab.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Stores files in Neon Object Storage (private bucket). Used to archive each gameweek's
 * predictions as JSON: a permanent record for the accuracy page and training data for the ML model.
 * Does nothing when storage isn't configured (e.g. plain local Docker).
 */
@Service
public class ArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveService.class);
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public ArchiveService(ObjectProvider<S3Client> s3, ObjectProvider<S3Presigner> presigner,
                          @Value("${fpl.storage.bucket:assets}") String bucket) {
        this.s3 = s3.getIfAvailable();
        this.presigner = presigner.getIfAvailable();
        this.bucket = bucket;
    }

    public boolean isEnabled() {
        return s3 != null && presigner != null;
    }

    public static String predictionsKey(int event) {
        return "predictions/gw" + event + ".json";
    }

    public void putJson(String key, String json) {
        if (!isEnabled()) {
            return;
        }
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType("application/json").build(),
                RequestBody.fromString(json, StandardCharsets.UTF_8));
        logger.info("Archived {} to bucket {}", key, bucket);
    }

    public Optional<String> getText(String key) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    public boolean exists(String key) {
        if (!isEnabled()) {
            return false;
        }
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    /** Time-limited download link for a private object, so no credentials reach the browser. */
    public Optional<String> downloadLink(String key) {
        if (!exists(key)) {
            return Optional.empty();
        }
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(LINK_LIFETIME)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return Optional.of(presigner.presignGetObject(request).url().toString());
    }
}
