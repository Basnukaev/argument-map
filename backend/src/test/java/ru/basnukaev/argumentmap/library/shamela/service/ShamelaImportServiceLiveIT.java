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
 * Реальный end-to-end {@link ShamelaMasterSyncService#syncMaster()}
 * против боевого shamela API. Скачивает master-snapshot ~5MB, разбирает
 * три SQLite, выгружает {@code ~8500} книг в Postgres-Testcontainer.
 *
 * <p>Исключён из обычного {@code ./mvnw verify} через
 * {@code <excludedGroups>live</excludedGroups>} в failsafe-plugin.
 * Запуск точечный:
 *
 * <pre>./mvnw failsafe:integration-test -Dgroups=live -Dit.test=ShamelaImportServiceLiveIT</pre>
 *
 * <p>Время прогона: ~10-20с (2с на metadata + 3-8с на download + ~5с
 * на парсинг и upsert ~8500 books). Если корпоративный прокси -
 * подхватывается через {@code HTTPS_PROXY} автоматически.
 */
@Tag("live")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaImportServiceLiveIT {

    @Autowired
    private ShamelaMasterSyncService masterSyncService;

    @Autowired
    private ShamelaBookDao bookDao;

    @Test
    void syncMaster_imports_real_master_snapshot() {
        MasterSyncResult result = masterSyncService.syncMaster();

        // bootstrap: master_version в свежей БД = 0, после sync должна
        // обновиться до текущей версии shamela (1000+)
        assertThat(result.changed()).isTrue();
        assertThat(result.previousVersion()).isZero();
        assertThat(result.currentVersion()).isGreaterThan(1000);
        // master-snapshot на момент исследования - около 8500 книг,
        // лимит снизу с большим запасом (если каталог сильно вырастет
        // или урежется - тест переписать)
        assertThat(result.booksCount()).isGreaterThan(5_000);
        assertThat(result.authorsCount()).isGreaterThan(1_000);
        assertThat(result.categoriesCount()).isGreaterThan(10);

        // sanity-check на запись в Postgres - не упало в идемпотентности
        assertThat(bookDao.findAll()).isNotEmpty();
    }
}
