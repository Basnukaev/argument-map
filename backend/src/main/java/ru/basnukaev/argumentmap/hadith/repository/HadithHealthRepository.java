package ru.basnukaev.argumentmap.hadith.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.HadithDataHealth;

/**
 * Аггрегатные COUNT-запросы «здоровья» данных хадис-корпуса (P1-2). Только
 * чтение — SQL живёт здесь, сервис лишь собирает DTO.
 *
 * <p>Две агрегации (по одной на сущность): один проход по hd_hadiths и один по
 * hd_narrators. Категории считаются через {@code COUNT(*) FILTER (WHERE ...)}
 * (Postgres) — все метрики из одного скана таблицы, без N отдельных запросов.
 *
 * <p>«Без иснада» / «без матна» — это отсутствие строк в дочерних таблицах
 * hd_sanads / hd_matns, поэтому считаются через {@code NOT EXISTS}-подзапросы
 * внутри того же FILTER (индексы idx_hd_sanads_hadith / idx_hd_matns_hadith).
 *
 * <p>Курацию (ADR-065 overlay) сознательно НЕ применяем: health смотрит на
 * БАЗОВЫЙ импортированный слой — именно его курирует админ.
 */
@Repository
public class HadithHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public HadithHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Счётчики недозаполненности хадисов одним сканом hd_hadiths. */
    public HadithDataHealth.Hadiths countHadithGaps() {
        return jdbcTemplate.queryForObject(
                "SELECT "
                        + "COUNT(*) AS total, "
                        + "COUNT(*) FILTER (WHERE authenticity IS NULL) AS null_authenticity, "
                        + "COUNT(*) FILTER (WHERE NOT EXISTS "
                        + "    (SELECT 1 FROM hd_sanads s WHERE s.hadith_id = h.id)) AS without_sanad, "
                        + "COUNT(*) FILTER (WHERE NOT EXISTS "
                        + "    (SELECT 1 FROM hd_matns m WHERE m.hadith_id = h.id)) AS without_matn, "
                        + "COUNT(*) FILTER (WHERE collection_id IS NULL) AS null_collection "
                        + "FROM hd_hadiths h",
                (rs, rn) -> new HadithDataHealth.Hadiths(
                        rs.getLong("total"),
                        rs.getLong("null_authenticity"),
                        rs.getLong("without_sanad"),
                        rs.getLong("without_matn"),
                        rs.getLong("null_collection")));
    }

    /** Счётчики недозаполненности рави одним сканом hd_narrators. */
    public HadithDataHealth.Narrators countNarratorGaps() {
        return jdbcTemplate.queryForObject(
                "SELECT "
                        + "COUNT(*) AS total, "
                        + "COUNT(*) FILTER (WHERE tabaqa IS NULL) AS null_tabaqa, "
                        + "COUNT(*) FILTER (WHERE reliability_grade IS NULL "
                        + "    OR reliability_grade = 'UNKNOWN') AS unknown_reliability, "
                        + "COUNT(*) FILTER (WHERE grade_text IS NULL) AS null_grade_text "
                        + "FROM hd_narrators",
                (rs, rn) -> new HadithDataHealth.Narrators(
                        rs.getLong("total"),
                        rs.getLong("null_tabaqa"),
                        rs.getLong("unknown_reliability"),
                        rs.getLong("null_grade_text")));
    }
}
