package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaDryRunResult;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

/**
 * IT dry-run-маппинга (план 3, Task 4): метод {@code @Transactional} с
 * {@code setRollbackOnly} — проверяем недеструктивность. Класс намеренно
 * <b>БЕЗ</b> {@code @Transactional}: иначе {@code setRollbackOnly} пометил бы
 * на откат разделяемую транзакцию теста, и проверить откат было бы невозможно
 * (запись осталась бы видна в той же транзакции). Чистим staging вручную
 * в {@link #cleanup()}; продукты dry-run откатываются сами.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlminasaDryRunIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private AmHadithStagingDao hadithStagingDao;
    @Autowired private AmNarratorStagingDao narratorStagingDao;
    @Autowired private AmRulingStagingDao rulingStagingDao;
    @Autowired private AmExplanationStagingDao explanationStagingDao;
    @Autowired private AlminasaNarratorMapper narratorMapper;
    @Autowired private AlminasaHadithMapper hadithMapper;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + name)) {
            return MAPPER.readTree(in);
        }
    }

    @BeforeEach
    void seed() throws IOException {
        JsonNode src = fixture("hadith-page.json").path("hits").path("hits").get(0).path("_source");
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-1", 146, 1L, "صحيح البخاري", "مرفوع",
                "باب بدء الوحي", "باب كيف كان بدء الوحي إلى رسول الله", src.toString())));

        JsonNode narratorSrc = fixture("narrators.json")
                .path("hits").path("hits").get(0).path("_source");
        narratorStagingDao.upsertAll(List.of(new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorSrc.toString())));

        for (JsonNode hit : fixture("rulings.json").path("hits").path("hits")) {
            JsonNode rs = hit.path("_source");
            rulingStagingDao.upsertAll(List.of(new AmRulingRow(
                    hit.path("_id").asText(), rs.path("hadith_id").asText(),
                    rs.path("ruler").asText(), rs.path("ruler_dod").asInt(),
                    rs.path("narrations_type").asText(), rs.toString())));
        }

        for (JsonNode hit : fixture("explanations.json").path("hits").path("hits")) {
            JsonNode es = hit.path("_source");
            if ("146-1".equals(es.path("hadith").path("hadith_id").asText())) {
                explanationStagingDao.upsertAll(List.of(new AmExplanationRow(
                        hit.path("_id").asText(), "146-1",
                        es.path("explanation").path("explanation_book_name").asText(),
                        es.path("explanation").path("explanation_book_author").asText(),
                        es.toString())));
            }
        }
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM am_staging_explanation WHERE hadith_id = '146-1'");
        jdbcTemplate.update("DELETE FROM am_staging_ruling WHERE hadith_id = '146-1'");
        jdbcTemplate.update("DELETE FROM am_staging_narrator WHERE narrator_id = 5719");
        jdbcTemplate.update("DELETE FROM am_staging_hadith WHERE hadith_id = '146-1'");
    }

    @Test
    void dryRunHadith_возвращает_снапшот_и_откатывает_запись() {
        AlminasaDryRunResult result = hadithMapper.dryRunHadith("146-1");

        assertThat(result.externalId()).isEqualTo("146-1");
        assertThat(result.collectionSlug()).isEqualTo("bukhari");
        assertThat(result.status()).isEqualTo("CANONICAL");
        assertThat(result.hadithType()).isEqualTo("مرفوع");
        assertThat(result.primaryNumber()).isEqualTo(1);
        assertThat(result.matnPreview()).isNotBlank();
        assertThat(result.sanad()).hasSize(6);
        assertThat(result.sanad().get(0).position()).isZero();
        assertThat(result.sanad().get(0).externalId()).isEqualTo("5913");
        assertThat(result.editionsCount()).isEqualTo(2);
        assertThat(result.rulingsCount()).isEqualTo(2);
        assertThat(result.explanationsCount()).isEqualTo(1);

        // откат: ни хадис, ни рави (даже 5719) НЕ закоммичены
        assertThat(hadithRepository.findByExternalId("alminasa", "146-1")).isEmpty();
        assertThat(narratorRepository.findByExternalId("alminasa", "5913")).isEmpty();
    }
}
