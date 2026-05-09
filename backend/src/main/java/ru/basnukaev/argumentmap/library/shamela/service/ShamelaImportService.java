package ru.basnukaev.argumentmap.library.shamela.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiClient;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiProperties;
import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaArchiveExtractor;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaBookReader;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaMasterReader;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookContent;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaSyncStateDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;

/**
 * Оркестрация импорта данных из desktop-API shamela в staging-таблицы
 * {@code lib_shamela_*} (ADR-020).
 *
 * <p>Два публичных метода:
 * <ul>
 *   <li>{@link #syncMaster()} - синхронизация каталога (категории/авторы/
 *       книги). Если {@code master_version} в staging совпадает с тем что
 *       вернула shamela - архив не качается, выполнение мгновенное</li>
 *   <li>{@link #importBook(long)} - импорт страниц и заголовков конкретной
 *       книги. Книга уже должна быть в {@code lib_shamela_book} (через
 *       предварительный {@code syncMaster}), оттуда берём {@code major_release}
 *       для детерминированного URL</li>
 * </ul>
 *
 * <p>Идемпотентность: оба метода идемпотентны через {@code ON CONFLICT
 * DO UPDATE} в DAO. Прерванный sync (например network error в середине)
 * безопасно повторяется - {@code master_version} обновляется в самом
 * конце syncMaster, поэтому при retry заново качаем тот же snapshot и
 * upsert переписывает все строки.
 *
 * <p>Транзакция на pipeline сознательно не оборачивается: bulk upsert
 * ~8500 books + ~25k авторов в одной транзакции долго держит лок и
 * съедает WAL. ADR-020 закрепляет идемпотентность как механизм
 * консистентности вместо атомарности.
 *
 * <p>Cleanup: каждый вызов создаёт изолированный workdir через
 * {@code Files.createTempDirectory} в {@code shamela.download-dir}.
 * После завершения (или exception) каталог рекурсивно удаляется. Это
 * безопасно для concurrent вызовов (например параллельный import двух
 * книг).
 */
@Service
public class ShamelaImportService {

    private static final Logger log = LoggerFactory.getLogger(ShamelaImportService.class);

    private static final String MASTER_CATEGORY_SQLITE = "category.sqlite";
    private static final String MASTER_AUTHOR_SQLITE = "author.sqlite";
    private static final String MASTER_BOOK_SQLITE = "book.sqlite";

    private final ShamelaApiClient apiClient;
    private final ShamelaArchiveExtractor extractor;
    private final ShamelaMasterReader masterReader;
    private final ShamelaBookReader bookReader;
    private final ShamelaCategoryDao categoryDao;
    private final ShamelaAuthorDao authorDao;
    private final ShamelaBookDao bookDao;
    private final ShamelaPageDao pageDao;
    private final ShamelaTitleDao titleDao;
    private final ShamelaSyncStateDao syncStateDao;
    private final ShamelaApiProperties props;

    public ShamelaImportService(ShamelaApiClient apiClient,
                                ShamelaArchiveExtractor extractor,
                                ShamelaMasterReader masterReader,
                                ShamelaBookReader bookReader,
                                ShamelaCategoryDao categoryDao,
                                ShamelaAuthorDao authorDao,
                                ShamelaBookDao bookDao,
                                ShamelaPageDao pageDao,
                                ShamelaTitleDao titleDao,
                                ShamelaSyncStateDao syncStateDao,
                                ShamelaApiProperties props) {
        this.apiClient = apiClient;
        this.extractor = extractor;
        this.masterReader = masterReader;
        this.bookReader = bookReader;
        this.categoryDao = categoryDao;
        this.authorDao = authorDao;
        this.bookDao = bookDao;
        this.pageDao = pageDao;
        this.titleDao = titleDao;
        this.syncStateDao = syncStateDao;
        this.props = props;
    }

    public MasterSyncResult syncMaster() {
        int currentVersion = syncStateDao.getMasterVersion();
        MasterMetadata meta = apiClient.fetchMasterMetadata(currentVersion);
        if (meta.version() == currentVersion) {
            log.info("shamela master uptodate: version={}", currentVersion);
            return MasterSyncResult.unchanged(currentVersion);
        }
        if (meta.patchUrl() == null || meta.patchUrl().isBlank()) {
            throw new ShamelaImportException(
                    "shamela master metadata вернула version=" + meta.version()
                            + ", но patch_url пустой");
        }
        Path workDir = createWorkDir("master");
        try {
            Path archive = apiClient.downloadArchive(URI.create(meta.patchUrl()), workDir);
            Path extracted = extractor.extract(archive, workDir.resolve("extracted"));

            Path categoryFile = requireSqlite(extracted, MASTER_CATEGORY_SQLITE);
            Path authorFile = requireSqlite(extracted, MASTER_AUTHOR_SQLITE);
            Path bookFile = requireSqlite(extracted, MASTER_BOOK_SQLITE);

            int categories = categoryDao.upsertAll(masterReader.readCategories(categoryFile));
            int authors = authorDao.upsertAll(masterReader.readAuthors(authorFile));
            int books = bookDao.upsertAll(masterReader.readBooks(bookFile));

            syncStateDao.updateMasterVersion(meta.version());
            log.info("shamela master synced: {}->{}, categories={} authors={} books={}",
                    currentVersion, meta.version(), categories, authors, books);
            return MasterSyncResult.synced(currentVersion, meta.version(), categories, authors, books);
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    public BookImportResult importBook(long bookId) {
        ShamelaBookRow book = bookDao.findById(bookId).orElseThrow(() ->
                new ShamelaImportException(
                        "книга id=" + bookId + " не найдена в lib_shamela_book - "
                                + "сначала вызови syncMaster()"));
        int majorRelease = book.majorRelease();
        URI url = URI.create(String.format("https://%s/books-store/%d-%d.zip",
                props.filesHost(), bookId, majorRelease));
        Path workDir = createWorkDir("book-" + bookId);
        try {
            Path archive = apiClient.downloadArchive(url, workDir);
            Path extracted = extractor.extract(archive, workDir.resolve("extracted"));
            Path bookSqlite = requireSqlite(extracted, bookId + ".sqlite");

            ShamelaBookContent content = bookReader.read(bookSqlite, bookId);
            int pages = pageDao.upsertAll(content.pages());
            int titles = titleDao.upsertAll(content.titles());

            log.info("shamela book imported: bookId={} major={} pages={} titles={}",
                    bookId, majorRelease, pages, titles);
            return new BookImportResult(bookId, majorRelease, pages, titles);
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    private Path createWorkDir(String prefix) {
        try {
            Path base = Path.of(props.downloadDir());
            Files.createDirectories(base);
            return Files.createTempDirectory(base, prefix + "-");
        } catch (IOException e) {
            throw new ShamelaImportException(
                    "не удалось создать рабочий каталог в " + props.downloadDir(), e);
        }
    }

    private static Path requireSqlite(Path extractedDir, String fileName) {
        Path file = extractedDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new ShamelaImportException(
                    "ожидаемый SQLite-файл отсутствует в архиве: " + fileName
                            + " (распакован в " + extractedDir + ")");
        }
        return file;
    }

    private static void cleanupWorkDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("shamela cleanup: не удалось удалить {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("shamela cleanup: walk упал на {}: {}", dir, e.getMessage());
        }
    }
}
