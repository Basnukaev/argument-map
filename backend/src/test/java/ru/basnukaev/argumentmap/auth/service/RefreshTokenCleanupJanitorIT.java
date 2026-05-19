package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.RefreshToken;
import ru.basnukaev.argumentmap.auth.repository.RefreshTokenRepository;

/**
 * IT для {@link RefreshTokenCleanupJanitor} (ADR-047 follow-up).
 *
 * <p>Сценарии:
 * <ul>
 *   <li>revoked старше retention - удаляется
 *   <li>revoked внутри retention - остаётся
 *   <li>expired активный старше retention - удаляется
 *   <li>active с expires_at в будущем - остаётся
 *   <li>revoked recently - остаётся (внутри window)
 * </ul>
 *
 * <p>Janitor enable'нут через {@code refresh-token.cleanup.enabled=true}
 * для активации bean'а под {@code @ConditionalOnProperty}. Минимально
 * допустимый retention 7 дней, тест использует 30 как production-realistic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "refresh-token.cleanup.enabled=true",
        "refresh-token.cleanup.retention-days=30"
})
class RefreshTokenCleanupJanitorIT {

    @Autowired private RefreshTokenCleanupJanitor janitor;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        // Чистка таблицы между тестами - изолированность. Tests этого
        // класса не используют @Transactional (cleanup'у janitor нужен
        // commit'ed state в БД), вместо этого ручной cleanup
        jdbc.update("DELETE FROM refresh_tokens");

        userId = UUID.randomUUID();
        // Добавим user'а - FK refresh_tokens.user_id → users(id) CASCADE.
        // Random suffix для username/email чтобы UNIQUE constraints не
        // блокировали последующие тесты (между тестами user не чистится -
        // другие IT в той же session могут полагаться на seeded users)
        String suffix = userId.toString().substring(0, 8);
        jdbc.update("INSERT INTO users (id, email, username, password_hash, role, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, 'USER', true, now())",
                userId,
                "cleanup-test-" + suffix + "@example.com",
                "cleanup-test-" + suffix,
                "$2a$10$dummybcrypthashstubforit/aaaaaa/");
    }

    @Test
    void cleanup_removesRevokedOlderThanRetention() {
        // Revoked 60 дней назад - за пределами 30-day retention
        Instant oldRevocation = Instant.now().minus(60, ChronoUnit.DAYS);
        UUID oldId = insertRefresh("old-revoked", Instant.now().plus(7, ChronoUnit.DAYS), oldRevocation);

        int deleted = janitor.cleanup();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokenRepository.findByHash("old-revoked")).isEmpty();
        // Cross-check direct DB count
        Long remaining = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE id = ?", Long.class, oldId);
        assertThat(remaining).isZero();
    }

    @Test
    void cleanup_keepsRevokedWithinRetention() {
        // Revoked 5 дней назад - внутри 30-day window
        Instant recentRevocation = Instant.now().minus(5, ChronoUnit.DAYS);
        insertRefresh("recent-revoked", Instant.now().plus(7, ChronoUnit.DAYS), recentRevocation);

        janitor.cleanup();

        assertThat(refreshTokenRepository.findByHash("recent-revoked")).isPresent();
    }

    @Test
    void cleanup_removesExpiredNeverUsed() {
        // Active токен (revoked_at NULL) но expires_at 60 дней назад -
        // никогда не будет валидным, чистим
        Instant longExpired = Instant.now().minus(60, ChronoUnit.DAYS);
        insertRefresh("expired-stale", longExpired, null);

        int deleted = janitor.cleanup();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokenRepository.findByHash("expired-stale")).isEmpty();
    }

    @Test
    void cleanup_keepsActiveValidTokens() {
        // Active токен с expires_at в будущем - не трогаем
        insertRefresh("active-valid", Instant.now().plus(5, ChronoUnit.DAYS), null);

        janitor.cleanup();

        assertThat(refreshTokenRepository.findByHash("active-valid")).isPresent();
    }

    @Test
    void cleanup_returnsCountOfDeleted() {
        // Mix: 2 удаляемых + 1 живой
        insertRefresh("old-1", Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now().minus(60, ChronoUnit.DAYS));
        insertRefresh("old-2", Instant.now().minus(60, ChronoUnit.DAYS), null);
        insertRefresh("alive", Instant.now().plus(5, ChronoUnit.DAYS), null);

        int deleted = janitor.cleanup();

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findByHash("alive")).isPresent();
        assertThat(refreshTokenRepository.findByHash("old-1")).isEmpty();
        assertThat(refreshTokenRepository.findByHash("old-2")).isEmpty();
    }

    private UUID insertRefresh(String hashLabel, Instant expiresAt, Instant revokedAt) {
        UUID id = UUID.randomUUID();
        RefreshToken token = new RefreshToken(
                id, userId, hashLabel,
                Instant.now().minus(70, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS),
                expiresAt.truncatedTo(ChronoUnit.MICROS),
                revokedAt == null ? null : revokedAt.truncatedTo(ChronoUnit.MICROS),
                null,
                revokedAt == null ? null : RefreshToken.REASON_ROTATION
        );
        refreshTokenRepository.save(token);
        return id;
    }
}
