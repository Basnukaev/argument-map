package ru.basnukaev.argumentmap.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Генерация и валидация JWT (ADR-040). HS256 single-key signature.
 * Token type различается через {@code typ} claim - "access" / "refresh".
 *
 * <p>Secret должен быть минимум 256 бит (32 байта). В prod через env
 * AUTH_JWT_SECRET, в dev placeholder из application.yml (заведомо длинее
 * 32 символов).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String ISSUER = "argument-map";

    private final SecretKey signingKey;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    /**
     * Sentinel-фрагмент в default placeholder в application.yml. Если
     * deploy в prod забыл выставить env AUTH_JWT_SECRET - secret будет
     * содержать эту подстроку, fail-fast в constructor (cross-cutting
     * audit finding #6).
     */
    private static final String DEV_PLACEHOLDER_MARKER = "dev-only";

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-token-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${auth.jwt.refresh-token-ttl-days:7}") long refreshTtlDays,
            Environment environment
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "auth.jwt.secret должен быть минимум 256 бит (32 байта UTF-8), получено "
                            + secretBytes.length + ". В prod использовать env AUTH_JWT_SECRET."
            );
        }
        // Fail-fast если в prod profile активен default dev placeholder.
        // Защита от deploy-mistake (забыли AUTH_JWT_SECRET env-var).
        boolean isProd = false;
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production")) {
                isProd = true;
                break;
            }
        }
        if (isProd && secret.contains(DEV_PLACEHOLDER_MARKER)) {
            throw new IllegalStateException(
                    "auth.jwt.secret содержит dev-placeholder '" + DEV_PLACEHOLDER_MARKER
                            + "' в prod profile. Установить AUTH_JWT_SECRET env-var "
                            + "сгенерированный через `openssl rand -hex 32`."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
        log.info("JwtService initialized: accessTtl={}min, refreshTtl={}d", accessTtlMinutes, refreshTtlDays);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
        return buildToken(user, TYPE_ACCESS, now, expiresAt);
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTtlDays, ChronoUnit.DAYS);
        return buildToken(user, TYPE_REFRESH, now, expiresAt);
    }

    public Instant accessTokenExpiry() {
        return Instant.now().plus(accessTtlMinutes, ChronoUnit.MINUTES);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
    }

    public long refreshTokenTtlSeconds() {
        return refreshTtlDays * 24L * 60L * 60L;
    }

    /**
     * Валидирует токен и возвращает principal. Не проверяет тип токена
     * (access/refresh) - вызывающий код решает по контексту (фильтр vs
     * refresh endpoint). Бросает {@link InvalidTokenException} на любую
     * ошибку - в т.ч. expired.
     */
    public AuthenticatedUser validateToken(String token) {
        Claims claims = parseClaims(token);
        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("subject не UUID: " + claims.getSubject());
        }
        return new AuthenticatedUser(
                userId,
                (String) claims.get(CLAIM_USERNAME),
                (String) claims.get(CLAIM_EMAIL),
                (String) claims.get(CLAIM_ROLE)
        );
    }

    /**
     * Проверяет тип токена. Используется на refresh endpoint -
     * нельзя refresh access-токеном.
     */
    public String extractTokenType(String token) {
        Claims claims = parseClaims(token);
        return (String) claims.get(CLAIM_TYPE);
    }

    private String buildToken(User user, String type, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.id().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claims(Map.of(
                        CLAIM_TYPE, type,
                        CLAIM_USERNAME, user.username(),
                        CLAIM_EMAIL, user.email(),
                        CLAIM_ROLE, user.role()
                ))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException("Токен истёк");
        } catch (JwtException | IllegalArgumentException ex) {
            // SecurityException / MalformedJwtException / UnsupportedJwtException
            // все наследуют JwtException. IllegalArgumentException - пустой / null token.
            throw new InvalidTokenException("Невалидный токен: " + ex.getMessage());
        }
    }
}
