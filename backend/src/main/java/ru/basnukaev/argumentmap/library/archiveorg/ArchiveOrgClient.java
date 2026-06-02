package ru.basnukaev.argumentmap.library.archiveorg;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * HTTP-клиент к публичному metadata-API archive.org (ADR-056).
 *
 * <p>Один вызов {@code GET {baseUrl}/metadata/{identifier}} → JSON
 * {@code { metadata, files[] }} ({@link ArchiveOrgMetadata}). Auth не
 * требуется. Переиспользует {@code shamelaHttpClient} (прямое
 * соединение + follow-redirects) - тот же транспорт что и shamela ETL.
 *
 * <p>Защищён Resilience4j Circuit Breaker {@code archiveOrg}: при >50%
 * ошибок в окне из 10 запросов circuit открывается на 30с, далее
 * fail-fast через {@link #fetchMetadataFallback}. Конфиг в
 * {@code application.yml}.
 *
 * <p>{@link #extractIdentifier(String)} - чистая (без сети) логика
 * разбора пользовательского ввода: полный URL
 * {@code archive.org/details/{id}/...} либо bare identifier.
 */
@Component
@EnableConfigurationProperties(ArchiveOrgProperties.class)
public class ArchiveOrgClient {

    private static final Logger log = LoggerFactory.getLogger(ArchiveOrgClient.class);
    private static final String CB_NAME = "archiveOrg";
    private static final String DETAILS_SEGMENT = "/details/";

    private final HttpClient httpClient;
    private final ArchiveOrgProperties props;
    private final ObjectMapper objectMapper;

    public ArchiveOrgClient(@Qualifier("shamelaHttpClient") HttpClient httpClient,
                            ArchiveOrgProperties props,
                            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Извлекает archive.org identifier из пользовательского ввода.
     * Поддерживает:
     * <ul>
     *   <li>полный URL {@code https://archive.org/details/fmhji/fmhji1/page/70}
     *       → {@code fmhji} (первый сегмент после {@code /details/});</li>
     *   <li>{@code archive.org/details/fmhji} без схемы → {@code fmhji};</li>
     *   <li>bare identifier {@code fmhji} → {@code fmhji}.</li>
     * </ul>
     * Bare identifier валидируется как «один path-сегмент без пробелов»;
     * любой другой URL (не archive.org / без {@code /details/}) →
     * {@link InvalidArchiveOrgUrlException}.
     *
     * @throws InvalidArchiveOrgUrlException если ввод пустой либо не
     *         распознаётся как archive.org-источник
     */
    public String extractIdentifier(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidArchiveOrgUrlException("URL/identifier не должен быть пустым");
        }
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("/")) {
            int detailsIdx = lower.indexOf(DETAILS_SEGMENT);
            if (detailsIdx < 0) {
                throw new InvalidArchiveOrgUrlException(
                        "не archive.org-URL (ожидается '/details/{identifier}'): " + trimmed);
            }
            String afterDetails = trimmed.substring(detailsIdx + DETAILS_SEGMENT.length());
            String id = firstPathSegment(afterDetails);
            if (id.isBlank()) {
                throw new InvalidArchiveOrgUrlException(
                        "пустой identifier после '/details/': " + trimmed);
            }
            return id;
        }

        // bare identifier - один сегмент, без пробелов/слешей
        if (trimmed.contains(" ")) {
            throw new InvalidArchiveOrgUrlException(
                    "identifier не должен содержать пробелы: " + trimmed);
        }
        return trimmed;
    }

    /**
     * Запрашивает metadata item'а. archive.org на несуществующий
     * identifier отдаёт {@code 200} с пустым {@code {}} (нет ключей
     * {@code metadata}/{@code files}) - это трактуется как
     * {@link ArchiveOrgItemNotFoundException} (→ 404). Не-2xx /
     * IO / прерывание → {@link ArchiveOrgException} (→ 502).
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fetchMetadataFallback")
    public ArchiveOrgMetadata fetchMetadata(String identifier) {
        URI uri = URI.create(props.baseUrl() + "/metadata/" + identifier);
        log.info("archive.org metadata: identifier={}", identifier);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 404) {
                throw new ArchiveOrgItemNotFoundException(identifier);
            }
            if (resp.statusCode() / 100 != 2) {
                throw new ArchiveOrgException(
                        "archive.org metadata вернула HTTP " + resp.statusCode() + " на " + uri);
            }
            ArchiveOrgMetadata parsed = objectMapper.readValue(resp.body(), ArchiveOrgMetadata.class);
            // archive.org на удалённый/несуществующий item отдаёт 200 + пустой {}
            if (parsed == null || parsed.metadata() == null || parsed.metadata().isEmpty()) {
                throw new ArchiveOrgItemNotFoundException(identifier);
            }
            return parsed;
        } catch (IOException e) {
            throw new ArchiveOrgException(
                    "ошибка вызова archive.org metadata: " + uri + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveOrgException("прерван вызов archive.org metadata: " + uri, e);
        }
    }

    /** Базовый URL (без trailing slash) - для построения download/thumbnail ссылок в маппере. */
    public String baseUrl() {
        return props.baseUrl();
    }

    /**
     * Fallback при открытом circuit breaker. Сигнатура mirror главного
     * метода + Throwable последним аргументом (контракт resilience4j).
     * {@link ArchiveOrgItemNotFoundException} (404, не сбой канала) НЕ
     * должен открывать circuit и НЕ перехватывается тут как сбой - он
     * исключён из record-exceptions в application.yml.
     */
    @SuppressWarnings("unused") // вызывается через AOP при CB open
    private ArchiveOrgMetadata fetchMetadataFallback(String identifier, Throwable cause) {
        if (cause instanceof ArchiveOrgItemNotFoundException notFound) {
            throw notFound;
        }
        log.warn("Circuit breaker archiveOrg открыт - fail fast для identifier={}: {}",
                identifier, cause.getMessage());
        throw new ArchiveOrgException(
                "archive.org временно недоступен (circuit breaker archiveOrg). "
                        + "Повтори через ~30 секунд. Причина: " + cause.getMessage(), cause);
    }

    private static String firstPathSegment(String path) {
        String p = path;
        // отрезаем query/fragment
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        int h = p.indexOf('#');
        if (h >= 0) {
            p = p.substring(0, h);
        }
        // ведущие слеши не ожидаются (substring после '/details/'), но защищаемся
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(0, slash) : p;
    }
}
