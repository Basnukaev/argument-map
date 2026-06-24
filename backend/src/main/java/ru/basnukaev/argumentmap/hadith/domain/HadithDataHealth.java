package ru.basnukaev.argumentmap.hadith.domain;

/**
 * Доменный носитель счётчиков «здоровья» данных (P1-2): результат двух
 * аггрегатных запросов репозитория. Отделён от web-DTO
 * {@code HadithDataHealthResponse}, чтобы repository-слой не зависел от web —
 * сервис мостит две части в плоский ответ.
 */
public final class HadithDataHealth {

    private HadithDataHealth() {
    }

    /**
     * Счётчики недозаполненности хадисов.
     *
     * @param total всего хадисов
     * @param nullAuthenticity authenticity IS NULL
     * @param withoutSanad нет ни одной цепи в hd_sanads
     * @param withoutMatn нет ни одного текста в hd_matns
     * @param nullCollection collection_id IS NULL
     */
    public record Hadiths(
            long total,
            long nullAuthenticity,
            long withoutSanad,
            long withoutMatn,
            long nullCollection
    ) {
    }

    /**
     * Счётчики недозаполненности рави.
     *
     * @param total всего рави
     * @param nullTabaqa tabaqa IS NULL
     * @param unknownReliability reliability_grade IS NULL ИЛИ 'UNKNOWN'
     * @param nullGradeText grade_text IS NULL
     */
    public record Narrators(
            long total,
            long nullTabaqa,
            long unknownReliability,
            long nullGradeText
    ) {
    }
}
