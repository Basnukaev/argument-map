package ru.basnukaev.argumentmap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit-тест fail-fast валидатора datasource-кредов (P0-3). Конструктор
 * проверяем напрямую с {@link MockEnvironment} - без Spring context.
 * Условную регистрацию бина ({@code app.datasource.prod-guard}) проверяем
 * через {@link ApplicationContextRunner}.
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

    // --- Условная регистрация бина (app.datasource.prod-guard) ---
    // Гард включён по умолчанию (matchIfMissing) - реальный prod защищён.
    // prod-profile IT'ы отключают его через app.datasource.prod-guard=false,
    // т.к. датасорс там из Testcontainers @ServiceConnection (localhost:<port>).

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DatasourceConfigValidator.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/argumentmap",
                    "spring.datasource.username=argmap");

    @Test
    void guardDisabled_beanNotCreated_noValidation() {
        // app.datasource.prod-guard=false → бин не создаётся, валидация не
        // запускается даже под prod profile с localhost-URL (так prod-profile
        // IT'ы загружают контекст под @ServiceConnection).
        contextRunner
                .withPropertyValues("app.datasource.prod-guard=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DatasourceConfigValidator.class));
    }

    @Test
    void guardEnabledByDefault_prodProfile_localUrl_failsContext() {
        // Без явного app.datasource.prod-guard (matchIfMissing=true) бин
        // создаётся; под prod profile localhost-URL валится - реальный prod
        // защищён от случайного коннекта к dev-БД.
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class));
    }
}
