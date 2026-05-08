package ru.basnukaev.argumentmap.library.shamela.etl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * <p>Безопасность: проверяется path-traversal через {@code ../} (Zip
 * Slip). Каждая entry должна резолвиться внутри destDir, иначе
 * {@link ShamelaArchiveException}.
 */
@Component
public class ShamelaArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(ShamelaArchiveExtractor.class);

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
            try (InputStream in = Files.newInputStream(zipFile);
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
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
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                        extracted++;
                    }
                    zip.closeEntry();
                }
            }
            log.info("shamela распаковал zip {} ({} файлов) в {}", zipFile.getFileName(), extracted, destDir);
            return destDir;
        } catch (IOException e) {
            throw new ShamelaArchiveException("ошибка распаковки " + zipFile, e);
        }
    }
}
