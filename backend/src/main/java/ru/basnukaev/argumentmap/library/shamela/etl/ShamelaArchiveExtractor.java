package ru.basnukaev.argumentmap.library.shamela.etl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Распаковывает zip-архивы shamela ({@code master-{from}-{to}.zip} с
 * тремя SQLite внутри, либо {@code {bookId}-{major}.zip} с одним
 * {@code {bookId}.sqlite}). Стандартный deflate-формат, никакой
 * специфики shamela тут нет.
 *
 * <p>Безопасность:
 * <ul>
 *   <li>path-traversal через {@code ../} (Zip Slip) - каждая entry должна
 *       резолвиться внутри destDir;</li>
 *   <li>decompression bomb - per-entry / total / entry-count лимиты на
 *       распакованный объём. Без них вредоносный zip (или повреждённый дамп)
 *       мог бы раздуться до исчерпания диска. Считаем фактически записанные
 *       байты в потоке копирования (не доверяем заявленному {@code getSize}).</li>
 * </ul>
 * Любое нарушение → {@link ShamelaArchiveException}.
 */
@Component
public class ShamelaArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(ShamelaArchiveExtractor.class);

    /**
     * Лимит по умолчанию на распакованный размер одной entry. Самые крупные
     * SQLite от shamela - порядка сотен МБ; 2 ГБ - щедрый потолок с большим
     * запасом, который при этом отсекает явный bomb.
     */
    static final long DEFAULT_MAX_ENTRY_SIZE_BYTES = 2L * 1024 * 1024 * 1024;

    /** Лимит по умолчанию на суммарный распакованный размер (master-zip = 3 SQLite). */
    static final long DEFAULT_MAX_TOTAL_SIZE_BYTES = 8L * 1024 * 1024 * 1024;

    /** Лимит по умолчанию на количество entry - master-zip несёт единицы файлов, не тысячи. */
    static final int DEFAULT_MAX_ENTRY_COUNT = 10_000;

    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final long maxEntrySizeBytes;
    private final long maxTotalSizeBytes;
    private final int maxEntryCount;

    /** Prod-конструктор (Spring) - дефолтные лимиты. */
    public ShamelaArchiveExtractor() {
        this(DEFAULT_MAX_ENTRY_SIZE_BYTES, DEFAULT_MAX_TOTAL_SIZE_BYTES, DEFAULT_MAX_ENTRY_COUNT);
    }

    /**
     * Конструктор с настраиваемыми лимитами - для тестов decompression-bomb
     * guard'а (крафтить реальные 2+ ГБ архивы непрактично).
     */
    ShamelaArchiveExtractor(long maxEntrySizeBytes, long maxTotalSizeBytes, int maxEntryCount) {
        this.maxEntrySizeBytes = maxEntrySizeBytes;
        this.maxTotalSizeBytes = maxTotalSizeBytes;
        this.maxEntryCount = maxEntryCount;
    }

    /**
     * Распаковывает zip полностью в destDir, переписывая существующие
     * файлы. Возвращает destDir для удобства chaining.
     */
    public Path extract(Path zipFile, Path destDir) {
        if (!Files.isRegularFile(zipFile)) {
            throw new ShamelaArchiveException("zip-файл не существует: " + zipFile);
        }
        try {
            Files.createDirectories(destDir);
            Path destNormalized = destDir.toAbsolutePath().normalize();
            int extracted = 0;
            int entryCount = 0;
            long totalBytes = 0;
            try (InputStream in = Files.newInputStream(zipFile);
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entryCount > maxEntryCount) {
                        throw new ShamelaArchiveException(
                                "превышен лимит количества записей в архиве (" + maxEntryCount
                                        + ") - возможен zip-bomb");
                    }
                    Path target = destNormalized.resolve(entry.getName()).normalize();
                    if (!target.startsWith(destNormalized)) {
                        // защита от Zip Slip - архив пытается записать вне destDir
                        throw new ShamelaArchiveException(
                                "запись вне target-каталога заблокирована: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        totalBytes += copyWithLimit(zip, target, totalBytes, entry.getName());
                        extracted++;
                    }
                    zip.closeEntry();
                }
            }
            log.info("shamela распаковал zip {} ({} файлов, {} байт) в {}",
                    zipFile.getFileName(), extracted, totalBytes, destDir);
            return destDir;
        } catch (IOException e) {
            throw new ShamelaArchiveException("ошибка распаковки " + zipFile, e);
        }
    }

    /**
     * Копирует одну entry на диск, считая фактические байты и обрывая запись при
     * превышении per-entry либо суммарного лимита (decompression-bomb guard).
     * Не доверяем заявленному {@code ZipEntry.getSize} - считаем реально
     * прочитанное. При превышении частично записанный файл удаляется и
     * бросается {@link ShamelaArchiveException}.
     *
     * @param alreadyExtracted суммарно записано до этой entry
     * @return сколько байт записала эта entry
     */
    private long copyWithLimit(ZipInputStream zip, Path target,
                               long alreadyExtracted, String entryName) throws IOException {
        long entryBytes = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = zip.read(buffer)) != -1) {
                entryBytes += read;
                if (entryBytes > maxEntrySizeBytes) {
                    abort(target, "запись '" + entryName + "' превышает per-entry лимит "
                            + maxEntrySizeBytes + " байт - возможен zip-bomb");
                }
                if (alreadyExtracted + entryBytes > maxTotalSizeBytes) {
                    abort(target, "суммарный распакованный объём превышает лимит "
                            + maxTotalSizeBytes + " байт - возможен zip-bomb");
                }
                out.write(buffer, 0, read);
            }
        }
        return entryBytes;
    }

    private static void abort(Path partial, String message) throws IOException {
        Files.deleteIfExists(partial);
        throw new ShamelaArchiveException(message);
    }
}
