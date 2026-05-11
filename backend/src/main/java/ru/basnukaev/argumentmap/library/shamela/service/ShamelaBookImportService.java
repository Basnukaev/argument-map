package ru.basnukaev.argumentmap.library.shamela.service;

import java.net.URI;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiClient;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiProperties;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaArchiveExtractor;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaBookReader;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookContent;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;

/**
 * Импорт страниц и заголовков конкретной книги из desktop-API shamela
 * в staging-таблицы {@code lib_shamela_page}/{@code lib_shamela_title}
 * (ADR-020). Один публичный метод - {@link #importBook(long)}.
 *
 * <p>Книга уже должна быть в {@code lib_shamela_book} (через
 * предварительный {@link ShamelaMasterSyncService#syncMaster()}), оттуда
 * берётся {@code major_release} для детерминированного URL.
 *
 * <p>Идемпотентность через {@code ON CONFLICT DO UPDATE}.
 */
@Service
public class ShamelaBookImportService {

    private static final Logger log = LoggerFactory.getLogger(ShamelaBookImportService.class);

    private final ShamelaApiClient apiClient;
    private final ShamelaArchiveExtractor extractor;
    private final ShamelaBookReader bookReader;
    private final ShamelaBookDao bookDao;
    private final ShamelaPageDao pageDao;
    private final ShamelaTitleDao titleDao;
    private final ShamelaWorkDirManager workDirManager;
    private final ShamelaApiProperties props;

    public ShamelaBookImportService(ShamelaApiClient apiClient,
                                    ShamelaArchiveExtractor extractor,
                                    ShamelaBookReader bookReader,
                                    ShamelaBookDao bookDao,
                                    ShamelaPageDao pageDao,
                                    ShamelaTitleDao titleDao,
                                    ShamelaWorkDirManager workDirManager,
                                    ShamelaApiProperties props) {
        this.apiClient = apiClient;
        this.extractor = extractor;
        this.bookReader = bookReader;
        this.bookDao = bookDao;
        this.pageDao = pageDao;
        this.titleDao = titleDao;
        this.workDirManager = workDirManager;
        this.props = props;
    }

    public BookImportResult importBook(long bookId) {
        ShamelaBookRow book = bookDao.findById(bookId).orElseThrow(() ->
                new ShamelaNotFoundException(
                        "книга id=" + bookId + " не найдена в lib_shamela_book - "
                                + "сначала вызови syncMaster()"));
        int majorRelease = book.majorRelease();
        URI url = URI.create(String.format("https://%s/books-store/%d-%d.zip",
                props.filesHost(), bookId, majorRelease));
        Path workDir = workDirManager.create("book-" + bookId);
        try {
            Path archive = apiClient.downloadArchive(url, workDir);
            Path extracted = extractor.extract(archive, workDir.resolve("extracted"));
            Path bookSqlite = workDirManager.findBookSqlite(extracted, bookId, majorRelease);

            ShamelaBookContent content = bookReader.read(bookSqlite, bookId);
            int pages = pageDao.upsertAll(content.pages());
            int titles = titleDao.upsertAll(content.titles());

            log.info("shamela book imported: bookId={} major={} pages={} titles={}",
                    bookId, majorRelease, pages, titles);
            return new BookImportResult(bookId, majorRelease, pages, titles);
        } finally {
            workDirManager.cleanup(workDir);
        }
    }
}
