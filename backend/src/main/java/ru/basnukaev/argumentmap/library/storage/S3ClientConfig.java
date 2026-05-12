package ru.basnukaev.argumentmap.library.storage;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Spring конфигурация для {@code S3Client} AWS SDK v2 (ADR-024).
 * Поднимает один singleton client с настройками из
 * {@link ObjectStorageProperties}.
 *
 * <p>Особенности конфигурации:
 * <ul>
 *   <li>{@code pathStyleAccess=true} - обязательно для MinIO (использует
 *       {@code host/bucket/key} URL вместо virtual-hosted). Для AWS S3
 *       в проде безопасен - AWS поддерживает оба формата</li>
 *   <li>{@code UrlConnectionHttpClient} - lightweight blocking client.
 *       Default netty (async/reactive) тяжелее, не нужен для синхронного
 *       use-case с Range streaming через ServletOutputStream</li>
 *   <li>Retry policy - встроенный exponential backoff на 5xx, throttling,
 *       connection reset. {@code maxRetries} из properties</li>
 *   <li>{@code endpointOverride} - dev указывает на MinIO localhost,
 *       prod на S3 провайдера</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class S3ClientConfig {

    private final ObjectStorageProperties properties;

    public S3ClientConfig(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(), properties.secretKey());

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();

        RetryPolicy retryPolicy = RetryPolicy.defaultRetryPolicy().toBuilder()
                .numRetries(properties.maxRetries())
                .build();

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(retryPolicy)
                .apiCallTimeout(properties.readTimeout())
                .apiCallAttemptTimeout(properties.readTimeout())
                .build();

        UrlConnectionHttpClient.Builder httpClientBuilder = UrlConnectionHttpClient.builder()
                .connectionTimeout(properties.connectTimeout())
                .socketTimeout(properties.readTimeout());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .overrideConfiguration(overrideConfig)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
