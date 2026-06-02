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

    // ---- decompression-bomb guard (баг #3 Tier-3) ----

    @Test
    void extract_blocks_entry_exceeding_per_entry_limit(@TempDir Path tmp) throws IOException {
        // per-entry лимит 1 КБ; entry на 4 КБ должна оборваться и удалить partial
        ShamelaArchiveExtractor limited =
                new ShamelaArchiveExtractor(1024, 100 * 1024, 100);
        Path zip = tmp.resolve("bomb-entry.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "big.sqlite", "x".repeat(4096));
        }
        Path dest = tmp.resolve("dest");

        assertThatThrownBy(() -> limited.extract(zip, dest))
                .isInstanceOf(ShamelaArchiveException.class)
                .hasMessageContaining("per-entry лимит");
        // частично записанный файл должен быть удалён (abort cleanup)
        assertThat(dest.resolve("big.sqlite")).doesNotExist();
    }

    @Test
    void extract_blocks_total_size_exceeded_across_entries(@TempDir Path tmp) throws IOException {
        // per-entry 4 КБ (ОК для каждой), но суммарный лимит 5 КБ → вторая
        // entry перевалит общий лимит
        ShamelaArchiveExtractor limited =
                new ShamelaArchiveExtractor(4096, 5 * 1024, 100);
        Path zip = tmp.resolve("bomb-total.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "a.sqlite", "a".repeat(4096));
            putEntry(zos, "b.sqlite", "b".repeat(4096));
        }
        Path dest = tmp.resolve("dest");

        assertThatThrownBy(() -> limited.extract(zip, dest))
                .isInstanceOf(ShamelaArchiveException.class)
                .hasMessageContaining("суммарный распакованный объём");
    }

    @Test
    void extract_blocks_entry_count_exceeded(@TempDir Path tmp) throws IOException {
        // лимит 2 entry; кладём 3 → обрыв
        ShamelaArchiveExtractor limited =
                new ShamelaArchiveExtractor(1024 * 1024, 1024 * 1024, 2);
        Path zip = tmp.resolve("bomb-count.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "1.txt", "a");
            putEntry(zos, "2.txt", "b");
            putEntry(zos, "3.txt", "c");
        }
        Path dest = tmp.resolve("dest");

        assertThatThrownBy(() -> limited.extract(zip, dest))
                .isInstanceOf(ShamelaArchiveException.class)
                .hasMessageContaining("лимит количества записей");
    }

    @Test
    void extract_allows_archive_within_limits(@TempDir Path tmp) throws IOException {
        // нормальный архив в пределах лимитов проходит без ошибок
        ShamelaArchiveExtractor limited =
                new ShamelaArchiveExtractor(1024 * 1024, 4 * 1024 * 1024, 100);
        Path zip = tmp.resolve("ok.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "ok.sqlite", "y".repeat(2048));
        }
        Path dest = tmp.resolve("dest");

        limited.extract(zip, dest);
        assertThat(dest.resolve("ok.sqlite")).isRegularFile();
        assertThat(Files.readString(dest.resolve("ok.sqlite"))).hasSize(2048);
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
