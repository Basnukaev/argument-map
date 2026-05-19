package ru.basnukaev.argumentmap.auth.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.auth.domain.RefreshToken;

/**
 * Доступ к {@code refresh_tokens} (ADR-047). Single-use refresh с
 * tracking-таблицей. JDBC Template + ручной RowMapper, как и весь
 * проект (см. /backend/CLAUDE.md - без JPA).
 */
@Repository
public class RefreshTokenRepository {

    private static final String COLUMNS =
            "id, user_id, token_hash, issued_at, expires_at, revoked_at, replaced_by, revocation_reason";

    private static final RowMapper<RefreshToken> ROW_MAPPER = (rs, rn) -> new RefreshToken(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("token_hash"),
            instant(rs, "issued_at"),
            instant(rs, "expires_at"),
            instant(rs, "revoked_at"),
            rs.getObject("replaced_by", UUID.class),
            rs.getString("revocation_reason")
    );

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefreshToken save(RefreshToken token) {
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                token.id(),
                token.userId(),
                token.tokenHash(),
                odt(token.issuedAt()),
                odt(token.expiresAt()),
                odt(token.revokedAt()),
                token.replacedBy(),
                token.revocationReason()
        );
        return token;
    }

    /**
     * Lookup даже если revoked - нужно для steal detection. AuthService
     * сам решает что делать (active → rotate, revoked → stolen).
     */
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM refresh_tokens WHERE token_hash = ?",
                ROW_MAPPER,
                tokenHash
        ).stream().findFirst();
    }

    /**
     * Active = revoked_at IS NULL AND expires_at > now(). Используется
     * в местах где нас не интересуют revoked токены отдельно (например
     * future janitor cleanup).
     */
    public Optional<RefreshToken> findActiveByHash(String tokenHash) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM refresh_tokens "
                        + "WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()",
                ROW_MAPPER,
                tokenHash
        ).stream().findFirst();
    }

    /**
     * Mark token revoked. Идемпотентна (UPDATE с фиксацией revoked_at если
     * уже null - чтобы не перезаписать предыдущую дату revocation).
     */
    public int revoke(UUID id, String reason) {
        return jdbcTemplate.update(
                "UPDATE refresh_tokens "
                        + "SET revoked_at = COALESCE(revoked_at, now()), "
                        + "    revocation_reason = COALESCE(revocation_reason, ?) "
                        + "WHERE id = ?",
                reason, id
        );
    }

    /**
     * Rotation: revoke текущий + указать replaced_by. Атомарный update
     * чтобы не было промежуточного состояния (revoked но без replaced_by).
     */
    public int markReplaced(UUID id, UUID replacedBy, String reason) {
        return jdbcTemplate.update(
                "UPDATE refresh_tokens "
                        + "SET revoked_at = COALESCE(revoked_at, now()), "
                        + "    replaced_by = ?, "
                        + "    revocation_reason = COALESCE(revocation_reason, ?) "
                        + "WHERE id = ? AND revoked_at IS NULL",
                replacedBy, reason, id
        );
    }

    /**
     * Bulk revoke всех active refresh-токенов user'а - вызывается при
     * steal detection (ADR-047). Возвращает количество затронутых строк
     * (для логирования).
     */
    public int revokeAllByUserId(UUID userId, String reason) {
        return jdbcTemplate.update(
                "UPDATE refresh_tokens "
                        + "SET revoked_at = now(), revocation_reason = ? "
                        + "WHERE user_id = ? AND revoked_at IS NULL",
                reason, userId
        );
    }

    /**
     * Найти токен, который был заменён указанным (chain backward).
     * Используется для аудита - проследить chain от current до initial
     * issuance.
     */
    public Optional<RefreshToken> findByReplacedBy(UUID replacedById) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM refresh_tokens WHERE replaced_by = ?",
                ROW_MAPPER,
                replacedById
        ).stream().findFirst();
    }

    /**
     * Cleanup helper для будущего janitor'а: revoke просроченных active
     * токенов (revoked_at NULL + expires_at < now()). Сейчас не вызывается
     * автоматически - оставлено для RefreshTokenCleanupJanitor (backlog).
     */
    public int revokeExpired(Instant cutoff) {
        return jdbcTemplate.update(
                "UPDATE refresh_tokens "
                        + "SET revoked_at = now(), revocation_reason = ? "
                        + "WHERE revoked_at IS NULL AND expires_at < ?",
                RefreshToken.REASON_EXPIRED, odt(cutoff)
        );
    }

    /**
     * Hard DELETE refresh-токенов старше {@code cutoff}. Удаляет:
     * <ul>
     *   <li>revoked токены где {@code revoked_at < cutoff} (history больше
     *       не нужна для steal-detection после retention window)
     *   <li>expired активные токены {@code expires_at < cutoff} - они
     *       никогда не будут validated (expired) и нет смысла хранить
     * </ul>
     *
     * <p>Используется {@code RefreshTokenCleanupJanitor} (ADR-047
     * follow-up). Без soft-delete - refresh-история редко нужна для
     * forensics, blob-storage backup-based recovery достаточно.
     * Возвращает количество удалённых строк.
     */
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM refresh_tokens "
                        + "WHERE (revoked_at IS NOT NULL AND revoked_at < ?) "
                        + "   OR (revoked_at IS NULL AND expires_at < ?)",
                odt(cutoff), odt(cutoff)
        );
    }
}
