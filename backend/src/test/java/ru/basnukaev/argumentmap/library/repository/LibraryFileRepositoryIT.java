package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LibraryFileRepositoryIT {

    @Autowired
    private LibraryFileRepository libraryFileRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void save_insertsLibraryFile_andFindByIdReturnsIt() {
        Book book = bookRepository.save(book("Сахих аль-Бухари"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LibraryFile file = new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", book.id() + "/01.pdf",
                "https://archive.org/download/sahih/01.pdf",
                LibraryFileSourceType.SHAMELA,
                "abc123def456", 1024L,
                "\"etag-abc\"", now, null, 6,
                "{\"volume\":1}", null
        );

        libraryFileRepository.save(file);

        LibraryFile reloaded = libraryFileRepository.findById(file.fileId()).orElseThrow();
        assertThat(reloaded.bookId()).isEqualTo(book.id());
        assertThat(reloaded.bucket()).isEqualTo("library-imported-books");
        assertThat(reloaded.storageKey()).isEqualTo(book.id() + "/01.pdf");
        assertThat(reloaded.sourceType()).isEqualTo(LibraryFileSourceType.SHAMELA);
        assertThat(reloaded.contentHash()).isEqualTo("abc123def456");
        assertThat(reloaded.sizeBytes()).isEqualTo(1024L);
        assertThat(reloaded.etag()).isEqualTo("\"etag-abc\"");
        assertThat(reloaded.downloadedAt()).isEqualTo(now);
        assertThat(reloaded.lastVerifiedAt()).isNull();
        assertThat(reloaded.shamelaMajorRelease()).isEqualTo(6);
        assertThat(reloaded.metadata()).contains("\"volume\"");
        assertThat(reloaded.deletedAt()).isNull();
    }

    @Test
    void save_withoutBookId_persistsForDerivedArtifact() {
        LibraryFile derived = libraryFile(null, "derived-artifacts", "graph-export-1.svg",
                LibraryFileSourceType.DERIVED);

        libraryFileRepository.save(derived);

        assertThat(libraryFileRepository.findById(derived.fileId()).orElseThrow().bookId()).isNull();
    }

    @Test
    void deleteBook_cascadesToLibraryFiles() {
        Book book = bookRepository.save(book("Тафсир"));
        LibraryFile file = libraryFile(book.id(), "library-imported-books",
                book.id() + "/01.pdf", LibraryFileSourceType.SHAMELA);
        libraryFileRepository.save(file);

        bookRepository.deleteById(book.id());

        assertThat(libraryFileRepository.findById(file.fileId())).isEmpty();
    }

    @Test
    void save_duplicateBucketAndKey_throwsDuplicateKey() {
        Book book = bookRepository.save(book("X"));
        LibraryFile first = libraryFile(book.id(), "library-imported-books",
                "shared-key.pdf", LibraryFileSourceType.SHAMELA);
        libraryFileRepository.save(first);
        LibraryFile collision = libraryFile(book.id(), "library-imported-books",
                "shared-key.pdf", LibraryFileSourceType.SHAMELA);

        assertThatThrownBy(() -> libraryFileRepository.save(collision))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void save_sameKeyDifferentBucket_isAllowed() {
        Book book = bookRepository.save(book("X"));
        libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));
        libraryFileRepository.save(libraryFile(book.id(),
                "derived-artifacts", "k.pdf", LibraryFileSourceType.DERIVED));

        List<LibraryFile> all = libraryFileRepository.findActiveByBookId(book.id());
        assertThat(all).hasSize(2);
        assertThat(all).extracting(LibraryFile::bucket)
                .containsExactlyInAnyOrder("library-imported-books", "derived-artifacts");
    }

    @Test
    void save_invalidSourceType_violatesCheckConstraint() {
        Book book = bookRepository.save(book("X"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO library_files (file_id, book_id, bucket, storage_key, source_type, "
                        + "content_hash, size_bytes, downloaded_at, metadata) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), book.id(), "library-imported-books", "k.pdf",
                "INVALID_TYPE", "hash", 1L,
                java.sql.Timestamp.from(Instant.now()), "{}"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_negativeSizeBytes_violatesCheckConstraint() {
        Book book = bookRepository.save(book("X"));
        LibraryFile invalid = new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "neg.pdf",
                null, LibraryFileSourceType.SHAMELA,
                "hash", -1L, null, Instant.now(), null, null,
                "{}", null
        );

        assertThatThrownBy(() -> libraryFileRepository.save(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_replacesAllFields() {
        Book book = bookRepository.save(book("X"));
        LibraryFile saved = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));
        Instant later = Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(60);
        LibraryFile updated = new LibraryFile(
                saved.fileId(), book.id(),
                "library-imported-books", "k.pdf",
                "https://archive.org/changed.pdf",
                LibraryFileSourceType.ARCHIVE_ORG,
                "new-hash", 2048L,
                "\"new-etag\"", later, later, 7,
                "{\"refreshed\":true}", null
        );

        boolean ok = libraryFileRepository.update(updated);

        assertThat(ok).isTrue();
        LibraryFile reloaded = libraryFileRepository.findById(saved.fileId()).orElseThrow();
        assertThat(reloaded.contentHash()).isEqualTo("new-hash");
        assertThat(reloaded.sizeBytes()).isEqualTo(2048L);
        assertThat(reloaded.sourceType()).isEqualTo(LibraryFileSourceType.ARCHIVE_ORG);
        assertThat(reloaded.shamelaMajorRelease()).isEqualTo(7);
        assertThat(reloaded.lastVerifiedAt()).isEqualTo(later);
    }

    @Test
    void update_nonExistingFileId_returnsFalse() {
        LibraryFile ghost = libraryFile(null, "derived-artifacts",
                "ghost.svg", LibraryFileSourceType.DERIVED);

        assertThat(libraryFileRepository.update(ghost)).isFalse();
    }

    @Test
    void findActiveByBucketAndKey_excludesSoftDeleted() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));

        libraryFileRepository.softDelete(file.fileId(), Instant.now());

        assertThat(libraryFileRepository.findActiveByBucketAndKey(
                "library-imported-books", "k.pdf")).isEmpty();
        assertThat(libraryFileRepository.findById(file.fileId())).isPresent();
    }

    @Test
    void findActiveByBookId_returnsMultipleFiles_orderedByDownloadedAt() {
        Book book = bookRepository.save(book("Multi-volume"));
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(300);
        libraryFileRepository.save(new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "vol2.pdf",
                null, LibraryFileSourceType.SHAMELA,
                "hash2", 1L, null, t0.plusSeconds(60), null, 6,
                "{}", null
        ));
        libraryFileRepository.save(new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "vol1.pdf",
                null, LibraryFileSourceType.SHAMELA,
                "hash1", 1L, null, t0, null, 6,
                "{}", null
        ));

        List<LibraryFile> files = libraryFileRepository.findActiveByBookId(book.id());

        assertThat(files).extracting(LibraryFile::storageKey)
                .containsExactly("vol1.pdf", "vol2.pdf");
    }

    @Test
    void findActiveBySourceUrl_returnsExistingFile_forReimportDetection() {
        Book book = bookRepository.save(book("X"));
        LibraryFile original = new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "k.pdf",
                "https://archive.org/download/x/k.pdf",
                LibraryFileSourceType.SHAMELA,
                "hash", 1L, null, Instant.now(), null, 6,
                "{}", null
        );
        libraryFileRepository.save(original);

        LibraryFile found = libraryFileRepository.findActiveBySourceUrl(
                "https://archive.org/download/x/k.pdf").orElseThrow();
        assertThat(found.fileId()).isEqualTo(original.fileId());
    }

    @Test
    void findActiveBySourceUrl_excludesSoftDeleted() {
        Book book = bookRepository.save(book("X"));
        LibraryFile original = new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "k.pdf",
                "https://archive.org/x.pdf",
                LibraryFileSourceType.SHAMELA,
                "hash", 1L, null, Instant.now(), null, 6,
                "{}", null
        );
        libraryFileRepository.save(original);
        libraryFileRepository.softDelete(original.fileId(), Instant.now());

        assertThat(libraryFileRepository.findActiveBySourceUrl(
                "https://archive.org/x.pdf")).isEmpty();
    }

    @Test
    void softDelete_alreadyDeleted_isNoOpReturningFalse() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));
        libraryFileRepository.softDelete(file.fileId(), Instant.now());

        assertThat(libraryFileRepository.softDelete(file.fileId(), Instant.now())).isFalse();
    }

    @Test
    void hardDelete_removesRecordPhysically() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));

        assertThat(libraryFileRepository.hardDelete(file.fileId())).isTrue();
        assertThat(libraryFileRepository.findById(file.fileId())).isEmpty();
    }

    @Test
    void hardDelete_nonExisting_returnsFalse() {
        assertThat(libraryFileRepository.hardDelete(UUID.randomUUID())).isFalse();
    }

    @Test
    void markVerified_updatesLastVerifiedAt() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));
        Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        boolean ok = libraryFileRepository.markVerified(file.fileId(), verifiedAt);

        assertThat(ok).isTrue();
        assertThat(libraryFileRepository.findById(file.fileId())
                .orElseThrow().lastVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void markVerified_softDeleted_isNoOpReturningFalse() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = libraryFileRepository.save(libraryFile(book.id(),
                "library-imported-books", "k.pdf", LibraryFileSourceType.SHAMELA));
        libraryFileRepository.softDelete(file.fileId(), Instant.now());

        assertThat(libraryFileRepository.markVerified(
                file.fileId(), Instant.now())).isFalse();
    }

    @Test
    void metadataJsonb_isQueryableWithGinOperators() {
        Book book = bookRepository.save(book("X"));
        libraryFileRepository.save(new LibraryFile(
                UUID.randomUUID(), book.id(),
                "library-imported-books", "k.pdf",
                null, LibraryFileSourceType.SHAMELA,
                "hash", 1L, null, Instant.now(), null, null,
                "{\"ocr_model\":\"tess4j\",\"pages\":120}", null
        ));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_files "
                        + "WHERE metadata @> ?::jsonb",
                Integer.class,
                "{\"ocr_model\":\"tess4j\"}"
        );
        assertThat(count).isOne();
    }

    private Book book(String title) {
        Instant now = Instant.now();
        return new Book(UUID.randomUUID(), BookType.BOOK, title, null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null, BookVisibility.PUBLIC);
    }

    private LibraryFile libraryFile(UUID bookId, String bucket, String storageKey,
                                     LibraryFileSourceType sourceType) {
        return new LibraryFile(
                UUID.randomUUID(), bookId,
                bucket, storageKey,
                null, sourceType,
                "hash-" + UUID.randomUUID(), 1L,
                null, Instant.now().truncatedTo(ChronoUnit.MICROS), null, null,
                "{}", null
        );
    }
}
