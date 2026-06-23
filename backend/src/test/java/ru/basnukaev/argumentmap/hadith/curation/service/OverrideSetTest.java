package ru.basnukaev.argumentmap.hadith.curation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;

/** Unit-тесты каст-помощников и группировки {@link OverrideSet} (§3.4). */
class OverrideSetTest {

    private static final UUID ID = UUID.randomUUID();

    private static FieldOverride ov(String field, String value, boolean isNull, boolean hidden) {
        return new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_HADITHS.tableName(),
                ID, field, value, isNull, hidden, UUID.randomUUID(), Instant.now(), null);
    }

    @Test
    void applyStr_overrideValueWins() {
        OverrideSet set = OverrideSet.group(List.of(ov("status", "VARIANT", false, false)));
        assertThat(set.applyStr(ID, "status", "CANONICAL")).isEqualTo("VARIANT");
    }

    @Test
    void applyStr_noOverride_returnsBase() {
        assertThat(OverrideSet.EMPTY.applyStr(ID, "status", "CANONICAL")).isEqualTo("CANONICAL");
    }

    @Test
    void applyStr_nullOverride_returnsNull() {
        OverrideSet set = OverrideSet.group(List.of(ov("chapter_ar", null, true, false)));
        assertThat(set.applyStr(ID, "chapter_ar", "باب")).isNull();
    }

    @Test
    void applyStr_hidden_returnsNull() {
        OverrideSet set = OverrideSet.group(List.of(ov("grade_text", null, false, true)));
        assertThat(set.applyStr(ID, "grade_text", "ثقة")).isNull();
    }

    @Test
    void applyInt_parsesValid() {
        OverrideSet set = OverrideSet.group(List.of(ov("primary_number", "42", false, false)));
        assertThat(set.applyInt(ID, "primary_number", 1)).isEqualTo(42);
    }

    @Test
    void applyInt_brokenValue_fallsBackToBaseNotThrow() {
        OverrideSet set = OverrideSet.group(List.of(ov("primary_number", "не-число", false, false)));
        // битый int НЕ роняет read — возвращает base (WARN в логе)
        assertThat(set.applyInt(ID, "primary_number", 7)).isEqualTo(7);
    }

    @Test
    void applyBool_truthy() {
        OverrideSet set = OverrideSet.group(List.of(ov("primary_chain", "true", false, false)));
        assertThat(set.applyBool(ID, "primary_chain", false)).isTrue();
    }

    @Test
    void emptySet_isNoOp() {
        assertThat(OverrideSet.EMPTY.isEmpty()).isTrue();
        assertThat(OverrideSet.EMPTY.hasEntity(ID)).isFalse();
    }

    @Test
    void overriddenFields_excludesRecordHide() {
        OverrideSet set = OverrideSet.group(List.of(
                ov("authenticity", "SAHIH", false, false),
                ov(FieldOverride.RECORD_FIELD, null, false, true)));
        assertThat(set.overriddenFields(ID)).containsExactly("authenticity");
        assertThat(set.isRecordHidden(ID)).isTrue();
    }
}
