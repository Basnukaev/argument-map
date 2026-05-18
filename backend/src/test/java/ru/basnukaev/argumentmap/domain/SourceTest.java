package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    private static Source makeSource(SourceType type, UUID bookId) {
        return new Source(
                UUID.randomUUID(), type, "title", "citation",
                null, null, bookId, null, Instant.now()
        );
    }
}
