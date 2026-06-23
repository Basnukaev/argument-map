package ru.basnukaev.argumentmap.hadith.curation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit-тесты whitelist'а курации (ADR-065 §5). Главное — НЕГАТИВНАЯ
 * проверка: первоисточник (текст матна/предания/verbatim-цитаты) НЕ
 * редактируем. Если кто-то ошибочно добавит {@code full_text_ar} в editable —
 * этот тест покраснеет.
 */
class CurationWhitelistTest {

    @Test
    void hadithMetadataEditable_butFirstSourceProtected() {
        var e = OverrideEntity.HD_HADITHS;
        assertThat(CurationWhitelist.isEditable(e, "authenticity")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "status")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "primary_number")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "chapter_ar")).isTrue();
        // первоисточник — запрещён
        assertThat(CurationWhitelist.isEditable(e, "normalized_matn")).isFalse();
        assertThat(CurationWhitelist.isEditable(e, "full_text_ar")).isFalse();
        // хадис целиком не скрывается
        assertThat(CurationWhitelist.isRecordHideAllowed(e)).isFalse();
    }

    @Test
    void matnTranslationEditable_butArabicTextProtected() {
        var e = OverrideEntity.HD_MATNS;
        assertThat(CurationWhitelist.isEditable(e, "text_ru")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "text_en")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "divergence_summary")).isTrue();
        // арабский матн — первоисточник
        assertThat(CurationWhitelist.isEditable(e, "text_ar")).isFalse();
        assertThat(CurationWhitelist.isEditable(e, "text_ar_normalized")).isFalse();
        // вариацию можно скрыть целиком
        assertThat(CurationWhitelist.isRecordHideAllowed(e)).isTrue();
    }

    @Test
    void narratorFieldsEditable_andDisputedJarhHideable_noRecordHide() {
        var e = OverrideEntity.HD_NARRATORS;
        assertThat(CurationWhitelist.isEditable(e, "reliability_grade")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "tabaqa")).isTrue();
        assertThat(CurationWhitelist.isEditable(e, "name_ar")).isTrue();
        // поле-уровневое скрытие спорного джарха
        assertThat(CurationWhitelist.isFieldHideable(e, "grade_text")).isTrue();
        assertThat(CurationWhitelist.isFieldHideable(e, "reliability_comment")).isTrue();
        assertThat(CurationWhitelist.isFieldHideable(e, "name_ar")).isFalse();
        // рави целиком не скрывается
        assertThat(CurationWhitelist.isRecordHideAllowed(e)).isFalse();
    }

    @Test
    void commentaryVerbatimQuotesProtected_butRecordHideable() {
        var e = OverrideEntity.HD_NARRATOR_COMMENTARIES;
        // comments (jsonb verbatim) — первоисточник риджаль-книги
        assertThat(CurationWhitelist.isEditable(e, "comments")).isFalse();
        assertThat(CurationWhitelist.isEditable(e, "commenter")).isTrue();
        // заблудшего критика можно скрыть целиком
        assertThat(CurationWhitelist.isRecordHideAllowed(e)).isTrue();
        assertThat(CurationWhitelist.isFieldHideable(e, "commenter")).isTrue();
    }

    @Test
    void enumOptions_exposeWhitelistForEnumFields_emptyForFreeText() {
        assertThat(CurationWhitelist.enumOptions(OverrideEntity.HD_HADITHS, "authenticity"))
                .contains("SAHIH", "HASAN", "DAIF", "MAUDU");
        assertThat(CurationWhitelist.enumOptions(OverrideEntity.HD_HADITHS, "status"))
                .contains("CANONICAL", "VARIANT");
        assertThat(CurationWhitelist.enumOptions(OverrideEntity.HD_NARRATORS, "reliability_grade"))
                .contains("THIQA", "DAIF", "UNKNOWN");
        // свободный текст / число — не enum
        assertThat(CurationWhitelist.enumOptions(OverrideEntity.HD_HADITHS, "chapter_ar")).isEmpty();
        assertThat(CurationWhitelist.enumOptions(OverrideEntity.HD_NARRATORS, "name_ar")).isEmpty();
    }

    @Test
    void isHideAllowed_recordField_followsRecordHidePolicy() {
        // запись-уровень разрешён для рулинга, запрещён для хадиса
        assertThat(CurationWhitelist.isHideAllowed(OverrideEntity.HD_RULINGS,
                FieldOverride.RECORD_FIELD)).isTrue();
        assertThat(CurationWhitelist.isHideAllowed(OverrideEntity.HD_HADITHS,
                FieldOverride.RECORD_FIELD)).isFalse();
        // поле-уровень: author шарха скрываем, book_name — нет
        assertThat(CurationWhitelist.isHideAllowed(OverrideEntity.HD_EXPLANATIONS, "author")).isTrue();
        assertThat(CurationWhitelist.isHideAllowed(OverrideEntity.HD_EXPLANATIONS, "book_name")).isFalse();
    }

    @Test
    void everyEntityHasRules() {
        for (OverrideEntity e : OverrideEntity.values()) {
            assertThat(CurationWhitelist.rules(e))
                    .as("rules for %s", e)
                    .isNotNull();
        }
    }
}
