package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;

import io.jsonwebtoken.security.Keys;
import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JwtServiceIT {

    @Autowired
    private JwtService jwtService;

    private User sampleUser() {
        Instant now = Instant.now();
        return new User(
                UUID.randomUUID(),
                "test_user",
                "test@example.com",
                "$2a$10$DummyHash",
                UserRole.USER,
                true,
                now, now
        );
    }

    @Test
    void generateAndValidateAccessToken_roundTrip_works() {
        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);

        AuthenticatedUser principal = jwtService.validateToken(token);
        assertThat(principal.id()).isEqualTo(user.id());
        assertThat(principal.username()).isEqualTo(user.username());
        assertThat(principal.email()).isEqualTo(user.email());
        assertThat(principal.role()).isEqualTo(user.role());

        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TYPE_ACCESS);
    }

    @Test
    void generateRefreshToken_hasRefreshType() {
        User user = sampleUser();
        String token = jwtService.generateRefreshToken(user);
        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TYPE_REFRESH);
    }

    @Test
    void validateToken_tamperedSignature_throws() {
        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);

        // подменим последний символ signature - токен станет невалидным
        String tampered = token.substring(0, token.length() - 2)
                + (token.charAt(token.length() - 2) == 'A' ? "B" : "A")
                + token.charAt(token.length() - 1);

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateToken_garbage_throws() {
        assertThatThrownBy(() -> jwtService.validateToken("not-a-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateToken_wrongSigningKey_throws() {
        // Самодельный токен с другим секретом - signature не пройдёт
        // против нашего ключа в JwtService
        SecretKey other = Keys.hmacShaKeyFor(
                "another-completely-different-secret-key-bytes-32-min"
                        .getBytes(StandardCharsets.UTF_8)
        );
        String foreign = io.jsonwebtoken.Jwts.builder()
                .issuer(JwtService.ISSUER)
                .subject(UUID.randomUUID().toString())
                .signWith(other, io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validateToken(foreign))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shortSecret_failsAtStartup() {
        // Конструктор должен бросить - проверяем напрямую
        MockEnvironment env = new MockEnvironment();
        assertThatThrownBy(() -> new JwtService("too-short", 15, 7, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("минимум 256 бит");
    }

    @Test
    void devPlaceholderSecret_inProdProfile_failsAtStartup() {
        // Cross-cutting audit fix #6: deploy в prod без AUTH_JWT_SECRET
        // получает default placeholder из application.yml. Если placeholder
        // содержит "dev-only" - fail-fast в constructor (не молча работаем
        // с известным ключом)
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        String devSecret = "dev-only-do-not-use-in-prod-at-least-256-bits-required-for-hs256-min-32-chars";
        assertThatThrownBy(() -> new JwtService(devSecret, 15, 7, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-placeholder")
                .hasMessageContaining("AUTH_JWT_SECRET");
    }

    @Test
    void devPlaceholderSecret_inDevProfile_doesNotFail() {
        // Контр-кейс - в local/test profile placeholder допустим
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("local");
        String devSecret = "dev-only-do-not-use-in-prod-at-least-256-bits-required-for-hs256-min-32-chars";
        // не бросает - конструктор работает
        JwtService js = new JwtService(devSecret, 15, 7, dev);
        assertThat(js).isNotNull();
    }

    @Test
    void productionSecretInProdProfile_doesNotFail() {
        // Реальный prod-deploy с AUTH_JWT_SECRET генерированным через
        // openssl rand -hex 32 - не должен fail'ить
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        String realSecret = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
        JwtService js = new JwtService(realSecret, 15, 7, prod);
        assertThat(js).isNotNull();
    }

    /**
     * Эксплуатируем рефлексию - в JwtService нет API для генерации
     * истёкших токенов. Поднимаем второй JwtService с тем же секретом,
     * напрямую строим токен с expiration в прошлом, валидируем
     * через основной bean.
     */
    @Test
    void validateToken_expired_throws() throws Exception {
        Field secretField = JwtService.class.getDeclaredField("signingKey");
        secretField.setAccessible(true);
        SecretKey key = (SecretKey) secretField.get(jwtService);

        Instant past = Instant.now().minusSeconds(120);
        String expired = io.jsonwebtoken.Jwts.builder()
                .issuer(JwtService.ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(java.util.Date.from(past.minusSeconds(60)))
                .expiration(java.util.Date.from(past))
                .signWith(key, io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validateToken(expired))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("истёк");
    }
}
