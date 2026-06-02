package ru.basnukaev.argumentmap.library.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookContentKindTest {

    @Test
    void of_textAndFile_returnsTextAndFile() {
        assertThat(BookContentKind.of(true, true)).isEqualTo(BookContentKind.TEXT_AND_FILE);
    }

    @Test
    void of_textOnly_returnsTextOnly() {
        assertThat(BookContentKind.of(true, false)).isEqualTo(BookContentKind.TEXT_ONLY);
    }

    @Test
    void of_fileOnly_returnsFileOnly() {
        assertThat(BookContentKind.of(false, true)).isEqualTo(BookContentKind.FILE_ONLY);
    }

    @Test
    void of_neither_defaultsToTextOnly() {
        assertThat(BookContentKind.of(false, false)).isEqualTo(BookContentKind.TEXT_ONLY);
    }
}
