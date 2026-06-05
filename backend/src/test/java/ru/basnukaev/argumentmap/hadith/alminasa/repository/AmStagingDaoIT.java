package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmAmbiguousRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmCommentaryRow;
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
    @Autowired private AmCommentaryStagingDao commentaryDao;
    @Autowired private AmAmbiguousStagingDao ambiguousDao;
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

    /**
     * Живой урок dev-краулинга (Сессия 56): hadith_serial_id — номер ВНУТРИ
     * сборника, НЕ глобальный (12 доков с serial=1, по одному на сборник).
     * Миграция 73 сняла UNIQUE с serial — хадисы разных сборников с
     * одинаковым serial обязаны стейджиться оба.
     */
    @Test
    void hadith_с_одинаковым_serial_разных_сборников_оба_вставляются() {
        hadithDao.upsertAll(List.of(
                new AmHadithRow("121-1", 121, 1, "مسند أحمد بن حنبل", "مرفوع", null, null, "{}"),
                new AmHadithRow("146-1", 146, 1, "صحيح البخاري", "مرفوع", null, null, "{}")));

        assertThat(hadithDao.count()).isEqualTo(2);
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

    // ── AmHadithStagingDao read-методы ────────────────────────────────────────

    @Test
    void hadith_findPage_с_начала_возвращает_limit_строк() {
        hadithDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1, "صحيح البخاري", "مرفوع", null, null, "{}"),
                new AmHadithRow("146-2", 146, 2, "صحيح البخاري", "مرفوع", null, null, "{}"),
                new AmHadithRow("158-1", 158, 1, "صحيح مسلم",   "مرفوع", null, null, "{}")));

        List<AmHadithRow> page = hadithDao.findPage(null, null, 2);

        assertThat(page).hasSize(2);
        assertThat(page.get(0).hadithId()).isEqualTo("146-1");
        assertThat(page.get(1).hadithId()).isEqualTo("146-2");
    }

    @Test
    void hadith_findPage_keyset_через_границу_book_id() {
        // book 146: serial 1, 2; book 158: serial 1
        hadithDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1, "صحيح البخاري", "مرفوع", null, null, "{}"),
                new AmHadithRow("146-2", 146, 2, "صحيح البخاري", "مرفوع", null, null, "{}"),
                new AmHadithRow("158-1", 158, 1, "صحيح مسلم",   "مرفوع", null, null, "{}")));

        // первая страница с начала — 2 строки
        List<AmHadithRow> page1 = hadithDao.findPage(null, null, 2);
        assertThat(page1).extracting(AmHadithRow::hadithId)
                .containsExactly("146-1", "146-2");

        // вторая страница keyset (146, 2) — 1 строка из book 158
        AmHadithRow last = page1.get(1);
        List<AmHadithRow> page2 = hadithDao.findPage(last.bookId(), last.hadithSerialId(), 2);
        assertThat(page2).extracting(AmHadithRow::hadithId)
                .containsExactly("158-1");

        // третья страница — пусто
        AmHadithRow last2 = page2.get(0);
        List<AmHadithRow> page3 = hadithDao.findPage(last2.bookId(), last2.hadithSerialId(), 2);
        assertThat(page3).isEmpty();
    }

    @Test
    void hadith_findById_hit_и_miss() {
        hadithDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1, "صحيح البخاري", "مرفوع", "باب", null, "{\"x\":1}")));

        Optional<AmHadithRow> hit = hadithDao.findById("146-1");
        assertThat(hit).isPresent();
        assertThat(hit.get().bookId()).isEqualTo(146);
        assertThat(hit.get().hadithSerialId()).isEqualTo(1L);
        assertThat(hit.get().chapter()).isEqualTo("باب");
        // raw::text должен вернуть валидный JSON
        assertThat(hit.get().rawJson()).contains("x");

        assertThat(hadithDao.findById("999-999")).isEmpty();
    }

    @Test
    void hadith_countByBookId() {
        hadithDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1, null, null, null, null, "{}"),
                new AmHadithRow("146-2", 146, 2, null, null, null, null, "{}"),
                new AmHadithRow("158-1", 158, 1, null, null, null, null, "{}")));

        Map<Integer, Long> counts = hadithDao.countByBookId();

        assertThat(counts).containsEntry(146, 2L);
        assertThat(counts).containsEntry(158, 1L);
        assertThat(counts).doesNotContainKey(999);
    }

    // ── AmNarratorStagingDao read-методы ──────────────────────────────────────

    @Test
    void narrator_findPage_keyset_по_pk() {
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(100, "رَاوٍ أ", "ثقة", "الثانية", "{}"),
                new AmNarratorRow(200, "رَاوٍ ب", "صدوق", "الثالثة", "{}"),
                new AmNarratorRow(300, "رَاوٍ ج", "ضعيف", "الرابعة", "{}")));

        // с начала, limit 2
        List<AmNarratorRow> page1 = narratorDao.findPage(null, 2);
        assertThat(page1).extracting(AmNarratorRow::narratorId).containsExactly(100L, 200L);

        // keyset от 200
        List<AmNarratorRow> page2 = narratorDao.findPage(200L, 2);
        assertThat(page2).extracting(AmNarratorRow::narratorId).containsExactly(300L);

        // дальше — пусто
        assertThat(narratorDao.findPage(300L, 2)).isEmpty();
    }

    @Test
    void narrator_findById_hit_и_miss() {
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", "{\"k\":2}")));

        Optional<AmNarratorRow> hit = narratorDao.findById(5719);
        assertThat(hit).isPresent();
        assertThat(hit.get().fullName()).isEqualTo("علقمة بن وقاص العتواري");
        assertThat(hit.get().grade()).isEqualTo("ثقة ثبت");
        assertThat(hit.get().rawJson()).contains("k");

        assertThat(narratorDao.findById(9999999L)).isEmpty();
    }

    // ── AmRulingStagingDao / AmExplanationStagingDao findByHadithId ───────────

    @Test
    void ruling_findByHadithId_фильтрует_по_hadith_id() {
        rulingDao.upsertAll(List.of(
                new AmRulingRow("es-r-1", "146-1", "البخاري",  256, "raw", "{}"),
                new AmRulingRow("es-r-2", "146-1", "مسلم",     261, "raw", "{}"),
                new AmRulingRow("es-r-3", "158-1", "الترمذي",  279, "raw", "{}")));

        List<AmRulingRow> rows146 = rulingDao.findByHadithId("146-1");
        assertThat(rows146).hasSize(2);
        assertThat(rows146).extracting(AmRulingRow::esId)
                .containsExactlyInAnyOrder("es-r-1", "es-r-2");

        List<AmRulingRow> rows158 = rulingDao.findByHadithId("158-1");
        assertThat(rows158).singleElement()
                .satisfies(r -> assertThat(r.ruler()).isEqualTo("الترمذي"));

        assertThat(rulingDao.findByHadithId("999-999")).isEmpty();
    }

    @Test
    void explanation_findByHadithId_фильтрует_по_hadith_id() {
        explanationDao.upsertAll(List.of(
                new AmExplanationRow("es-e-1", "146-1", "فتح الباري",    "ابن حجر",   "{}"),
                new AmExplanationRow("es-e-2", "146-1", "عمدة القاري",   "العيني",    "{}"),
                new AmExplanationRow("es-e-3", "158-1", "شرح النووي",    "النووي",    "{}")));

        List<AmExplanationRow> rows146 = explanationDao.findByHadithId("146-1");
        assertThat(rows146).hasSize(2);
        assertThat(rows146).extracting(AmExplanationRow::esId)
                .containsExactlyInAnyOrder("es-e-1", "es-e-2");

        List<AmExplanationRow> rows158 = explanationDao.findByHadithId("158-1");
        assertThat(rows158).singleElement()
                .satisfies(e -> assertThat(e.author()).isEqualTo("النووي"));

        assertThat(explanationDao.findByHadithId("999-999")).isEmpty();
    }

    // ── AmCommentaryStagingDao / AmAmbiguousStagingDao (миграция 75, План 8) ──

    @Test
    void commentary_upsert_идемпотентен_по_pk() {
        commentaryDao.upsertAll(List.of(new AmCommentaryRow(
                3491, "علل الدارقطني", "أبو الحسن الدارقطني",
                "[\"146-2\"]", "{\"commentary_text\":\"...\"}")));
        commentaryDao.upsertAll(List.of(new AmCommentaryRow(
                3491, "علل الدارقطني المحدّث", "أبو الحسن الدارقطني",
                "[\"146-2\"]", "{\"commentary_text\":\"...\"}")));

        assertThat(commentaryDao.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT book_name FROM am_staging_commentary WHERE commentary_id = 3491", String.class))
                .isEqualTo("علل الدارقطني المحدّث");
    }

    @Test
    void commentary_findByNarration_находит_по_элементу_массива() {
        // 146-2 — в narrations; 999-9 — в другом доке; джойн по элементу массива
        commentaryDao.upsertAll(List.of(
                new AmCommentaryRow(3491, "علل الدارقطني", "الدارقطني",
                        "[\"146-2\",\"454-38\"]", "{\"commentary_text\":\"a\"}"),
                new AmCommentaryRow(7777, "علل أخرى", "آخر",
                        "[\"999-9\"]", "{\"commentary_text\":\"b\"}")));

        List<AmCommentaryRow> hit = commentaryDao.findByNarration("146-2");
        assertThat(hit).singleElement()
                .satisfies(r -> assertThat(r.commentaryId()).isEqualTo(3491));

        // второй элемент того же массива тоже находит
        assertThat(commentaryDao.findByNarration("454-38")).singleElement()
                .satisfies(r -> assertThat(r.commentaryId()).isEqualTo(3491));

        // несуществующий — пусто (НЕ silent substring-match)
        assertThat(commentaryDao.findByNarration("146-200")).isEmpty();
    }

    @Test
    void ambiguous_upsert_идемпотентен_и_findByIds() {
        ambiguousDao.upsertAll(List.of(
                new AmAmbiguousRow(760182, "النهاية في غريب الحديث", "ابن الأثير",
                        "{\"explanation\":\"...\"}"),
                new AmAmbiguousRow(770632, "لسان العرب", "ابن منظور",
                        "{\"explanation\":\"...\"}")));
        // повтор того же id — count стабилен
        ambiguousDao.upsertAll(List.of(
                new AmAmbiguousRow(760182, "النهاية في غريب الحديث", "ابن الأثير",
                        "{\"explanation\":\"updated\"}")));
        assertThat(ambiguousDao.count()).isEqualTo(2);

        List<AmAmbiguousRow> found = ambiguousDao.findByIds(List.of(760182, 770632, 999999));
        assertThat(found).extracting(AmAmbiguousRow::ambiguousId)
                .containsExactlyInAnyOrder(760182, 770632);
        assertThat(ambiguousDao.findByIds(List.of())).isEmpty();
    }

    @Test
    void checkpoint_полный_жизненный_цикл() {
        assertThat(checkpointDao.find("hadith-12")).isEmpty();

        AmCrawlCheckpoint started = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(started.status()).isEqualTo(AmCrawlStatus.RUNNING);
        assertThat(started.lastSortValue()).isNull();
        assertThat(started.lastSortId()).isNull();
        assertThat(started.fetchedCount()).isZero();
        assertThat(started.startedAt()).isNotNull();

        checkpointDao.setTotalHits("hadith-12", 82596L);
        // advance — АБСОЛЮТНЫЙ счётчик + СОСТАВНОЙ курсор (serial per-book!)
        checkpointDao.advance("hadith-12", 100L, "146-100", 100);
        checkpointDao.advance("hadith-12", 200L, "158-200", 200);

        AmCrawlCheckpoint mid = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(mid.lastSortValue()).isEqualTo(200L);
        assertThat(mid.lastSortId()).isEqualTo("158-200");
        assertThat(mid.fetchedCount()).isEqualTo(200L);
        assertThat(mid.totalHits()).isEqualTo(82596L);

        checkpointDao.markPaused("hadith-12");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().status())
                .isEqualTo(AmCrawlStatus.PAUSED);

        // resume БЕЗ reset — обе компоненты курсора сохраняются
        AmCrawlCheckpoint resumed = checkpointDao.upsertRunning("hadith-12", false);
        assertThat(resumed.lastSortValue()).isEqualTo(200L);
        assertThat(resumed.lastSortId()).isEqualTo("158-200");
        assertThat(resumed.fetchedCount()).isEqualTo(200L);

        checkpointDao.markFailed("hadith-12", "boom");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().error()).isEqualTo("boom");

        checkpointDao.markCompleted("hadith-12");
        AmCrawlCheckpoint done = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(done.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(done.error()).isNull();

        // рестарт с reset — обе компоненты курсора обнуляются
        AmCrawlCheckpoint fresh = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(fresh.lastSortValue()).isNull();
        assertThat(fresh.lastSortId()).isNull();
        assertThat(fresh.fetchedCount()).isZero();
    }
}
