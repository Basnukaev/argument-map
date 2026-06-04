package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;

/**
 * Round-trip IT staging-DAO alminasa (миграция 72): upsert идемпотентен по
 * природному ключу, чекпоинт проходит полный жизненный цикл статусов.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AmStagingDaoIT {

    @Autowired private AmHadithStagingDao hadithDao;
    @Autowired private AmNarratorStagingDao narratorDao;
    @Autowired private AmExplanationStagingDao explanationDao;
    @Autowired private AmRulingStagingDao rulingDao;
    @Autowired private AmCrawlCheckpointDao checkpointDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static AmHadithRow hadith(String id, long serial, String type) {
        return new AmHadithRow(id, 146, serial, "صحيح البخاري", type,
                "باب بدء الوحي", null, "{\"hadith_id\":\"" + id + "\"}");
    }

    @Test
    void hadith_upsert_идемпотентен_и_обновляет_поля() {
        hadithDao.upsertAll(List.of(hadith("146-1", 1, "مرفوع"), hadith("146-2", 2, "مرفوع")));
        assertThat(hadithDao.count()).isEqualTo(2);

        // Повторный upsert того же ключа с другим type — строка одна, поле новое
        hadithDao.upsertAll(List.of(hadith("146-1", 1, "موقوف")));
        assertThat(hadithDao.count()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hadith_type FROM am_staging_hadith WHERE hadith_id = '146-1'", String.class))
                .isEqualTo("موقوف");
        // raw — валидный jsonb
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw->>'hadith_id' FROM am_staging_hadith WHERE hadith_id = '146-1'", String.class))
                .isEqualTo("146-1");
    }

    @Test
    void narrator_upsert_и_findAllIds() {
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", "{}"),
                new AmNarratorRow(4698, "الحميدي", "ثقة حافظ", "العاشرة", "{}")));
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", "{}")));

        assertThat(narratorDao.count()).isEqualTo(2);
        assertThat(narratorDao.findAllIds()).containsExactlyInAnyOrder(5719L, 4698L);
    }

    @Test
    void explanation_и_ruling_upsert_по_es_id() {
        explanationDao.upsertAll(List.of(new AmExplanationRow(
                "GqPGhpEBXUur4f6nXKde", "146-1", "فتح الباري", "ابن حجر العسقلاني", "{}")));
        explanationDao.upsertAll(List.of(new AmExplanationRow(
                "GqPGhpEBXUur4f6nXKde", "146-1", "فتح الباري بشرح صحيح البخاري", "ابن حجر العسقلاني", "{}")));
        assertThat(explanationDao.count()).isEqualTo(1);

        rulingDao.upsertAll(List.of(new AmRulingRow(
                "yanMhpEBXUur4f6nVw_U", "146-1", "البخاري", 256, "raw", "{}")));
        rulingDao.upsertAll(List.of(new AmRulingRow(
                "yanMhpEBXUur4f6nVw_U", "146-1", "البخاري", 256, "raw", "{}")));
        assertThat(rulingDao.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ruler_dod FROM am_staging_ruling WHERE es_id = 'yanMhpEBXUur4f6nVw_U'", Integer.class))
                .isEqualTo(256);
    }

    @Test
    void checkpoint_полный_жизненный_цикл() {
        assertThat(checkpointDao.find("hadith-12")).isEmpty();

        AmCrawlCheckpoint started = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(started.status()).isEqualTo(AmCrawlStatus.RUNNING);
        assertThat(started.lastSortValue()).isNull();
        assertThat(started.fetchedCount()).isZero();
        assertThat(started.startedAt()).isNotNull();

        checkpointDao.setTotalHits("hadith-12", 82596L);
        checkpointDao.advance("hadith-12", 100L, 100);
        checkpointDao.advance("hadith-12", 200L, 100);

        AmCrawlCheckpoint mid = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(mid.lastSortValue()).isEqualTo(200L);
        assertThat(mid.fetchedCount()).isEqualTo(200L);
        assertThat(mid.totalHits()).isEqualTo(82596L);

        checkpointDao.markPaused("hadith-12");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().status())
                .isEqualTo(AmCrawlStatus.PAUSED);

        // resume БЕЗ reset — прогресс сохраняется
        AmCrawlCheckpoint resumed = checkpointDao.upsertRunning("hadith-12", false);
        assertThat(resumed.lastSortValue()).isEqualTo(200L);
        assertThat(resumed.fetchedCount()).isEqualTo(200L);

        checkpointDao.markFailed("hadith-12", "boom");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().error()).isEqualTo("boom");

        checkpointDao.markCompleted("hadith-12");
        AmCrawlCheckpoint done = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(done.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(done.error()).isNull();

        // рестарт с reset — прогресс обнуляется
        AmCrawlCheckpoint fresh = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(fresh.lastSortValue()).isNull();
        assertThat(fresh.fetchedCount()).isZero();
    }
}
