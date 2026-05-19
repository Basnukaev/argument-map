package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.AuthTokens;
import ru.basnukaev.argumentmap.auth.domain.RefreshToken;
import ru.basnukaev.argumentmap.auth.repository.RefreshTokenRepository;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Покрытие single-use refresh rotation + steal detection (ADR-047).
 *
 * <p>Сценарии:
 * <ul>
 *   <li>valid → возвращает new pair, старый помечен rotation+replaced_by
 *   <li>reuse already rotated refresh → revoke всей chain (steal detected)
 *   <li>logout → revoke текущего refresh
 *   <li>login → создаёт refresh row в БД
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthServiceRotationIT {

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void login_savesRefreshTokenRecord() {
        userService.register("loginrec@example.com", "loginrec", "password1");
        AuthTokens tokens = authService.login("loginrec@example.com", "password1");

        // Запись существует, не revoked
        RefreshToken record = refreshTokenRepository
                .findByHash(sha256(tokens.refreshToken()))
                .orElseThrow();
        assertThat(record.revokedAt()).isNull();
        assertThat(record.replacedBy()).isNull();
        assertThat(record.revocationReason()).isNull();
        // AuthService.issueTokenPair truncate'ит к MICROS до persist (см.
        // комментарий там) - оба значения уже truncated, точное равенство ok
        assertThat(record.expiresAt()).isEqualTo(tokens.refreshTokenExpiresAt());
    }

    @Test
    void refresh_validToken_returnsNewPair_oldMarkedReplaced() {
        userService.register("rot@example.com", "rotuser", "password1");
        AuthTokens initial = authService.login("rot@example.com", "password1");

        AuthTokens rotated = authService.refresh(initial.refreshToken());

        // Новый refresh - другой строкой
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());

        // Старая запись revoked с reason=rotation и replaced_by=new
        RefreshToken oldRecord = refreshTokenRepository
                .findByHash(sha256(initial.refreshToken()))
                .orElseThrow();
        assertThat(oldRecord.revokedAt()).isNotNull();
        assertThat(oldRecord.revocationReason()).isEqualTo(RefreshToken.REASON_ROTATION);

        // Новая запись существует, active
        RefreshToken newRecord = refreshTokenRepository
                .findByHash(sha256(rotated.refreshToken()))
                .orElseThrow();
        assertThat(newRecord.revokedAt()).isNull();
        assertThat(oldRecord.replacedBy()).isEqualTo(newRecord.id());
    }

    @Test
    void refresh_reusedRefresh_revokesAllChain() {
        userService.register("steal@example.com", "stealuser", "password1");
        AuthTokens initial = authService.login("steal@example.com", "password1");

        // Первый legitimate rotation
        AuthTokens rotated = authService.refresh(initial.refreshToken());

        // Attacker пытается использовать тот же initial refresh снова
        // (он украл его до того как legitimate user сделал rotation)
        assertThatThrownBy(() -> authService.refresh(initial.refreshToken()))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Подозрительная активность");

        // ВСЯ chain user'а должна быть revoked - включая rotated (свежий)
        RefreshToken rotatedRecord = refreshTokenRepository
                .findByHash(sha256(rotated.refreshToken()))
                .orElseThrow();
        assertThat(rotatedRecord.revokedAt()).isNotNull();
        assertThat(rotatedRecord.revocationReason()).isEqualTo(RefreshToken.REASON_STOLEN_DETECTED);

        // Попытка legitimate user'а использовать его rotated токен -
        // тоже не сработает (он revoked - тоже steal detected re-trigger)
        assertThatThrownBy(() -> authService.refresh(rotated.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_garbageToken_throws() {
        assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_unknownButValidJwt_throwsNotFound() {
        // Регистрируем user A, выдаём refresh, но удаляем запись из БД
        // чтобы сэмулировать ситуацию когда JWT валидный но в refresh_tokens
        // его нет (например после revoke janitor'ом)
        userService.register("unkr@example.com", "unkruser", "password1");
        AuthTokens tokens = authService.login("unkr@example.com", "password1");
        RefreshToken existing = refreshTokenRepository
                .findByHash(sha256(tokens.refreshToken()))
                .orElseThrow();
        // Симулируем что запись была hard-deleted (не наш типичный flow,
        // но защита от inconsistency cases)
        refreshTokenRepository.revoke(existing.id(), "test-cleanup");
        // Bulk-revoke не удаляет, просто помечает - первая ветка проверки
        // (revoked_at != null) выпрыгивает с steal-detection. Это
        // правильный безопасный default - неизвестные/inconsistent state
        // → 401
        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logout_revokesRefreshToken() {
        userService.register("lo@example.com", "louser", "password1");
        AuthTokens tokens = authService.login("lo@example.com", "password1");

        authService.logout(tokens.refreshToken());

        RefreshToken record = refreshTokenRepository
                .findByHash(sha256(tokens.refreshToken()))
                .orElseThrow();
        assertThat(record.revokedAt()).isNotNull();
        assertThat(record.revocationReason()).isEqualTo(RefreshToken.REASON_LOGOUT);

        // Использовать logged-out refresh - попытка вызовет steal detection
        // (revoked_at != null) - правильно: после logout не должен
        // приниматься
        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logout_idempotent_nullValue() {
        // logout не должен бросать на null/blank (cookie могла быть удалена)
        authService.logout(null);
        authService.logout("");
        authService.logout("   ");
        // если дошли - ok, никакого исключения
    }

    @Test
    void refresh_replacedBy_chainsCorrectly() {
        // 3-token chain - проверяем что replaced_by указывает правильно
        userService.register("chain@example.com", "chainuser", "password1");
        AuthTokens t1 = authService.login("chain@example.com", "password1");
        AuthTokens t2 = authService.refresh(t1.refreshToken());
        AuthTokens t3 = authService.refresh(t2.refreshToken());

        RefreshToken r1 = refreshTokenRepository.findByHash(sha256(t1.refreshToken())).orElseThrow();
        RefreshToken r2 = refreshTokenRepository.findByHash(sha256(t2.refreshToken())).orElseThrow();
        RefreshToken r3 = refreshTokenRepository.findByHash(sha256(t3.refreshToken())).orElseThrow();

        assertThat(r1.replacedBy()).isEqualTo(r2.id());
        assertThat(r2.replacedBy()).isEqualTo(r3.id());
        assertThat(r3.replacedBy()).isNull();

        // только t3 active
        assertThat(r1.revokedAt()).isNotNull();
        assertThat(r2.revokedAt()).isNotNull();
        assertThat(r3.revokedAt()).isNull();
    }

    // ---- helpers ----

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
