package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfFileInfo;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

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
 * <h2>Caching (25.b.5)</h2>
 *
 * <p>Двухуровневый кеш через {@link ObjectStorageService} +
 * {@link LibraryFileRepository} catalog (ADR-024):
 *
 * <ul>
 *   <li><b>MinIO (persistent)</b> - bucket
 *       {@code library-imported-books}, выживает restart backend и
 *       multi-instance deploys. Catalog row в {@code library_files}
 *       resolve'ит по {@code (bucket, storage_key)}</li>
 *   <li><b>local temp file (per-process speed)</b> - копия в
 *       {@code targetDir} для быстрой повторной отдачи через
 *       {@code FileSystemResource} с HTTP Range. Теряется при restart,
 *       восстанавливается из MinIO при первом обращении после restart'а
 *       (без upstream re-download)</li>
 * </ul>
 *
 * <p>На MVP пока сохраняется local file copy - для интеграции с
 * существующим {@link ru.basnukaev.argumentmap.library.pdf.web.PdfController}
 * который использует {@code FileSystemResource}. lazy streaming из
 * MinIO напрямую через Range - см. 25.b.6.
 *
 * <p>{@link Order} = 100 - провайдер первого приоритета. Будущие
 * провайдеры (например ArchiveOrgDirect) встают рядом с разным order.
 */
@Component
@Order(100)
public class PdfLinksSourceProvider implements PdfSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(PdfLinksSourceProvider.class);

    private final ObjectMapper objectMapper;
    private final PdfFetcher pdfFetcher;
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageProperties storageProperties;
    private final LibraryFileRepository libraryFileRepository;

    public PdfLinksSourceProvider(ObjectMapper objectMapper,
                                  PdfFetcher pdfFetcher,
                                  ObjectStorageService objectStorageService,
                                  ObjectStorageProperties storageProperties,
                                  LibraryFileRepository libraryFileRepository) {
        this.objectMapper = objectMapper;
        this.pdfFetcher = pdfFetcher;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.libraryFileRepository = libraryFileRepository;
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
            // По convention shamela/archive.org: если hasCover - первый
            // файл это обложка. Маркируем чтобы frontend пропускал её
            // из основного potoka чтения
            boolean cover = hasCover && i == 0;
            files.add(parseFileEntry(i, raw, cover));
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
        ensureDirectory(targetDir);
        Path target = targetDir.resolve(file.filename());

        // Уровень 1: local temp file - speed cache в пределах одной JVM
        if (Files.exists(target)) {
            log.info("pdf local cache hit: book={} file={} -> {}",
                    book.id(), file.filename(), target);
            return target;
        }

        String bucket = storageProperties.buckets().importedBooks();
        String storageKey = storageKey(book, file);

        // Уровень 2: MinIO catalog - persistent cache (выживает restart)
        Optional<LibraryFile> cached = libraryFileRepository
                .findActiveByBucketAndKey(bucket, storageKey);
        if (cached.isPresent()) {
            log.info("pdf minio cache hit: book={} file={} catalog={}",
                    book.id(), file.filename(), cached.get().fileId());
            copyFromMinioToLocal(bucket, storageKey, target);
            return target;
        }

        // Cache miss - download upstream + register в catalog + сохранить локально
        URI url = URI.create(meta.root() + file.filename());
        log.info("pdf download from upstream: book={} file={} from {}",
                book.id(), file.filename(), url);
        pdfFetcher.fetch(url, target);

        registerInCatalog(book, target, bucket, storageKey, url);
        return target;
    }

    /**
     * Storage key для объекта в bucket'е. Format
     * {@code {book_id}/{filename}} - читаем в {@code mc ls bucket/<bookId>/}
     * увидим все pdf-файлы книги.
     */
    private static String storageKey(Book book, PdfFileInfo file) {
        return book.id() + "/" + file.filename();
    }

    private void copyFromMinioToLocal(String bucket, String storageKey, Path target) {
        try (ResponseInputStream<GetObjectResponse> stream =
                     objectStorageService.get(bucket, storageKey)) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // частично скопированный файл удаляем чтобы next request увидел miss
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best-effort
            }
            throw new ShamelaApiException(
                    "ошибка копирования из MinIO в local: " + bucket + "/" + storageKey, e);
        }
    }

    private void registerInCatalog(Book book, Path localFile,
                                    String bucket, String storageKey, URI sourceUrl) {
        Integer shamelaMajor = readShamelaMajorRelease(book);
        LibraryFileSourceType sourceType = shamelaMajor != null
                ? LibraryFileSourceType.SHAMELA
                : LibraryFileSourceType.ARCHIVE_ORG;
        try (InputStream stream = Files.newInputStream(localFile)) {
            objectStorageService.putAndRegister(
                    bucket, storageKey, stream, Files.size(localFile),
                    "application/pdf",
                    book.id(), sourceUrl.toString(),
                    sourceType, shamelaMajor, null);
        } catch (IOException e) {
            throw new ShamelaApiException(
                    "не удалось загрузить PDF в MinIO: " + bucket + "/" + storageKey, e);
        }
    }

    private Integer readShamelaMajorRelease(Book book) {
        JsonNode root = readBookMetadata(book);
        if (root == null) {
            return null;
        }
        JsonNode field = root.get("shamela_major_release");
        return (field != null && field.canConvertToInt()) ? field.asInt() : null;
    }

    private JsonNode readPdfLinks(Book book) {
        JsonNode root = readBookMetadata(book);
        return root != null ? root.get("pdf_links") : null;
    }

    private JsonNode readBookMetadata(Book book) {
        String metadata = book.metadata();
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(metadata);
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
    private static PdfFileInfo parseFileEntry(int index, String raw, boolean isCover) {
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
        return new PdfFileInfo(index, filename, label, isCover, null, null);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ShamelaApiException("не удалось создать каталог " + dir, e);
        }
    }
}
