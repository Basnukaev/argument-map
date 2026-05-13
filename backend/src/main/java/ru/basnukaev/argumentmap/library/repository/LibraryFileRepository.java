package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;

/**
 * Catalog для {@link LibraryFile} - запись о blob'е в object storage
 * (ADR-024). Все запросы по active-файлам (без soft-deleted) используют
 * partial index {@code idx_library_files_active}. Hard-delete -
 * отдельный метод, обычно вызывается из admin two-phase action.
 */
@Repository
public class LibraryFileRepository {

    private static final String COLUMNS =
            "file_id, book_id, bucket, storage_key, source_url, source_type, "
            + "content_hash, size_bytes, etag, downloaded_at, last_verified_at, "
            + "shamela_major_release, metadata, deleted_at";

    private static final RowMapper<LibraryFile> ROW_MAPPER = (rs, rn) -> new LibraryFile(
            rs.getObject("file_id", UUID.class),
            rs.getObject("book_id", UUID.class),
            rs.getString("bucket"),
            rs.getString("storage_key"),
            rs.getString("source_url"),
            LibraryFileSourceType.valueOf(rs.getString("source_type")),
            rs.getString("content_hash"),
            rs.getLong("size_bytes"),
            rs.getString("etag"),
            instant(rs, "downloaded_at"),
            instant(rs, "last_verified_at"),
            (Integer) rs.getObject("shamela_major_release"),
            rs.getString("metadata"),
            instant(rs, "deleted_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public LibraryFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LibraryFile save(LibraryFile file) {
        jdbcTemplate.update(
                "INSERT INTO library_files (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                file.fileId(),
                file.bookId(),
                file.bucket(),
                file.storageKey(),
                file.sourceUrl(),
                file.sourceType().name(),
                file.contentHash(),
                file.sizeBytes(),
                file.etag(),
                odt(file.downloadedAt()),
                odt(file.lastVerifiedAt()),
                file.shamelaMajorRelease(),
                file.metadata(),
                odt(file.deletedAt())
        );
        return file;
    }

    /**
     * Полный UPDATE по {@code file_id}. Используется при re-upload
     * того же storage_key (новый content_hash, новый downloaded_at,
     * etc) - bucket versioning создаёт новую версию объекта, в
     * catalog обновляется одна запись.
     *
     * @return {@code true} если запись существовала и была обновлена
     */
    public boolean update(LibraryFile file) {
        int updated = jdbcTemplate.update(
                "UPDATE library_files SET "
                        + "book_id = ?, bucket = ?, storage_key = ?, source_url = ?, "
                        + "source_type = ?, content_hash = ?, size_bytes = ?, etag = ?, "
                        + "downloaded_at = ?, last_verified_at = ?, "
                        + "shamela_major_release = ?, metadata = ?::jsonb, deleted_at = ? "
                        + "WHERE file_id = ?",
                file.bookId(),
                file.bucket(),
                file.storageKey(),
                file.sourceUrl(),
                file.sourceType().name(),
                file.contentHash(),
                file.sizeBytes(),
                file.etag(),
                odt(file.downloadedAt()),
                odt(file.lastVerifiedAt()),
                file.shamelaMajorRelease(),
                file.metadata(),
                odt(file.deletedAt()),
                file.fileId()
        );
        return updated > 0;
    }

    /**
     * Атомарный upsert по уникальному ключу {@code (bucket, storage_key)}.
     * Если row уже существует - все поля кроме {@code file_id} и
     * {@code (bucket, storage_key)} обновляются, существующий {@code file_id}
     * сохраняется. Если row нет - INSERT с {@code file_id} из аргумента.
     *
     * <p>Используется в {@code ObjectStorageService.putAndRegister} вместо
     * двухшагового find + save/update - убирает race condition при
     * concurrent first-load одного и того же объекта (две сессии могли
     * параллельно download'ить + регистрировать, получая DuplicateKey
     * на UNIQUE constraint).
     *
     * <p>Resurrects soft-deleted row: если row был помечен
     * {@code deleted_at}, upsert установит {@code deleted_at = NULL}
     * (через EXCLUDED). Это правильно для re-upload после accidental
     * soft-delete. Hard-delete делает физический DELETE - там
     * resurrection невозможен.
     */
    public LibraryFile upsertByBucketAndKey(LibraryFile file) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO library_files (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) "
                        + "ON CONFLICT (bucket, storage_key) DO UPDATE SET "
                        + "  book_id = EXCLUDED.book_id, "
                        + "  source_url = EXCLUDED.source_url, "
                        + "  source_type = EXCLUDED.source_type, "
                        + "  content_hash = EXCLUDED.content_hash, "
                        + "  size_bytes = EXCLUDED.size_bytes, "
                        + "  etag = EXCLUDED.etag, "
                        + "  downloaded_at = EXCLUDED.downloaded_at, "
                        + "  last_verified_at = EXCLUDED.last_verified_at, "
                        + "  shamela_major_release = EXCLUDED.shamela_major_release, "
                        + "  metadata = EXCLUDED.metadata, "
                        + "  deleted_at = EXCLUDED.deleted_at "
                        + "RETURNING " + COLUMNS,
                ROW_MAPPER,
                file.fileId(),
                file.bookId(),
                file.bucket(),
                file.storageKey(),
                file.sourceUrl(),
                file.sourceType().name(),
                file.contentHash(),
                file.sizeBytes(),
                file.etag(),
                odt(file.downloadedAt()),
                odt(file.lastVerifiedAt()),
                file.shamelaMajorRelease(),
                file.metadata(),
                odt(file.deletedAt())
        );
    }

    public Optional<LibraryFile> findById(UUID fileId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM library_files WHERE file_id = ?",
                ROW_MAPPER,
                fileId
        ).stream().findFirst();
    }

    /**
     * Lookup активной записи по physical location в storage. Используется
     * hot-path при чтении файла: бэк ищет catalog → если есть → отдаёт
     * из bucket'а. Partial index {@code idx_library_files_active}
     * ускоряет запрос.
     */
    public Optional<LibraryFile> findActiveByBucketAndKey(String bucket, String storageKey) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM library_files "
                        + "WHERE bucket = ? AND storage_key = ? AND deleted_at IS NULL",
                ROW_MAPPER,
                bucket,
                storageKey
        ).stream().findFirst();
    }

    /**
     * Все active-файлы конкретной книги. Используется для multi-volume
     * PDF (одна book → N files), book-сheet view "файлы привязанные к
     * книге".
     */
    public List<LibraryFile> findActiveByBookId(UUID bookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM library_files "
                        + "WHERE book_id = ? AND deleted_at IS NULL "
                        + "ORDER BY downloaded_at",
                ROW_MAPPER,
                bookId
        );
    }

    /**
     * Lookup по upstream URL - используется для re-import detection.
     * Если URL уже скачан и в catalog - не качаем заново. Partial
     * index {@code idx_library_files_source_url}.
     */
    public Optional<LibraryFile> findActiveBySourceUrl(String sourceUrl) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM library_files "
                        + "WHERE source_url = ? AND deleted_at IS NULL",
                ROW_MAPPER,
                sourceUrl
        ).stream().findFirst();
    }

    /**
     * Soft-delete: помечает запись удалённой, объект в storage остаётся.
     * Active-queries ({@code findActive*}) исключают такие записи.
     * Hard-delete - см. {@link #hardDelete(UUID)}.
     *
     * @return {@code true} если запись существовала и помечена удалённой
     *         (no-op если уже была soft-deleted)
     */
    public boolean softDelete(UUID fileId, Instant when) {
        int updated = jdbcTemplate.update(
                "UPDATE library_files SET deleted_at = ? "
                        + "WHERE file_id = ? AND deleted_at IS NULL",
                odt(when),
                fileId
        );
        return updated > 0;
    }

    /**
     * Hard-delete: физически удаляет запись из catalog. Обычно вызывается
     * из admin two-phase delete после физического удаления объекта в
     * bucket'е (см. ADR-024 GDPR-like deletion section). В обычном
     * пользовательском flow использовать {@link #softDelete}.
     */
    public boolean hardDelete(UUID fileId) {
        return jdbcTemplate.update("DELETE FROM library_files WHERE file_id = ?", fileId) > 0;
    }

    /**
     * Обновляет {@code last_verified_at} timestamp после успешной
     * integrity-check (background job сверил content_hash с физическим
     * объектом).
     */
    public boolean markVerified(UUID fileId, Instant when) {
        int updated = jdbcTemplate.update(
                "UPDATE library_files SET last_verified_at = ? "
                        + "WHERE file_id = ? AND deleted_at IS NULL",
                odt(when),
                fileId
        );
        return updated > 0;
    }
}
