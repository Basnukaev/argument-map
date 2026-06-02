package ru.basnukaev.argumentmap.library.shamela.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
     * Поиск известного sqlite-файла в распакованном master-архиве.
     * Сначала пробует плоский путь {@code extractedDir/{fileName}} (так
     * выглядит архив на момент исследования - три SQLite в корне). Если
     * не нашёлся - tolerant fallback: рекурсивный поиск по имени файла
     * (master-архив shamela может оборачивать содержимое в подкаталог
     * вроде {@code master-0-1261/category.sqlite} - поведение зеркалит
     * {@link #findBookSqlite} которое уже tolerant к вложенности).
     *
     * <p>Если файл не найден ни плоско, ни рекурсивно - бросаем
     * {@link ShamelaImportException} с диагностикой: что искали и что
     * РЕАЛЬНО лежит в архиве (имена + относительные пути). Это делает
     * ошибку actionable при upstream schema/structure drift - видно, был
     * ли скачан валидный архив и под какими именами лежат SQLite.
     */
    public Path requireSqlite(Path extractedDir, String fileName) {
        Path flat = extractedDir.resolve(fileName);
        if (Files.isRegularFile(flat)) {
            return flat;
        }
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            Optional<Path> nested = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();
            if (nested.isPresent()) {
                log.warn("shamela master sqlite {} найден через fallback walk: {}",
                        fileName, extractedDir.relativize(nested.get()));
                return nested.get();
            }
        } catch (IOException e) {
            throw new ShamelaImportException(
                    "ошибка обхода " + extractedDir + " для поиска " + fileName, e);
        }
        throw new ShamelaImportException(
                "ожидаемый SQLite-файл отсутствует в архиве: " + fileName
                        + " (распакован в " + extractedDir + "; реально в архиве: "
                        + listArchiveContents(extractedDir) + ")");
    }

    /**
     * Список содержимого распакованного архива (относительные пути) для
     * диагностики - помогает понять под какими именами/в каких подкаталогах
     * лежат файлы, если ожидаемый не найден.
     */
    private String listArchiveContents(Path extractedDir) {
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            List<String> names = walk
                    .filter(Files::isRegularFile)
                    .map(p -> extractedDir.relativize(p).toString())
                    .sorted()
                    .limit(50)
                    .toList();
            return names.isEmpty() ? "<пусто>" : names.toString();
        } catch (IOException e) {
            return "<ошибка обхода: " + e.getMessage() + ">";
        }
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
