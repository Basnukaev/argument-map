package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceTest {

    @Test
    void isHadith_returnsTrue_forHadithType() {
        Source source = makeSource(SourceType.HADITH, null);
        assertThat(source.isHadith()).isTrue();
        assertThat(source.isBook()).isFalse();
    }

    @Test
    void isBook_returnsTrue_forBookType() {
        Source source = makeSource(SourceType.BOOK, UUID.randomUUID());
        assertThat(source.isBook()).isTrue();
        assertThat(source.requiresBookLink()).isTrue();
    }

    @Test
    void requiresBookLink_falseForNonBookTypes() {
        for (SourceType type : new SourceType[]{
                SourceType.QURAN, SourceType.HADITH,
                SourceType.ARTICLE, SourceType.URL}) {
            assertThat(makeSource(type, null).requiresBookLink())
                    .as("requiresBookLink for " + type)
                    .isFalse();
        }
    }

    @Test
    void requireType_passes_whenTypeMatches() {
        Source source = makeSource(SourceType.HADITH, null);
        // no throw
        source.requireType(SourceType.HADITH);
    }

    @Test
    void requireType_throws_whenTypeMismatches() {
        Source source = makeSource(SourceType.BOOK, UUID.randomUUID());
        assertThatThrownBy(() -> source.requireType(SourceType.HADITH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HADITH")
                .hasMessageContaining("BOOK");
    }

    private static Source makeSource(SourceType type, UUID bookId) {
        return new Source(
                UUID.randomUUID(), type, "title", "citation",
                null, null, bookId, null, Instant.now()
        );
    }
}
