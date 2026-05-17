package ru.basnukaev.argumentmap.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PagedResponseTest {

    @Test
    void of_firstPageWithMore_hasNextTrueHasPrevFalse() {
        PagedResponse<String> pr = PagedResponse.of(List.of("a", "b"), 0, 2, 5L);
        assertThat(pr.totalPages()).isEqualTo(3);
        assertThat(pr.hasNext()).isTrue();
        assertThat(pr.hasPrev()).isFalse();
        assertThat(pr.totalElements()).isEqualTo(5L);
    }

    @Test
    void of_middlePage_hasBothNextAndPrev() {
        PagedResponse<String> pr = PagedResponse.of(List.of("c", "d"), 1, 2, 5L);
        assertThat(pr.hasNext()).isTrue();
        assertThat(pr.hasPrev()).isTrue();
    }

    @Test
    void of_lastPage_hasNextFalseHasPrevTrue() {
        PagedResponse<String> pr = PagedResponse.of(List.of("e"), 2, 2, 5L);
        assertThat(pr.hasNext()).isFalse();
        assertThat(pr.hasPrev()).isTrue();
    }

    @Test
    void of_emptyResult_returnsSinglePageZeroTotal() {
        PagedResponse<String> pr = PagedResponse.of(List.of(), 0, 20, 0L);
        assertThat(pr.totalPages()).isEqualTo(1);
        assertThat(pr.hasNext()).isFalse();
        assertThat(pr.hasPrev()).isFalse();
        assertThat(pr.totalElements()).isZero();
    }

    @Test
    void of_exactlyFullPage_noNextPage() {
        PagedResponse<String> pr = PagedResponse.of(List.of("a", "b"), 0, 2, 2L);
        assertThat(pr.totalPages()).isEqualTo(1);
        assertThat(pr.hasNext()).isFalse();
    }
}
