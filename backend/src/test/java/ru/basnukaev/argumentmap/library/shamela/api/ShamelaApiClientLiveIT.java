package ru.basnukaev.argumentmap.library.shamela.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;

/**
 * Интеграционный тест против реального shamela API. Исключён из
 * обычного {@code ./mvnw verify} через
 * {@code <excludedGroups>live</excludedGroups>} в failsafe-plugin
 * (см. pom.xml). Запуск точечный:
 *
 * <pre>./mvnw failsafe:integration-test -Dgroups=live</pre>
 *
 * <p>Требует интернет и доступ к {@code dev.shamela.ws}/{@code ready.shamela.ws}.
 * Если сеть за корпоративным прокси - {@code HTTPS_PROXY} env-var в shell
 * подхватится автоматически (см. {@link ShamelaHttpClientConfig}).
 *
 * <p>Использует local-профиль (datasource по дефолту) - тест не требует
 * Postgres, но ApplicationContext грузится целиком ради
 * {@link ShamelaApiClient} bean'а. В будущем при росте таких тестов
 * можно ускорить через @WebMvcTest-подобную узкую конфигурацию.
 */
@Tag("live")
@SpringBootTest
@ActiveProfiles("local")
class ShamelaApiClientLiveIT {

    @Autowired
    private ShamelaApiClient client;

    @Test
    void fetchMasterMetadata_full_snapshot_returns_real_url() {
        // version=0 - запрос полного snapshot (не дельты)
        MasterMetadata meta = client.fetchMasterMetadata(0);

        assertThat(meta.version()).isPositive();
        assertThat(meta.patchUrl()).isNotBlank();
        assertThat(meta.patchUrl()).startsWith("https://");
        // у URL должен быть зашит api_key (потому что host для метаданных требует его)
        assertThat(meta.patchUrl()).contains("api_key=");
        // имя файла соответствует master-{from}-{to}.zip
        assertThat(meta.patchUrl()).contains("master-");
        assertThat(meta.patchUrl()).contains(".zip");
    }

    @Test
    void downloadArchive_master_zip_actually_downloads(@TempDir Path tmp) throws Exception {
        // получаем URL и сразу качаем - проверяем что весь pipeline
        // (DNS, TCP, прокси, CDN) работает end-to-end
        MasterMetadata meta = client.fetchMasterMetadata(0);

        Path archive = client.downloadArchive(URI.create(meta.patchUrl()), tmp);

        assertThat(archive).isRegularFile();
        // master-snapshot имеет ~5MB на момент исследования - sanity-check на
        // ненулевой и реалистичный размер (>100KB, чтобы не спутать с error-page)
        long size = Files.size(archive);
        assertThat(size).isGreaterThan(100_000L);
        // первые 4 байта zip-сигнатуры PK\003\004
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(archive)) {
            assertThat(in.read(head)).isEqualTo(4);
        }
        assertThat(head[0]).isEqualTo((byte) 'P');
        assertThat(head[1]).isEqualTo((byte) 'K');
    }
}
