package com.arash.fpllab.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Neon Object Storage client. Neon injects AWS-standard variables (AWS_ENDPOINT_URL_S3, AWS_REGION,
 * AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY); they are read from Spring's environment so the local
 * .env.local file works too. Only created when a storage endpoint is configured.
 */
@Configuration
@ConditionalOnProperty("AWS_ENDPOINT_URL_S3")
public class StorageConfig {

    // Neon requires path-style addressing (https://endpoint/bucket/key)
    private static final S3Configuration PATH_STYLE = S3Configuration.builder().pathStyleAccessEnabled(true).build();

    @Bean(destroyMethod = "close")
    public S3Client s3Client(Environment env) {
        return S3Client.builder()
                .endpointOverride(URI.create(env.getRequiredProperty("AWS_ENDPOINT_URL_S3")))
                .region(Region.of(env.getProperty("AWS_REGION", "us-east-1")))
                .credentialsProvider(credentials(env))
                .serviceConfiguration(PATH_STYLE)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(Environment env) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(env.getRequiredProperty("AWS_ENDPOINT_URL_S3")))
                .region(Region.of(env.getProperty("AWS_REGION", "us-east-1")))
                .credentialsProvider(credentials(env))
                .serviceConfiguration(PATH_STYLE)
                .build();
    }

    private static StaticCredentialsProvider credentials(Environment env) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                env.getRequiredProperty("AWS_ACCESS_KEY_ID"), env.getRequiredProperty("AWS_SECRET_ACCESS_KEY")));
    }
}
