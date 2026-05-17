package ru.basnukaev.argumentmap.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void from_nullParams_returnsDefaults() {
        PageRequest pr = PageRequest.from(null, null);
        assertThat(pr.page()).isZero();
        assertThat(pr.size()).isEqualTo(20);
        assertThat(pr.offset()).isZero();
    }

    @Test
    void from_validParams_computesOffset() {
        PageRequest pr = PageRequest.from(3, 25);
        assertThat(pr.page()).isEqualTo(3);
        assertThat(pr.size()).isEqualTo(25);
        assertThat(pr.offset()).isEqualTo(75);
    }

    @Test
    void from_negativePage_resetsToZero() {
        PageRequest pr = PageRequest.from(-5, 10);
        assertThat(pr.page()).isZero();
        assertThat(pr.size()).isEqualTo(10);
    }

    @Test
    void from_zeroOrNegativeSize_usesDefault() {
        assertThat(PageRequest.from(0, 0).size()).isEqualTo(20);
        assertThat(PageRequest.from(0, -10).size()).isEqualTo(20);
    }

    @Test
    void from_sizeOverMax_clampsToMax() {
        PageRequest pr = PageRequest.from(0, 500);
        assertThat(pr.size()).isEqualTo(100);
    }

    @Test
    void from_sizeAtMax_keepsExact() {
        PageRequest pr = PageRequest.from(0, 100);
        assertThat(pr.size()).isEqualTo(100);
    }
}
