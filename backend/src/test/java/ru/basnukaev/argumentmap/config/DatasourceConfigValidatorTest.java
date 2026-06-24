package ru.basnukaev.argumentmap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit-тест fail-fast валидатора datasource-кредов (P0-3). Конструктор
 * проверяем напрямую с {@link MockEnvironment} - без Spring context.
 */
class DatasourceConfigValidatorTest {

    private static final String PROD_URL = "jdbc:postgresql://db.internal:5432/argprod";
    private static final String PROD_USER = "argprod_user";

    @Test
    void localUrlInProdProfile_failsAtStartup() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new DatasourceConfigValidator(
                prod, "jdbc:postgresql://localhost:5432/argumentmap", "argmap"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev/local");
    }

    @Test
    void devUsernameInProdProfile_failsAtStartup() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        // URL чистый, но username остался дефолтным dev-кредом
        assertThatThrownBy(() -> new DatasourceConfigValidator(prod, PROD_URL, "argmap"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("argmap");
    }

    @Test
    void prodUrlInProdProfile_doesNotFail() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        DatasourceConfigValidator v = new DatasourceConfigValidator(prod, PROD_URL, PROD_USER);
        assertThat(v).isNotNull();
    }

    @Test
    void localUrlInLocalProfile_doesNotFail() {
        // Контр-кейс: в local profile dev-креды - норма, валидатор молчит
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> new DatasourceConfigValidator(
                local, "jdbc:postgresql://localhost:5432/argumentmap", "argmap"))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyUrlInTestProfile_doesNotFail() {
        // test profile: datasource через Testcontainers, placeholder может
        // быть пуст на этом этапе - валидатор не должен падать
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        assertThatCode(() -> new DatasourceConfigValidator(test, "", ""))
                .doesNotThrowAnyException();
    }
}
