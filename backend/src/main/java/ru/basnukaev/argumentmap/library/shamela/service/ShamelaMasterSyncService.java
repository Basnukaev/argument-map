package ru.basnukaev.argumentmap.library.shamela.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiClient;
import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaArchiveExtractor;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaMasterReader;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaSyncStateDao;

/**
 * Синхронизация каталога shamela (категории/авторы/книги) из desktop-API
 * в staging-таблицы {@code lib_shamela_*} (ADR-020). Один публичный
 * метод - {@link #syncMaster()}.
 *
 * <p>Если {@code master_version} в staging совпадает с тем что вернула
 * shamela - архив не качается, выполнение мгновенное.
 *
 * <p>Идемпотентность: через {@code ON CONFLICT DO UPDATE} в DAO.
 * Прерванный sync безопасно повторяется - {@code master_version}
 * обновляется в самом конце, поэтому при retry заново качаем тот же
 * snapshot и upsert переписывает все строки.
 *
 * <p>Транзакция на pipeline сознательно не оборачивается: bulk upsert
 * ~8500 books + ~25k авторов в одной транзакции долго держит лок и
 * съедает WAL. ADR-020 закрепляет идемпотентность как механизм
 * консистентности вместо атомарности.
 */
@Service
public class ShamelaMasterSyncService {

    private static final Logger log = LoggerFactory.getLogger(ShamelaMasterSyncService.class);

    private static final String CATEGORY_SQLITE = "category.sqlite";
    private static final String AUTHOR_SQLITE = "author.sqlite";
    private static final String BOOK_SQLITE = "book.sqlite";

    private final ShamelaApiClient apiClient;
    private final ShamelaArchiveExtractor extractor;
    private final ShamelaMasterReader masterReader;
    private final ShamelaCategoryDao categoryDao;
    private final ShamelaAuthorDao authorDao;
    private final ShamelaBookDao bookDao;
    private final ShamelaSyncStateDao syncStateDao;
    private final ShamelaWorkDirManager workDirManager;

    public ShamelaMasterSyncService(ShamelaApiClient apiClient,
                                    ShamelaArchiveExtractor extractor,
                                    ShamelaMasterReader masterReader,
                                    ShamelaCategoryDao categoryDao,
                                    ShamelaAuthorDao authorDao,
                                    ShamelaBookDao bookDao,
                                    ShamelaSyncStateDao syncStateDao,
                                    ShamelaWorkDirManager workDirManager) {
        this.apiClient = apiClient;
        this.extractor = extractor;
        this.masterReader = masterReader;
        this.categoryDao = categoryDao;
        this.authorDao = authorDao;
        this.bookDao = bookDao;
        this.syncStateDao = syncStateDao;
        this.workDirManager = workDirManager;
    }

    public MasterSyncResult syncMaster() {
        int currentVersion = syncStateDao.getMasterVersion();
        log.info("shamela master sync starting: currentVersion={}", currentVersion);
        Optional<MasterMetadata> metaOpt = apiClient.fetchMasterMetadata(currentVersion);
        // Пустой Optional = shamela вернула 2xx с empty body = uptodate.
        // Тот же исход и при non-empty JSON с version == currentVersion
        if (metaOpt.isEmpty() || metaOpt.get().version() == currentVersion) {
            log.info("shamela master uptodate: version={}", currentVersion);
            return MasterSyncResult.unchanged(currentVersion);
        }
        MasterMetadata meta = metaOpt.get();
        if (meta.patchUrl() == null || meta.patchUrl().isBlank()) {
            throw new ShamelaImportException(
                    "shamela master metadata вернула version=" + meta.version()
                            + ", но patch_url пустой");
        }
        Path workDir = workDirManager.create("master");
        try {
            Path archive = apiClient.downloadArchive(URI.create(meta.patchUrl()), workDir);
            Path extracted = extractor.extract(archive, workDir.resolve("extracted"));

            Path categoryFile = workDirManager.requireSqlite(extracted, CATEGORY_SQLITE);
            Path authorFile = workDirManager.requireSqlite(extracted, AUTHOR_SQLITE);
            Path bookFile = workDirManager.requireSqlite(extracted, BOOK_SQLITE);

            int categories = categoryDao.upsertAll(masterReader.readCategories(categoryFile));
            int authors = authorDao.upsertAll(masterReader.readAuthors(authorFile));
            int books = bookDao.upsertAll(masterReader.readBooks(bookFile));

            syncStateDao.updateMasterVersion(meta.version());
            log.info("shamela master synced: {}->{}, categories={} authors={} books={}",
                    currentVersion, meta.version(), categories, authors, books);
            return MasterSyncResult.synced(currentVersion, meta.version(), categories, authors, books);
        } finally {
            workDirManager.cleanup(workDir);
        }
    }
}
