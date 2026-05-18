package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;

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
 * <h2>Caching (25.b.6)</h2>
 *
 * <p>Единственный кеш - MinIO + {@code library_files} catalog (ADR-024).
 * Local file cache отсутствует - PDF читается напрямую из object
 * storage через {@code ObjectStorageService.getRange} в controller.
 *
 * <p>Flow {@link #locateFile}:
 * <ol>
 *   <li>Resolve {@code (bucket, storageKey)} из book+fileIndex</li>
 *   <li>Check {@code library_files.findActiveByBucketAndKey} →
 *       если найдено, return PdfLocation (cache hit)</li>
 *   <li>Cache miss: download upstream в temp file (буфер для hashing) →
 *       {@code putAndRegister} в MinIO + insert catalog → return
 *       PdfLocation</li>
 * </ol>
 *
 * <p>Temp file используется только как download buffer на стороне
 * {@link ObjectStorageService} (для SHA-256 + retry-safety, см. ADR-024).
 * Никакого долгоживущего local state.
 *
 * <p>{@link Order} = 100 - провайдер первого приоритета. Будущие
 * провайдеры (например ArchiveOrgDirect, UserUploadProvider) встают
 * рядом с разным order.
 */
@Component
@Order(100)
public class PdfLinksSourceProvider implements PdfSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(PdfLinksSourceProvider.class);
    private static final String CONTENT_TYPE = "application/pdf";

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
            boolean cover = hasCover && i == 0;
            files.add(parseFileEntry(i, raw, cover));
        }
        return new PdfMetadata(root, hasCover, totalSize, Collections.unmodifiableList(files));
    }

    @Override
    public PdfLocation locateFile(Book book, int fileIndex) {
        PdfMetadata meta = getMetadata(book);
        if (fileIndex < 0 || fileIndex >= meta.files().size()) {
            throw new PdfNotAvailableException(book.id(), fileIndex, meta.files().size());
        }
        PdfFileInfo file = meta.files().get(fileIndex);
        String bucket = storageProperties.buckets().importedBooks();
        String storageKey = storageKey(book, file);

        // Cache check - persistent через catalog + MinIO
        Optional<LibraryFile> cached = libraryFileRepository
                .findActiveByBucketAndKey(bucket, storageKey);
        if (cached.isPresent()) {
            LibraryFile row = cached.get();
            log.info("pdf cache hit: book={} file={} catalog={}",
                    book.id(), file.filename(), row.fileId());
            return new PdfLocation(bucket, storageKey, row.sizeBytes(), CONTENT_TYPE);
        }

        // Cache miss - download upstream + register в catalog/MinIO.
        // resolveUpstreamUrl бросит ShamelaApiException если root нужен и
        // отсутствует - проверка перенесена внутрь helper, чтобы absolute
        // URL в filename мог обойтись без root (archive.org-style books)
        URI url = resolveUpstreamUrl(meta, file, book);
        log.info("pdf download from upstream: book={} file={} from {}",
                book.id(), file.filename(), url);
        LibraryFile registered = downloadAndRegister(book, url, bucket, storageKey);
        return new PdfLocation(bucket, storageKey, registered.sizeBytes(), CONTENT_TYPE);
    }

    /**
     * Lazy streaming (25.d.5, ADR-023 amendment). Логика:
     * <ul>
     *   <li>Cache hit в {@code library_files} → MinIO Range (быстро,
     *       никакого upstream call)</li>
     *   <li>Cache miss + {@code range == null} (full download) → синхронно
     *       качаем upstream через {@link #downloadAndRegister} + стримим
     *       из MinIO. Это редкий path - обычно вызывается из admin smoke
     *       или integration tests</li>
     *   <li>Cache miss + Range != null → форвардим Range к archive.org через
     *       {@link PdfFetcher#openStream}. Bytes идут upstream → backend →
     *       клиент напрямую. MinIO cache не пополняется (избегаем tee
     *       complexity, MinIO fill отдельный flow через {@link #locateFile})</li>
     * </ul>
     *
     * <p>Если archive.org проигнорировал Range header и вернул 200 -
     * {@link PdfStreamingResult#isPartial()} = false, controller отдаст
     * full content вместо 206. Это нормально для не-Range-aware mirror'ов
     * (хотя archive.org обычно поддерживает Range нативно).
     */
    @Override
    public PdfStreamingResult openStream(Book book, int fileIndex, RangeSpec range) {
        PdfMetadata meta = getMetadata(book);
        if (fileIndex < 0 || fileIndex >= meta.files().size()) {
            throw new PdfNotAvailableException(book.id(), fileIndex, meta.files().size());
        }
        PdfFileInfo file = meta.files().get(fileIndex);
        String bucket = storageProperties.buckets().importedBooks();
        String storageKey = storageKey(book, file);

        Optional<LibraryFile> cached = libraryFileRepository
                .findActiveByBucketAndKey(bucket, storageKey);
        if (cached.isPresent()) {
            return streamFromMinIO(bucket, storageKey, cached.get().sizeBytes(), range);
        }

        // Cache miss - решаем по наличию range: lazy upstream vs sync cache fill
        if (range == null) {
            log.info("pdf cache miss + full request: sync cache fill для book={} file={}",
                    book.id(), file.filename());
            PdfLocation loc = locateFile(book, fileIndex);
            return streamFromMinIO(loc.bucket(), loc.storageKey(), loc.sizeBytes(), null);
        }

        URI url = resolveUpstreamUrl(meta, file, book);
        log.info("pdf cache miss + range request: lazy forward к {} range=bytes={}-{}",
                url, range.startInclusive(),
                range.endInclusive() != null ? range.endInclusive() : "");
        return pdfFetcher.openStream(url, range);
    }

    /**
     * Storage key для объекта в bucket'е. Format {@code {book_id}/{name}}
     * где {@code name} - либо filename как есть (shamela CDN style:
     * {@code root + filename}), либо basename абсолютного URL для
     * archive.org-style записей. Цель - чтобы MinIO key не содержал
     * URL-схемы и slashes из абсолютного URL.
     */
    private static String storageKey(Book book, PdfFileInfo file) {
        return book.id() + "/" + storageName(file.filename());
    }

    private static String storageName(String filename) {
        if (isAbsoluteUrl(filename)) {
            // Basename из URL: last path segment до '?'/'#' (URI.getPath
            // уже отбрасывает query/fragment, дальше split по '/')
            String path = URI.create(filename).getPath();
            int last = path.lastIndexOf('/');
            String basename = (last >= 0 && last < path.length() - 1)
                    ? path.substring(last + 1)
                    : path;
            return basename.isBlank() ? filename : basename;
        }
        return filename;
    }

    /**
     * Resolves upstream URL для скачивания файла. Поддерживает два формата
     * shamela {@code pdf_links}:
     * <ul>
     *   <li><b>shamela CDN style</b> - {@code root + filename}: например
     *       {@code root="https://archive.org/download/foo/"} +
     *       {@code filename="01_113015.pdf"} → один URL</li>
     *   <li><b>direct absolute URL style</b> - {@code filename} уже
     *       полный URL (типичный case когда shamela ссылается на
     *       archive.org напрямую без CDN-mirror'а). {@code root} пустой
     *       или отсутствует</li>
     * </ul>
     * Различаются по prefix {@code http://}/{@code https://} в filename.
     * Если filename относительный, а root отсутствует - бросаем
     * {@link ShamelaApiException} с диагностикой.
     */
    private static URI resolveUpstreamUrl(PdfMetadata meta, PdfFileInfo file, Book book) {
        String filename = file.filename();
        if (isAbsoluteUrl(filename)) {
            return URI.create(filename);
        }
        if (meta.root() == null || meta.root().isBlank()) {
            throw new ShamelaApiException(
                    "pdf_links.root отсутствует для книги " + book.id()
                            + " (filename '" + filename + "' относительный)");
        }
        return URI.create(meta.root() + filename);
    }

    private static boolean isAbsoluteUrl(String filename) {
        return filename != null
                && (filename.startsWith("http://") || filename.startsWith("https://"));
    }

    private PdfStreamingResult streamFromMinIO(
            String bucket, String storageKey, long totalSize, RangeSpec range) {
        if (range == null) {
            var stream = objectStorageService.get(bucket, storageKey);
            return new PdfStreamingResult(stream, totalSize, 0L, totalSize - 1, totalSize, false);
        }
        if (range.startInclusive() >= totalSize) {
            throw new RangeNotSatisfiableException(range.startInclusive(), totalSize);
        }
        long end = range.resolvedEndInclusive(totalSize);
        long length = end - range.startInclusive() + 1;
        var stream = objectStorageService.getRange(bucket, storageKey, range.startInclusive(), end);
        return new PdfStreamingResult(stream, length, range.startInclusive(), end, totalSize, true);
    }

    /**
     * Скачивает PDF в temp file (для SHA-256 hashing + retry-safety
     * в {@link ObjectStorageService}), upload'ит в MinIO + регистрирует
     * в catalog. Temp file удаляется в finally.
     */
    private LibraryFile downloadAndRegister(Book book, URI url, String bucket, String storageKey) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-fetch-", ".pdf");
            pdfFetcher.fetch(url, tempFile);

            Integer shamelaMajor = readShamelaMajorRelease(book);
            LibraryFileSourceType sourceType = shamelaMajor != null
                    ? LibraryFileSourceType.SHAMELA
                    : LibraryFileSourceType.ARCHIVE_ORG;

            try (InputStream stream = Files.newInputStream(tempFile)) {
                return objectStorageService.putAndRegister(
                        bucket, storageKey, stream,
                        CONTENT_TYPE,
                        book.id(), url.toString(),
                        sourceType, shamelaMajor, null);
            }
        } catch (IOException e) {
            throw new ShamelaApiException(
                    "не удалось зарегистрировать PDF в storage: " + bucket + "/" + storageKey, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
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
}
