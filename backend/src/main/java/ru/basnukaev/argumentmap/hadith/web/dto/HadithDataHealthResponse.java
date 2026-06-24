package ru.basnukaev.argumentmap.hadith.web.dto;

/**
 * Снапшот «здоровья» данных хадис-корпуса (P1-2, PROD-READINESS-AUDIT §4/§7):
 * счётчики записей с пробелами, которые нужно докурировать. Админ видит ЧТО
 * чинить, не зная заранее объёма проблемы.
 *
 * <p>Поля сгруппированы по сущности:
 * <ul>
 *   <li>хадисы: всего + 4 категории недозаполненности
 *       (нет оси достоверности / нет иснада / нет матна / нет сборника);
 *   <li>рави: всего + 3 категории
 *       (нет табака / надёжность UNKNOWN-или-NULL / нет verbatim джарх-таʿдиль).
 * </ul>
 *
 * @param totalHadiths всего хадисов в hd_hadiths
 * @param hadithsNullAuthenticity authenticity IS NULL (ось достоверности не выведена)
 * @param hadithsWithoutSanad нет ни одной цепи в hd_sanads
 * @param hadithsWithoutMatn нет ни одного текста в hd_matns
 * @param hadithsNullCollection collection_id IS NULL (хадис не привязан к сборнику)
 * @param totalNarrators всего рави в hd_narrators
 * @param narratorsNullTabaqa tabaqa IS NULL (поколение не указано)
 * @param narratorsUnknownReliability reliability_grade IS NULL ИЛИ 'UNKNOWN'
 * @param narratorsNullGradeText grade_text IS NULL (нет verbatim джарх-таʿдиль)
 */
public record HadithDataHealthResponse(
        long totalHadiths,
        long hadithsNullAuthenticity,
        long hadithsWithoutSanad,
        long hadithsWithoutMatn,
        long hadithsNullCollection,
        long totalNarrators,
        long narratorsNullTabaqa,
        long narratorsUnknownReliability,
        long narratorsNullGradeText
) {
}
