package ru.basnukaev.argumentmap.library.shamela.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.library.shamela.api.dto.BookMetadata;
import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;

/**
 * HTTP-клиент к официальному desktop-API shamela (ADR-020).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code dev.shamela.ws/api/v1/patches/master?version=N&api_key=...}
 *       - метадата каталога (полный snapshot или дельта)</li>
 *   <li>{@code dev.shamela.ws/api/v1/patches/book-updates/{id}?...}
 *       - метадата книги (URL полного snapshot)</li>
 *   <li>{@code ready.shamela.ws/books-store/{id}-{major}.zip}
 *       - полный snapshot книги (без api_key)</li>
 *   <li>{@code ready.shamela.ws/pdf{relativePath}}
 *       - PDF исходного издания (без api_key, lazy)</li>
 * </ul>
 *
 * <p>Бинарные endpoints на {@code ready.shamela.ws} раздаются как
 * статика с CDN, api_key не требуется (см. ADR-020). Все ошибки HTTP
 * (не-2xx) пробрасываются как {@link ShamelaApiException}.
 */
@Component
public class ShamelaApiClient {

    private static final Logger log = LoggerFactory.getLogger(ShamelaApiClient.class);

    private final HttpClient httpClient;
    private final ShamelaApiProperties props;
    private final ObjectMapper objectMapper;

    public ShamelaApiClient(HttpClient shamelaHttpClient,
                            ShamelaApiProperties props,
                            ObjectMapper objectMapper) {
        this.httpClient = shamelaHttpClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public MasterMetadata fetchMasterMetadata(int currentVersion) {
        URI uri = URI.create(String.format(
                "https://%s/api/v1/patches/master?api_key=%s&version=%d",
                props.metadataHost(), props.apiKey(), currentVersion));
        log.info("shamela master metadata: version={}", currentVersion);
        return getJson(uri, MasterMetadata.class);
    }

    public BookMetadata fetchBookMetadata(long bookId, int majorRelease, int minorRelease) {
        URI uri = URI.create(String.format(
                "https://%s/api/v1/patches/book-updates/%d?api_key=%s&major_release=%d&minor_release=%d",
                props.metadataHost(), bookId, props.apiKey(), majorRelease, minorRelease));
        log.info("shamela book metadata: bookId={} major={} minor={}", bookId, majorRelease, minorRelease);
        return getJson(uri, BookMetadata.class);
    }

    /**
     * Скачивает архив по предоставленному URL потоково в файл, чтобы не
     * держать ~MB-payload в памяти. URL обычно приходит из
     * {@link MasterMetadata#patchUrl()} или {@link BookMetadata#majorReleaseUrl()}
     * и уже содержит api_key (либо не требует его для ready-host).
     *
     * @param url       полный URL архива (включая api_key если нужно)
     * @param targetDir каталог куда положить файл (создаётся при отсутствии)
     * @return путь к скачанному zip-файлу. Имя берётся из последнего
     *         сегмента URL, например {@code master-0-1261.zip}
     */
    public Path downloadArchive(URI url, Path targetDir) {
        ensureDirectory(targetDir);
        String fileName = lastPathSegment(url);
        Path target = targetDir.resolve(fileName);
        log.info("shamela download archive: {} -> {}", url.getHost() + url.getPath(), target);
        return downloadTo(url, target);
    }

    /**
     * Lazy-скачивание PDF исходного издания. Путь {@code relativePath}
     * берётся из {@code book.pdf_links.files[N]} (например
     * {@code "/1/41557.pdf"}). API key не требуется.
     */
    public Path downloadPdf(String relativePath, Path targetDir) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("PDF relative path не должен быть пустым");
        }
        String normalized = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        URI url = URI.create(String.format("https://%s/pdf%s", props.filesHost(), normalized));
        ensureDirectory(targetDir);
        Path target = targetDir.resolve(lastPathSegment(url));
        log.info("shamela download pdf: {} -> {}", relativePath, target);
        return downloadTo(url, target);
    }

    private <T> T getJson(URI uri, Class<T> type) {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new ShamelaApiException(
                        "shamela API вернула HTTP " + resp.statusCode() + " на " + maskApiKey(uri));
            }
            return objectMapper.readValue(resp.body(), type);
        } catch (IOException e) {
            throw new ShamelaApiException("ошибка вызова shamela API: " + maskApiKey(uri), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван вызов shamela API: " + maskApiKey(uri), e);
        }
    }

    private Path downloadTo(URI url, Path target) {
        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .GET()
                .build();
        try {
            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() / 100 != 2) {
                Files.deleteIfExists(target);
                throw new ShamelaApiException(
                        "shamela download вернул HTTP " + resp.statusCode() + " на " + maskApiKey(url));
            }
            return resp.body();
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // частично скачанный файл оставляем диагностически
            }
            throw new ShamelaApiException("ошибка скачивания shamela archive: " + maskApiKey(url), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван download shamela: " + maskApiKey(url), e);
        }
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ShamelaApiException("не удалось создать каталог " + dir, e);
        }
    }

    private static String lastPathSegment(URI uri) {
        String path = uri.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank()) {
            throw new ShamelaApiException("URL без файлового сегмента: " + uri);
        }
        return name;
    }

    private static String maskApiKey(URI uri) {
        String s = uri.toString();
        // не логируем api_key и proxy-credentials
        return s.replaceAll("api_key=[^&]+", "api_key=***");
    }
}
