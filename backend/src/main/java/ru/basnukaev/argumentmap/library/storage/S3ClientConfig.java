package ru.basnukaev.argumentmap.library.storage;

import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
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
 *   <li>Retry strategy - {@code AwsRetryStrategy.standardRetryStrategy()}
 *       (современный API вместо deprecated {@code RetryPolicy}). Встроенный
 *       exponential backoff с jitter, retry на 5xx / throttling /
 *       connection reset. {@code maxAttempts = maxRetries + 1} - первая
 *       попытка не считается retry в новом API, поэтому семантика
 *       сохранена идентично legacy {@code numRetries}</li>
 *   <li>{@code endpointOverride} - dev указывает на MinIO localhost,
 *       prod на S3 провайдера</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({ObjectStorageProperties.class, IntegrityVerificationProperties.class})
@EnableScheduling
public class S3ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(S3ClientConfig.class);

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

        // AWS SDK v2 RetryStrategy (заменил deprecated RetryPolicy в 2.26+).
        // standardRetryStrategy() = exponential backoff с jitter + retry на
        // transient errors (5xx, throttling, connection reset). maxAttempts
        // включает первую попытку, поэтому +1 к maxRetries для сохранения
        // семантики (numRetries=3 → 1 initial + 3 retries = 4 attempts).
        RetryStrategy retryStrategy = AwsRetryStrategy.standardRetryStrategy().toBuilder()
                .maxAttempts(properties.maxRetries() + 1)
                .build();

        // apiCallTimeout split (ADR-024, Этап 25.b operational hardening).
        // Раньше apiCallTimeout == apiCallAttemptTimeout == readTimeout
        // → retries не успевали пройти если первый attempt пошёл до конца
        // timeout'а. Теперь:
        //   - apiCallAttemptTimeout = readTimeout (per single attempt)
        //   - apiCallTimeout = readTimeout × (maxRetries + 1) + 50% jitter
        //     (total wall-clock budget включая backoff между retries)
        Duration attemptTimeout = properties.readTimeout();
        Duration totalTimeout = attemptTimeout.multipliedBy(properties.maxRetries() + 1L)
                .plus(attemptTimeout.dividedBy(2));
        log.info("S3 timeouts: attempt={}s total={}s connect={}s",
                attemptTimeout.toSeconds(), totalTimeout.toSeconds(),
                properties.connectTimeout().toSeconds());

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryStrategy(retryStrategy)
                .apiCallTimeout(totalTimeout)
                .apiCallAttemptTimeout(attemptTimeout)
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
