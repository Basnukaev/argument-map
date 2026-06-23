package ru.basnukaev.argumentmap.hadith.curation.domain;

import java.util.Map;
import java.util.Set;

import ru.basnukaev.argumentmap.hadith.domain.HadithAuthenticity;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;

/**
 * Источник истины «что можно править/скрывать» по каждой hd_*-сущности
 * (ADR-065 §5). Декларативная защита первоисточника: текст
 * откровения/предания/цитаты ({@code normalized_matn}, {@code full_text_ar},
 * {@code text_ar}, commentary {@code comments}) просто не входит в
 * editable-набор → PATCH с ним отвергается (400), а не блокируется схемой.
 *
 * <p>Принцип (§5): РЕДАКТИРУЕМО = метаданные/классификации/атрибуции, где
 * ошибка импорта реальна; СКРЫВАЕМО = вторичные суждения/имена (модерация);
 * ЗАПРЕЩЕНО = первоисточник. Валидируется на каждом PATCH override.
 */
public final class CurationWhitelist {

    /**
     * Правила курации одной сущности.
     *
     * @param editable        поля, которым разрешена правка значения
     * @param hideableFields  поля, которым разрешено поле-уровневое скрытие
     *                        ({@code hidden=true} на колонке → apply отдаёт null)
     * @param recordHideAllowed разрешено ли скрытие всей записи
     *                        ({@code field_name='__record__'} → запись вырезается)
     * @param enumOptions     для enum-полей — допустимый whitelist значений
     *                        (валидация {@code value} на ЗАПИСИ, §3.4)
     */
    public record EntityRules(
            Set<String> editable,
            Set<String> hideableFields,
            boolean recordHideAllowed,
            Map<String, Set<String>> enumOptions
    ) {
    }

    private static final Map<OverrideEntity, EntityRules> RULES = Map.of(
            OverrideEntity.HD_HADITHS, new EntityRules(
                    Set.of("status", "authenticity", "primary_number",
                            "hadith_type", "chapter_ar", "sub_chapter_ar"),
                    Set.of(),                 // хадис целиком не скрывается (§5)
                    false,
                    Map.of("status", HadithStatus.ALL,
                            "authenticity", HadithAuthenticity.ALL)),

            OverrideEntity.HD_MATNS, new EntityRules(
                    // перевод text_ru/en — наш контент, не первоисточник (§5);
                    // text_ar / text_ar_normalized сознательно вне набора
                    Set.of("printed_number", "page_no", "volume",
                            "divergence_summary", "text_ru", "text_en"),
                    Set.of(),
                    true,                     // скрыть вариацию матна целиком
                    Map.of()),

            OverrideEntity.HD_NARRATORS, new EntityRules(
                    Set.of("reliability_grade", "tabaqa", "grade_text", "name_ar",
                            "kunya", "laqab", "year_birth_hijri", "year_death_hijri",
                            "birthplace", "death_place", "primary_residence",
                            "reliability_comment"),
                    Set.of("grade_text", "reliability_comment"),  // спорный джарх
                    false,                    // рави целиком не скрывается (§5)
                    Map.of("reliability_grade", NarratorReliability.ALL)),

            OverrideEntity.HD_SANADS, new EntityRules(
                    Set.of("chain_grade", "primary_chain"),
                    Set.of(),
                    true,                     // скрыть слабую цепь целиком
                    Map.of()),

            OverrideEntity.HD_SANAD_NARRATORS, new EntityRules(
                    // структуру цепи (narrator_id/position) overlay не меняет (§5)
                    Set.of("transmission_phrase"),
                    Set.of("transmission_phrase"),
                    false,
                    Map.of()),

            OverrideEntity.HD_RULINGS, new EntityRules(
                    Set.of("ruler_name", "ruler_death_year", "ruling_text",
                            "book_name", "page", "volume"),
                    Set.of("ruler_name"),     // имя одиозного «учёного»
                    true,
                    Map.of()),

            OverrideEntity.HD_EXPLANATIONS, new EntityRules(
                    Set.of("book_name", "author", "author_death_year",
                            "page", "volume", "text"),
                    Set.of("author", "text"), // экстремистский шарх
                    true,
                    Map.of()),

            OverrideEntity.HD_NARRATOR_COMMENTARIES, new EntityRules(
                    // comments (jsonb verbatim-цитаты) — первоисточник риджаль-
                    // книги, не правится; только скрытие записи целиком (§5)
                    Set.of("commenter", "commenter_death_year", "book_name",
                            "author", "page", "volume"),
                    Set.of("commenter"),      // заблудший критик
                    true,
                    Map.of())
    );

    private CurationWhitelist() {
    }

    public static EntityRules rules(OverrideEntity entity) {
        return RULES.get(entity);
    }

    /** Разрешена ли правка значения поля {@code field} у сущности. */
    public static boolean isEditable(OverrideEntity entity, String field) {
        return RULES.get(entity).editable().contains(field);
    }

    /** Разрешено ли поле-уровневое скрытие колонки {@code field}. */
    public static boolean isFieldHideable(OverrideEntity entity, String field) {
        return RULES.get(entity).hideableFields().contains(field);
    }

    /** Разрешено ли скрытие всей записи ({@code __record__}). */
    public static boolean isRecordHideAllowed(OverrideEntity entity) {
        return RULES.get(entity).recordHideAllowed();
    }

    /**
     * Разрешено ли скрытие для данного {@code field}: для
     * {@link FieldOverride#RECORD_FIELD} — запись-уровень, иначе поле-уровень.
     */
    public static boolean isHideAllowed(OverrideEntity entity, String field) {
        if (FieldOverride.RECORD_FIELD.equals(field)) {
            return isRecordHideAllowed(entity);
        }
        return isFieldHideable(entity, field);
    }

    /**
     * Whitelist значений enum-поля (для валидации {@code value} на записи).
     * Пустой набор — поле не enum-ограничено (свободный текст/число).
     */
    public static Set<String> enumOptions(OverrideEntity entity, String field) {
        return RULES.get(entity).enumOptions().getOrDefault(field, Set.of());
    }
}
