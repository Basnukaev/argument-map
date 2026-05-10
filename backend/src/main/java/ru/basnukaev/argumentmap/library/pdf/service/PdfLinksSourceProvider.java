package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfFileInfo;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;

/**
 * Универсальный PDF-провайдер для книг с {@code pdf_links} в
 * {@code metadata}. Покрывает книги из shamela (использует
 * archive.org как CDN - например root
 * {@code "https://archive.org/download/ibnkatheer_jawzee/"}) и
 * прямые archive.org-источники - оба укладываются в один формат
 * {@code {root, files: [...]}}.
 *
 * <p>Формат {@code metadata.pdf_links}:
 * <pre>{@code
 * {
 *   "root": "https://archive.org/download/.../",
 *   "size": 135102734,
 *   "cover": 1,
 *   "files": ["01_113015.pdf", "02_113015p.pdf|المقدمة", ...]
 * }
 * }</pre>
 *
 * <p>Каждая запись {@code files[i]} - либо чистый filename, либо
 * {@code "filename|label"} (label - арабская/латинская строка
 * с описанием тома или раздела).
 *
 * <p>Download делает прямой HTTP GET на {@code root + filename} в
 * указанный {@code targetDir}. Не использует
 * {@code ShamelaApiClient.downloadPdf} - тот строит другой URL
 * через {@code ready.shamela.ws/pdf...}, что для archive.org-host
 * не работает.
 *
 * <p>{@link Order} = 100 - провайдер первого приоритета. Будущие
 * MinioCacheProvider должны иметь order=10 чтобы перехватывать
 * cached requests раньше.
 */
@Component
@Order(100)
public class PdfLinksSourceProvider implements PdfSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(PdfLinksSourceProvider.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PdfLinksSourceProvider(ObjectMapper objectMapper,
                                  @Qualifier("shamelaHttpClient") HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(Book book) {
        JsonNode pdfLinks = readPdfLinks(book);
        if (pdfLinks == null || pdfLinks.isNull()) {
            return false;
        }
        JsonNode files = pdfLinks.get("files");
        return files != null && files.isArray() && !files.isEmpty();
    }

    @Override
    public PdfMetadata getMetadata(Book book) {
        JsonNode pdfLinks = readPdfLinks(book);
        if (pdfLinks == null) {
            throw new PdfNotAvailableException(book.id());
        }
        String root = textOrNull(pdfLinks.get("root"));
        boolean hasCover = pdfLinks.has("cover") && pdfLinks.get("cover").asInt(0) > 0;
        Long totalSize = pdfLinks.has("size") ? pdfLinks.get("size").asLong() : null;

        JsonNode filesNode = pdfLinks.get("files");
        if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
            throw new PdfNotAvailableException(book.id());
        }
        List<PdfFileInfo> files = new ArrayList<>(filesNode.size());
        for (int i = 0; i < filesNode.size(); i++) {
            String raw = textOrNull(filesNode.get(i));
            if (raw == null) {
                continue;
            }
            files.add(parseFileEntry(i, raw));
        }
        return new PdfMetadata(root, hasCover, totalSize, Collections.unmodifiableList(files));
    }

    @Override
    public Path downloadFile(Book book, int fileIndex, Path targetDir) {
        PdfMetadata meta = getMetadata(book);
        if (fileIndex < 0 || fileIndex >= meta.files().size()) {
            throw new PdfNotAvailableException(book.id(), fileIndex, meta.files().size());
        }
        if (meta.root() == null || meta.root().isBlank()) {
            throw new ShamelaApiException(
                    "pdf_links.root отсутствует для книги " + book.id());
        }
        PdfFileInfo file = meta.files().get(fileIndex);
        URI url = URI.create(meta.root() + file.filename());
        ensureDirectory(targetDir);
        Path target = targetDir.resolve(file.filename());
        if (Files.exists(target)) {
            // Простой in-process cache - если файл уже скачан в этот
            // tempDir, не качаем повторно. После 25.b заменим на
            // MinIO с TTL
            log.info("pdf cache hit: book={} file={} -> {}",
                    book.id(), file.filename(), target);
            return target;
        }
        log.info("pdf download: book={} file={} from {}",
                book.id(), file.filename(), url);
        return downloadTo(url, target);
    }

    private JsonNode readPdfLinks(Book book) {
        String metadata = book.metadata();
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            return root.get("pdf_links");
        } catch (JsonProcessingException e) {
            log.warn("книга {} содержит невалидный JSON в metadata: {}",
                    book.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Разбирает запись из {@code files}: либо чистое
     * {@code "01_113015.pdf"}, либо {@code "01_113015.pdf|المقدمة"} -
     * filename с label через pipe-separator. Label по умолчанию -
     * filename без расширения.
     */
    private static PdfFileInfo parseFileEntry(int index, String raw) {
        String filename;
        String label;
        int pipe = raw.indexOf('|');
        if (pipe >= 0) {
            filename = raw.substring(0, pipe).trim();
            label = raw.substring(pipe + 1).trim();
        } else {
            filename = raw.trim();
            label = stripExtension(filename);
        }
        return new PdfFileInfo(index, filename, label, null, null);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private Path downloadTo(URI url, Path target) {
        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        try {
            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() / 100 != 2) {
                Files.deleteIfExists(target);
                throw new ShamelaApiException(
                        "PDF download вернул HTTP " + resp.statusCode() + " на " + url);
            }
            return resp.body();
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // частично скачанный файл оставляем для диагностики
            }
            throw new ShamelaApiException(
                    "ошибка PDF download: " + url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван PDF download: " + url, e);
        }
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ShamelaApiException("не удалось создать каталог " + dir, e);
        }
    }
}
