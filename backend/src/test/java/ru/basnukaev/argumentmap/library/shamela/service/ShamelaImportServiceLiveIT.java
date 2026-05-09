package ru.basnukaev.argumentmap.library.shamela.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;

/**
 * Реальный end-to-end {@link ShamelaImportService#syncMaster()} против
 * боевого shamela API. Скачивает master-snapshot ~5MB, разбирает три
 * SQLite, выгружает {@code ~270000} книг в Postgres-Testcontainer.
 *
 * <p>Исключён из обычного {@code ./mvnw verify} через
 * {@code <excludedGroups>live</excludedGroups>} в failsafe-plugin.
 * Запуск точечный:
 *
 * <pre>./mvnw failsafe:integration-test -Dgroups=live -Dit.test=ShamelaImportServiceLiveIT</pre>
 *
 * <p>Время прогона: ~30-60с (2с на metadata + 3-8с на download + ~20с
 * на парсинг и upsert ~270k books). Если корпоративный прокси -
 * подхватывается через {@code HTTPS_PROXY} автоматически.
 */
@Tag("live")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaImportServiceLiveIT {

    @Autowired
    private ShamelaImportService service;

    @Autowired
    private ShamelaBookDao bookDao;

    @Test
    void syncMaster_imports_real_master_snapshot() {
        MasterSyncResult result = service.syncMaster();

        // bootstrap: master_version в свежей БД = 0, после sync должна
        // обновиться до текущей версии shamela (1000+)
        assertThat(result.changed()).isTrue();
        assertThat(result.previousVersion()).isZero();
        assertThat(result.currentVersion()).isGreaterThan(1000);
        // master-snapshot на момент исследования - около 270k книг,
        // лимит снизу с большим запасом
        assertThat(result.booksCount()).isGreaterThan(10_000);
        assertThat(result.authorsCount()).isGreaterThan(1_000);
        assertThat(result.categoriesCount()).isGreaterThan(10);

        // sanity-check на запись в Postgres - не упало в идемпотентности
        assertThat(bookDao.findAll()).isNotEmpty();
    }
}
