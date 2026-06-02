package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceSource;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;

/**
 * Чистые unit-тесты {@link ArchiveOrgMetadataMapper} на фикстурах
 * реальных metadata.json (ADR-056): провенанс полей + авто-группировка
 * PDF + edge cases. Без Spring и без сети.
 *
 * <p>Фикстуры (src/test/resources/archiveorg/):
 * <ul>
 *   <li>{@code fmhji.json} - multi-volume (обложка + 3 тома), каждый
 *       original + OCR; creator=null (автор в description);</li>
 *   <li>{@code sahih-bukhari-arabic.json} - single-volume original+OCR,
 *       имя файла НЕ матчит {id}{N} (99184.pdf), creator присутствует;</li>
 *   <li>{@code kitab-al-tawhid.json} - один PDF без OCR (scan-only).</li>
 * </ul>
 */
class ArchiveOrgMetadataMapperTest {

    private static final String BASE = "https://archive.org";

    private final ObjectMapper objectMapper = new ObjectMapper();
    // client нужен мапперу только ради baseUrl() - сеть не дёргается
    private final ArchiveOrgClient client =
            new ArchiveOrgClient(null, new ArchiveOrgProperties(BASE, 30, 10), objectMapper);
    private final ArchiveOrgMetadataMapper mapper = new ArchiveOrgMetadataMapper(client);

    private ArchiveOrgMetadata load(String fixture) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/archiveorg/" + fixture)) {
            assertThat(is).as("фикстура " + fixture).isNotNull();
            return objectMapper.readValue(is, ArchiveOrgMetadata.class);
        }
    }

    // ---------------- fmhji: multi-volume + cover + OCR ----------------

    @Test
    void fmhji_provenance_titleFromSource_authorMissing() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("fmhji", load("fmhji.json"));

        assertThat(p.archiveOrgId()).isEqualTo("fmhji");
        assertThat(p.title().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.title().value()).contains("الفقه المنهجي");
        // creator у fmhji null - автор только в арабском description
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.language().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.language().value()).isEqualTo("ar"); // "Arabic" → "ar"
        // editions/years/place/muhaqqiq/volumes - MVP не парсит description
        assertThat(p.publisher().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.yearHijri().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.volumes().source()).isEqualTo(ProvenanceSource.missing);
        // rawDescription отдаётся для копипасты админом
        assertThat(p.rawDescription()).contains("الناشر").contains("عدد المجلدات");
    }

    @Test
    void fmhji_grouping_coverPlusThreeVolumes_eachOriginalAndOcr() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("fmhji", load("fmhji.json"));

        assertThat(p.hasPdf()).isTrue();
        assertThat(p.files()).hasSize(4); // обложка + 3 тома

        VolumeGroup cover = p.files().get(0);
        assertThat(cover.role()).isEqualTo(VolumeGroup.ROLE_COVER);
        assertThat(cover.volumeNo()).isZero();
        assertThat(cover.original().name()).isEqualTo("fmhji0.pdf");
        assertThat(cover.ocr().name()).isEqualTo("fmhji0_text.pdf");

        VolumeGroup vol1 = p.files().get(1);
        assertThat(vol1.role()).isEqualTo(VolumeGroup.ROLE_VOLUME);
        assertThat(vol1.volumeNo()).isEqualTo(1);
        assertThat(vol1.original().name()).isEqualTo("fmhji1.pdf");
        assertThat(vol1.original().downloadUrl())
                .isEqualTo("https://archive.org/download/fmhji/fmhji1.pdf");
        assertThat(vol1.original().size()).isEqualTo(19518609L);
        assertThat(vol1.ocr().name()).isEqualTo("fmhji1_text.pdf");
        assertThat(vol1.ocr().size()).isEqualTo(25286151L);

        assertThat(p.files().get(3).volumeNo()).isEqualTo(3);
        // тома отсортированы по номеру детерминированно
        assertThat(p.files().stream().map(VolumeGroup::volumeNo)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void fmhji_coverOptions_thumbnailAndCoverPdfAndUpload() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("fmhji", load("fmhji.json"));

        assertThat(p.coverOptions()).extracting("kind")
                .containsExactly("thumbnail", "cover_pdf_page", "upload");
        assertThat(p.coverOptions().get(0).url())
                .isEqualTo("https://archive.org/services/img/fmhji");
    }

    // ---------------- sahih-bukhari: single volume, no {id}{N} match ----------------

    @Test
    void sahihBukhari_singleVolume_originalAndOcr_noCover() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("sahih-bukhari-arabic",
                load("sahih-bukhari-arabic.json"));

        // имя 99184.pdf НЕ начинается с identifier → fallback в один том, без обложки
        assertThat(p.files()).hasSize(1);
        VolumeGroup g = p.files().get(0);
        assertThat(g.role()).isEqualTo(VolumeGroup.ROLE_VOLUME);
        assertThat(g.volumeNo()).isEqualTo(1);
        assertThat(g.original().name()).isEqualTo("99184.pdf");
        assertThat(g.ocr().name()).isEqualTo("99184_text.pdf");

        // нет обложки → coverOptions без cover_pdf_page
        assertThat(p.coverOptions()).extracting("kind")
                .containsExactly("thumbnail", "upload");

        // creator у этого item присутствует → author из источника
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.author().value()).isEqualTo("إمام بخاري");
        // language "ara" → "ar"
        assertThat(p.language().value()).isEqualTo("ar");
    }

    // ---------------- kitab-al-tawhid: scan-only, no OCR ----------------

    @Test
    void kitabAlTawhid_scanOnly_noOcrBranch() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("kitab-al-tawhid",
                load("kitab-al-tawhid.json"));

        assertThat(p.files()).hasSize(1);
        VolumeGroup g = p.files().get(0);
        assertThat(g.role()).isEqualTo(VolumeGroup.ROLE_VOLUME);
        assertThat(g.original().name()).isEqualTo("kitab al-tawhid.pdf");
        assertThat(g.ocr()).as("нет _text.pdf → scan-only").isNull();

        assertThat(p.coverOptions()).extracting("kind")
                .containsExactly("thumbnail", "upload");
    }

    // ---------------- synthetic edge cases ----------------

    @Test
    void noPdf_hasPdfFalse_emptyGroups() {
        ArchiveOrgMetadata raw = new ArchiveOrgMetadata(
                java.util.Map.of("title", "Только EPUB"),
                java.util.List.of(new ArchiveOrgMetadata.FileEntry(
                        "book.epub", "EPUB", "original", "1000")));

        ArchiveOrgPreview p = mapper.toPreview("epubonly", raw);

        assertThat(p.hasPdf()).isFalse();
        assertThat(p.files()).isEmpty();
        assertThat(p.title().value()).isEqualTo("Только EPUB");
    }
}
