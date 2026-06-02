package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Unit-тест (без Spring/БД) для timing-protection dummy-хэша (баг #1 Tier-3).
 *
 * <p>Суть бага: прежняя константа {@code $2a$10$DummyHashForTimingProtectionOnly}
 * была синтаксически невалидным BCrypt-хэшем (соль ≠ 22 base64-символа).
 * {@link BCryptPasswordEncoder#matches} на malformed-хэше логирует warning и
 * возвращается мгновенно <b>без</b> запуска KDF - timing-protection не работает,
 * остаётся user-enumeration по времени ответа.
 *
 * <p>Тест читает реальную константу из {@link AuthService} рефлексией и
 * проверяет, что она - валидный BCrypt-хэш, против которого {@code matches}
 * прогоняет полноценный KDF (а не короткое замыкание).
 */
class AuthServiceDummyHashTest {

    // BCrypt: $2[aby]$ + cost(2) + $ + 22-символьная соль + 31-символьный хэш = 53 base64-символа
    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$\\d\\d\\$[./A-Za-z0-9]{53}$";

    @Test
    void dummyHash_isSyntacticallyValidBcrypt() throws Exception {
        String dummy = readDummyHash();
        assertThat(dummy)
                .as("dummy timing-protection hash должен быть валидным BCrypt (иначе KDF не запускается)")
                .matches(BCRYPT_PATTERN);
    }

    @Test
    void dummyHash_matchesRunsKdfAndReturnsFalse() throws Exception {
        String dummy = readDummyHash();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // Валидный хэш произвольного пароля → matches любого ввода = false,
        // но КДФ реально прогоняется (для malformed-хэша Spring вернул бы false
        // мгновенно через короткое замыкание - именно это и был timing-leak).
        assertThat(encoder.matches("any-attacker-supplied-password", dummy)).isFalse();
        // Контрпример: старый malformed-хэш НЕ матчит паттерн (фиксирует регрессию).
        assertThat("$2a$10$DummyHashForTimingProtectionOnly").doesNotMatch(BCRYPT_PATTERN);
    }

    private static String readDummyHash() throws Exception {
        Field f = AuthService.class.getDeclaredField("DUMMY_BCRYPT_HASH");
        f.setAccessible(true);
        return (String) f.get(null);
    }
}
