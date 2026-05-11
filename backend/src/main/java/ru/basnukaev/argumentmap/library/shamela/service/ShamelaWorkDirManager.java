package ru.basnukaev.argumentmap.library.shamela.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiProperties;

/**
 * Управление рабочими каталогами для shamela-импорта. Каждый вызов
 * shamela-сервиса создаёт изолированный workdir через
 * {@link Files#createTempDirectory(Path, String, java.nio.file.attribute.FileAttribute[])}
 * в {@code shamela.download-dir}. После завершения (или exception)
 * каталог рекурсивно удаляется. Это безопасно для concurrent вызовов
 * (например параллельный import двух книг).
 */
@Component
public class ShamelaWorkDirManager {

    private static final Logger log = LoggerFactory.getLogger(ShamelaWorkDirManager.class);

    private final ShamelaApiProperties props;

    public ShamelaWorkDirManager(ShamelaApiProperties props) {
        this.props = props;
    }

    public Path create(String prefix) {
        try {
            Path base = Path.of(props.downloadDir());
            Files.createDirectories(base);
            return Files.createTempDirectory(base, prefix + "-");
        } catch (IOException e) {
            throw new ShamelaImportException(
                    "не удалось создать рабочий каталог в " + props.downloadDir(), e);
        }
    }

    public void cleanup(Path dir) {
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

    /**
     * Проверка наличия известного sqlite-файла в распакованном каталоге.
     * Используется для master-архивов с детерминированными именами.
     */
    public Path requireSqlite(Path extractedDir, String fileName) {
        Path file = extractedDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new ShamelaImportException(
                    "ожидаемый SQLite-файл отсутствует в архиве: " + fileName
                            + " (распакован в " + extractedDir + ")");
        }
        return file;
    }

    /**
     * Tolerant поиск sqlite-файла книги в распакованном каталоге.
     * Реальные naming convention shamela:
     * <ul>
     *   <li>{@code {bookId}-{majorRelease}.sqlite} - major_release=6+
     *       (например {@code 1681-6.sqlite})</li>
     *   <li>{@code {bookId}.sqlite} - старый формат (мокированные
     *       тестовые архивы, возможно live для старых версий)</li>
     * </ul>
     *
     * <p>Стратегия: пробуем оба известных имени, если ни один не нашёлся
     * - ищем любой {@code .sqlite} файл рекурсивно. Если найден ровно
     * один - возвращаем. Иначе - {@link ShamelaImportException}.
     */
    public Path findBookSqlite(Path extractedDir, long bookId, int majorRelease) {
        Path withMajor = extractedDir.resolve(bookId + "-" + majorRelease + ".sqlite");
        if (Files.isRegularFile(withMajor)) {
            return withMajor;
        }
        Path bareId = extractedDir.resolve(bookId + ".sqlite");
        if (Files.isRegularFile(bareId)) {
            return bareId;
        }
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            List<Path> sqliteFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sqlite"))
                    .toList();
            if (sqliteFiles.size() == 1) {
                log.warn("shamela book {} sqlite найден через fallback walk: {}",
                        bookId, sqliteFiles.get(0).getFileName());
                return sqliteFiles.get(0);
            }
            throw new ShamelaImportException(
                    "не удалось найти sqlite-файл книги " + bookId
                            + " в распакованном архиве (распакован в " + extractedDir
                            + ", найдено .sqlite файлов: " + sqliteFiles.size()
                            + ", искали " + bookId + "-" + majorRelease + ".sqlite и "
                            + bookId + ".sqlite)");
        } catch (IOException e) {
            throw new ShamelaImportException(
                    "ошибка обхода " + extractedDir + " для поиска sqlite", e);
        }
    }
}
