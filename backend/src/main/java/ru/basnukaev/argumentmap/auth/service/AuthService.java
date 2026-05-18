package ru.basnukaev.argumentmap.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.AuthTokens;
import ru.basnukaev.argumentmap.auth.domain.RefreshToken;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.repository.RefreshTokenRepository;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.exception.InvalidCredentialsException;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Login / refresh / logout flow (ADR-040 + ADR-047). Выдаёт пары
 * access+refresh, ротирует refresh single-use, detect'ит stolen refresh.
 *
 * <p>Login: проверка пароля + сохранение refresh в БД с {@code token_hash}.
 *
 * <p>Refresh (ADR-047): single-use. На каждый /auth/refresh:
 * <ol>
 *   <li>SHA-256(incoming refresh) → lookup в refresh_tokens
 *   <li>Если уже revoked - <b>steal detected</b> - revoke всю chain user'а
 *       + force re-login (401)
 *   <li>Если expired - revoke + 401
 *   <li>Иначе - выпуск новой пары, mark старый replaced_by=new_id
 * </ol>
 *
 * <p>Logout: revoke incoming refresh.
 *
 * <p>Hash: SHA-256 hex - не bcrypt. Refresh validated на каждом requestе,
 * bcrypt медленный (~100ms на проверку). JWT signature - high-entropy, для
 * защиты от случайного БД-дампа SHA-256 достаточен.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // Сравним hash с любой константой чтобы не дать timing-leak
                    // "email не зарегистрирован" vs "пароль неверный"
                    passwordEncoder.matches(rawPassword, "$2a$10$DummyHashForTimingProtectionOnly");
                    return new InvalidCredentialsException("Неверный email или пароль");
                });

        if (user.passwordHash() == null) {
            // legacy X-User-Id user без password (см. ADR-040 transitional)
            throw new InvalidCredentialsException("Пароль не установлен для пользователя");
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException("Неверный email или пароль");
        }
        if (!user.enabled()) {
            throw new InvalidCredentialsException("Аккаунт деактивирован");
        }

        return issueTokenPair(user);
    }

    /**
     * Single-use refresh rotation (ADR-047). При reuse уже rotated токена -
     * revoke всю chain пользователя + log security incident.
     */
    @Transactional
    public AuthTokens refresh(String incomingRefreshToken) {
        // Сначала проверим что это refresh-токен, а не access (защита от
        // подмены типа). Не валидируем full signature здесь - этим занимается
        // jwtService.validateToken ниже. extractTokenType сам бросит
        // InvalidTokenException на malformed/expired - что нам и нужно.
        String tokenType = jwtService.extractTokenType(incomingRefreshToken);
        if (!JwtService.TYPE_REFRESH.equals(tokenType)) {
            throw new InvalidTokenException("Ожидается refresh-токен, получен: " + tokenType);
        }

        String hash = sha256(incomingRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh-токен не найден"));

        if (existing.revokedAt() != null) {
            // STEAL DETECTED. Кто-то использует уже ротированный/revoked
            // refresh. Это может быть attacker который скомпрометировал
            // старый refresh + параллельно с legitimate user'ом пытается
            // его использовать. Revoke всю chain → force re-login для всех
            // сессий user'а.
            int revoked = refreshTokenRepository.revokeAllByUserId(
                    existing.userId(), RefreshToken.REASON_STOLEN_DETECTED);
            log.warn(
                    "SECURITY: refresh token reuse detected userId={} previousReason={} "
                            + "tokensRevoked={} - forcing re-login on all sessions",
                    existing.userId(), existing.revocationReason(), revoked);
            throw new InvalidTokenException(
                    "Подозрительная активность - все сессии завершены, требуется повторный вход");
        }

        if (existing.expiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.revoke(existing.id(), RefreshToken.REASON_EXPIRED);
            throw new InvalidTokenException("Refresh-токен истёк");
        }

        User user = userRepository.findById(existing.userId())
                .orElseThrow(() -> new InvalidTokenException("Пользователь из refresh-токена не существует"));
        if (!user.enabled()) {
            throw new InvalidTokenException("Аккаунт деактивирован");
        }

        // Выпуск новой пары - сохраняем new в БД ДО mark replaced,
        // чтобы не было промежуточного состояния "старый revoked + новый
        // ещё не записан"
        AuthTokens newTokens = issueTokenPair(user);

        // Mark старого как replaced - rotation
        refreshTokenRepository.markReplaced(
                existing.id(),
                findIdByHash(sha256(newTokens.refreshToken())),
                RefreshToken.REASON_ROTATION);

        return newTokens;
    }

    /**
     * Revoke incoming refresh (logout). Идемпотентна - если токена нет
     * или уже revoked, просто no-op (не бросает исключение - logout
     * должен быть forgiving).
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        String hash = sha256(refreshTokenValue);
        refreshTokenRepository.findByHash(hash)
                .filter(t -> t.revokedAt() == null)
                .ifPresent(t -> refreshTokenRepository.revoke(t.id(), RefreshToken.REASON_LOGOUT));
    }

    /**
     * Выпуск пары access+refresh + персист записи refresh в БД (только
     * hash, raw value возвращается caller'у для cookie).
     */
    private AuthTokens issueTokenPair(User user) {
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        Instant accessExpiry = jwtService.accessTokenExpiry();
        Instant refreshExpiry = jwtService.refreshTokenExpiry();

        RefreshToken record = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                sha256(refresh),
                Instant.now(),
                refreshExpiry,
                null, null, null
        );
        refreshTokenRepository.save(record);

        return new AuthTokens(access, accessExpiry, refresh, refreshExpiry);
    }

    /**
     * Lookup id только-что сохранённого токена для simple chain linking.
     * Альтернатива - возвращать id из {@code save()}, но record-immutability
     * требовала бы более сложного API. SHA-256 уникален по UNIQUE
     * constraint на token_hash, single lookup гарантирован.
     */
    private UUID findIdByHash(String hash) {
        return refreshTokenRepository.findByHash(hash)
                .map(RefreshToken::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Только что сохранённый refresh не найден по hash"));
    }

    /**
     * SHA-256 hex от UTF-8 представления. Используется для хранения
     * refresh-токена в БД (см. ADR-047 - не bcrypt).
     */
    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен на JVM", e);
        }
    }
}
