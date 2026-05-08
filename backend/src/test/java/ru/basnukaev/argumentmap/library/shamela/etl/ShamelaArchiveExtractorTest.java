package ru.basnukaev.argumentmap.library.shamela.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShamelaArchiveExtractorTest {

    private final ShamelaArchiveExtractor extractor = new ShamelaArchiveExtractor();

    @Test
    void extract_unpacks_master_like_zip(@TempDir Path tmp) throws IOException {
        // имитируем структуру master-zip от shamela - три "SQLite-файла" (для теста просто текст)
        Path zip = tmp.resolve("master-0-1261.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "category.sqlite", "fake-sqlite-category");
            putEntry(zos, "author.sqlite", "fake-sqlite-author");
            putEntry(zos, "book.sqlite", "fake-sqlite-book");
        }

        Path dest = tmp.resolve("unpacked");
        Path returned = extractor.extract(zip, dest);

        assertThat(returned).isEqualTo(dest);
        assertThat(dest.resolve("category.sqlite")).isRegularFile();
        assertThat(dest.resolve("author.sqlite")).isRegularFile();
        assertThat(dest.resolve("book.sqlite")).isRegularFile();
        assertThat(Files.readString(dest.resolve("category.sqlite"))).isEqualTo("fake-sqlite-category");
    }

    @Test
    void extract_unpacks_book_like_zip_with_single_sqlite(@TempDir Path tmp) throws IOException {
        // имитируем books-store/{bookId}-{major}.zip - один файл {bookId}.sqlite
        Path zip = tmp.resolve("35077-4.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "35077.sqlite", "fake-book-content");
        }

        Path dest = tmp.resolve("book");
        extractor.extract(zip, dest);

        assertThat(dest.resolve("35077.sqlite")).isRegularFile();
        assertThat(Files.list(dest)).hasSize(1);
    }

    @Test
    void extract_creates_destination_directory_if_missing(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("simple.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "x.txt", "hello");
        }

        Path dest = tmp.resolve("missing").resolve("nested").resolve("dest");
        extractor.extract(zip, dest);

        assertThat(dest.resolve("x.txt")).isRegularFile();
    }

    @Test
    void extract_overrides_existing_files(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("update.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "data.sqlite", "new-content");
        }

        Path dest = tmp.resolve("dest");
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("data.sqlite"), "old-content");

        extractor.extract(zip, dest);

        assertThat(Files.readString(dest.resolve("data.sqlite"))).isEqualTo("new-content");
    }

    @Test
    void extract_blocks_zip_slip_path_traversal(@TempDir Path tmp) throws IOException {
        // защита: вредоносный архив с entry "../../etc/passwd" не должен записать
        // ничего вне dest каталога
        Path zip = tmp.resolve("malicious.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "../escape.txt", "should-not-extract");
        }
        Path dest = tmp.resolve("safe");

        assertThatThrownBy(() -> extractor.extract(zip, dest))
                .isInstanceOf(ShamelaArchiveException.class)
                .hasMessageContaining("заблокирована");
    }

    @Test
    void extract_throws_on_missing_zip(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.zip");

        assertThatThrownBy(() -> extractor.extract(missing, tmp.resolve("dest")))
                .isInstanceOf(ShamelaArchiveException.class)
                .hasMessageContaining("не существует");
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
