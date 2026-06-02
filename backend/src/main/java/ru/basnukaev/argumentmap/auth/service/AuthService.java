package ru.basnukaev.argumentmap.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * Валидный BCrypt-хэш ($2a$10$, 22-символьная соль) для timing-protection.
     * Когда email не найден (или у пользователя нет пароля), мы всё равно
     * прогоняем {@code passwordEncoder.matches} против этой константы - bcrypt
     * KDF отрабатывает столько же времени, сколько и для существующего user'а.
     * <p><b>Важно:</b> хэш должен быть синтаксически корректным, иначе
     * {@code BCryptPasswordEncoder.matches} логирует warning и возвращается
     * мгновенно <b>без</b> запуска KDF - тогда timing-leak (user-enumeration по
     * времени ответа) сохраняется. Прежняя константа была malformed (баг #1
     * Tier-3). Это хэш произвольного пароля - сравнение всегда даёт false.
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$b75ZOcsmayKEZ6ySLD4bs.mW56WX/mIq7kmFpEOp2S0.djRt86LnG";

    /** Единое сообщение для всех login-неудач - не leak'аем что именно неверно. */
    private static final String INVALID_CREDENTIALS_MESSAGE = "Неверный email или пароль";

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
        User user = userRepository.findByEmail(email).orElse(null);

        // Баг #1 Tier-3: email не найден ИЛИ у user'а нет пароля (legacy
        // X-User-Id, ADR-040 transitional) - в обоих случаях прогоняем bcrypt
        // против валидного dummy-хэша, чтобы время ответа не зависело от факта
        // существования аккаунта (защита от user-enumeration по timing).
        if (user == null || user.passwordHash() == null) {
            passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        // Баг #2 Tier-3: disabled-аккаунт раньше бросал ОТДЕЛЬНОЕ сообщение
        // ("Аккаунт деактивирован") - это раскрывало, что аккаунт существует
        // (enabled vs disabled). Теперь то же generic invalid-credentials, что
        // и для неверного пароля - не различаем enabled/disabled наружу.
        if (!user.enabled()) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        return issueTokenPair(user).tokens();
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
        // ещё не записан". issueTokenPair возвращает (tokens, id) -
        // используем id напрямую для linkage без дополнительного SELECT
        // по token_hash (lookup был лишним, мы только что сами назначили
        // UUID при save)
        IssuedTokens issued = issueTokenPair(user);

        // Mark старого как replaced - rotation. markReplaced - conditional
        // UPDATE (WHERE revoked_at IS NULL), возвращает affected rows.
        //
        // Atomic single-use guard (ADR-047): два concurrent refresh с ОДНИМ
        // и тем же ещё-валидным токеном оба проходят revokedAt==null check
        // выше (READ COMMITTED не сериализует SELECT'ы), оба делают
        // issueTokenPair. Но markReplaced на одной строке сериализуется
        // row-lock'ом: loser блокируется на UPDATE, после commit winner'а
        // re-evaluate'ит WHERE → revoked_at уже NOT NULL → 0 rows. Раньше
        // return игнорировался → обе сессии получали рабочую пару (ДВЕ live
        // chain из одной ротации, обход single-use). Теперь loser (rows==0)
        // бросает → его @Transactional откатывает в т.ч. его issueTokenPair
        // INSERT → выживает ровно одна chain.
        int rotated = refreshTokenRepository.markReplaced(
                existing.id(),
                issued.refreshTokenId(),
                RefreshToken.REASON_ROTATION);
        if (rotated == 0) {
            // Проиграли гонку ротации: токен уже был ротирован concurrent
            // запросом (reason=rotation, НЕ reuse-after-revoke - chain не
            // нюкаем, это benign double-submit / retry). Откатываем нашу
            // выпущенную пару через rollback и просим повторить с новой cookie.
            log.warn("Concurrent refresh rotation race userId={} - откат проигравшей "
                    + "транзакции (single-use invariant сохранён)", existing.userId());
            throw new InvalidTokenException(
                    "Параллельная ротация токена - повторите запрос");
        }

        return issued.tokens();
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
     * hash, raw value возвращается caller'у для cookie). Expiry приходят
     * уже truncated к MICROS из {@link JwtService} (PG TIMESTAMPTZ
     * precision контракт).
     *
     * <p>Возвращает {@link IssuedTokens} - tokens + id новой refresh-записи.
     * Id нужен caller'у для linkage (chain rotation в {@link #refresh}) -
     * избавляет от лишнего SELECT по token_hash после save.
     */
    private IssuedTokens issueTokenPair(User user) {
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        Instant accessExpiry = jwtService.accessTokenExpiry();
        Instant refreshExpiry = jwtService.refreshTokenExpiry();

        UUID refreshId = UUID.randomUUID();
        RefreshToken record = new RefreshToken(
                refreshId,
                user.id(),
                sha256(refresh),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                refreshExpiry,
                null, null, null
        );
        refreshTokenRepository.save(record);

        return new IssuedTokens(
                new AuthTokens(access, accessExpiry, refresh, refreshExpiry),
                refreshId);
    }

    /**
     * Внутренний holder для возврата tokens + id новой refresh-записи из
     * {@link #issueTokenPair}. Public AuthTokens API не меняется - id
     * (внутренний идентификатор БД) не должен утекать клиенту.
     */
    private record IssuedTokens(AuthTokens tokens, UUID refreshTokenId) {}

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
