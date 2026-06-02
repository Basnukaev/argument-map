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
 * <p>Регистрируются ТОЛЬКО original Image-Container PDF - OCR
 * {@code _text.pdf} варианты отброшены (ADR-056 amendment b).
 *
 * <p>Фикстуры (src/test/resources/archiveorg/):
 * <ul>
 *   <li>{@code fmhji.json} - multi-volume (обложка + 3 тома), у каждого
 *       тома есть OCR-вариант, но он не должен попасть в группы;
 *       creator=null (автор в description);</li>
 *   <li>{@code sahih-bukhari-arabic.json} - single-volume,
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
    private final ArchiveOrgMetadataMapper mapper =
            new ArchiveOrgMetadataMapper(client, new ArchiveOrgDescriptionParser());

    private ArchiveOrgMetadata load(String fixture) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/archiveorg/" + fixture)) {
            assertThat(is).as("фикстура " + fixture).isNotNull();
            return objectMapper.readValue(is, ArchiveOrgMetadata.class);
        }
    }

    // ---------------- fmhji: multi-volume + cover + OCR ----------------

    @Test
    void fmhji_provenance_titleFromSource_gapFieldsParsedFromDescription() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("fmhji", load("fmhji.json"));

        assertThat(p.archiveOrgId()).isEqualTo("fmhji");
        assertThat(p.title().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.title().value()).contains("الفقه المنهجي");
        assertThat(p.language().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.language().value()).isEqualTo("ar"); // "Arabic" → "ar"

        // creator у fmhji null - автор теперь парсится из description (المؤلف:)
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.author().value()).contains("مصطفى الخن");
        // الناشر: دار القلم دمشق - кладём целиком в publisher (place не сплитим)
        assertThat(p.publisher().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.publisher().value()).isEqualTo("دار القلم دمشق");
        // سنة النشر: 1433 - 2012 → hijri=1433, gregorian=2012
        assertThat(p.yearHijri().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.yearHijri().value()).isEqualTo("1433");
        assertThat(p.yearGregorian().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.yearGregorian().value()).isEqualTo("2012");
        // عدد المجلدات : 3 → volumes=3
        assertThat(p.volumes().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.volumes().value()).isEqualTo("3");
        // رقم الطبعة : الطبعة الثالثة عشر → 13 (best-effort ordinal)
        assertThat(p.edition().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.edition().value()).isEqualTo("13");
        // place явной метки مكان النشر нет → остаётся missing
        assertThat(p.place().source()).isEqualTo(ProvenanceSource.missing);
        // мухаккык в archive.org description отсутствует → missing
        assertThat(p.muhaqqiq().source()).isEqualTo(ProvenanceSource.missing);

        // rawDescription отдаётся для копипасты админом, но уже plain-text:
        // HTML-теги сняты (reader иначе показывал бы буквальные <div>)
        assertThat(p.rawDescription()).contains("الناشر").contains("عدد المجلدات");
        assertThat(p.rawDescription()).doesNotContain("<").doesNotContain("/>");
    }

    @Test
    void description_htmlStripped_plainTextStored() {
        ArchiveOrgMetadata raw = new ArchiveOrgMetadata(
                java.util.Map.of(
                        "title", "كتاب",
                        "language", "ar",
                        "description", "<div>سطر أول</div><br/><p>سطر ثاني</p>"),
                java.util.List.of(new ArchiveOrgMetadata.FileEntry(
                        "book.pdf", "Image Container PDF", "original", "1000")));

        ArchiveOrgPreview p = mapper.toPreview("htmltest", raw);

        assertThat(p.rawDescription()).doesNotContain("<div>").doesNotContain("<p>");
        assertThat(p.rawDescription()).contains("سطر أول").contains("سطر ثاني");
    }

    // ---------------- description parsing edge cases ----------------

    @Test
    void noParseableDescription_allGapFieldsMissing() {
        ArchiveOrgMetadata raw = new ArchiveOrgMetadata(
                java.util.Map.of(
                        "title", "كتاب بلا وصف",
                        "language", "Arabic",
                        // description без меток المؤلف/الناشر/... - ничего не парсится
                        "description", "<div>مجرد نص حر بدون أي حقول معرّفة</div>"),
                java.util.List.of(new ArchiveOrgMetadata.FileEntry(
                        "book.pdf", "Text PDF", "original", "1000")));

        ArchiveOrgPreview p = mapper.toPreview("nodesc", raw);

        assertThat(p.title().value()).isEqualTo("كتاب بلا وصف");
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.publisher().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.place().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.edition().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.yearHijri().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.yearGregorian().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.volumes().source()).isEqualTo(ProvenanceSource.missing);
        // raw всё равно отдаётся
        assertThat(p.rawDescription()).contains("مجرد نص حر");
    }

    @Test
    void partialLabels_onlyPresentFieldsParsed() {
        // только الناشر + عدد المجلدات; остальные метки отсутствуют
        ArchiveOrgMetadata raw = new ArchiveOrgMetadata(
                java.util.Map.of(
                        "title", "كتاب",
                        "language", "ara",
                        "description", "الناشر: دار الفكر<br />عدد المجلدات: 5"),
                java.util.List.of(new ArchiveOrgMetadata.FileEntry(
                        "book.pdf", "Text PDF", "original", "1000")));

        ArchiveOrgPreview p = mapper.toPreview("partial", raw);

        assertThat(p.publisher().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.publisher().value()).isEqualTo("دار الفكر");
        assertThat(p.volumes().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.volumes().value()).isEqualTo("5");
        // отсутствующие метки → missing
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.yearHijri().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.yearGregorian().source()).isEqualTo(ProvenanceSource.missing);
        assertThat(p.edition().source()).isEqualTo(ProvenanceSource.missing);
    }

    @Test
    void cleanMetadataField_preferredOverDescription() {
        // у item есть чистый publisher И description с الناشر - берём чистый
        ArchiveOrgMetadata raw = new ArchiveOrgMetadata(
                java.util.Map.of(
                        "title", "كتاب",
                        "creator", "مؤلف نظيف",
                        "publisher", "ناشر نظيف",
                        "description", "المؤلف: مؤلف من الوصف<br />الناشر: ناشر من الوصف"),
                java.util.List.of(new ArchiveOrgMetadata.FileEntry(
                        "book.pdf", "Text PDF", "original", "1000")));

        ArchiveOrgPreview p = mapper.toPreview("clean", raw);

        assertThat(p.author().value()).isEqualTo("مؤلف نظيف");
        assertThat(p.publisher().value()).isEqualTo("ناشر نظيف");
    }

    @Test
    void fmhji_grouping_coverPlusThreeVolumes_originalsOnly_noOcr() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("fmhji", load("fmhji.json"));

        assertThat(p.hasPdf()).isTrue();
        assertThat(p.files()).hasSize(4); // обложка + 3 тома (OCR отброшены)

        VolumeGroup cover = p.files().get(0);
        assertThat(cover.role()).isEqualTo(VolumeGroup.ROLE_COVER);
        assertThat(cover.volumeNo()).isZero();
        assertThat(cover.name()).isEqualTo("fmhji0.pdf");
        assertThat(cover.label()).isEqualTo("Обложка");

        VolumeGroup vol1 = p.files().get(1);
        assertThat(vol1.role()).isEqualTo(VolumeGroup.ROLE_VOLUME);
        assertThat(vol1.volumeNo()).isEqualTo(1);
        assertThat(vol1.name()).isEqualTo("fmhji1.pdf");
        assertThat(vol1.label()).isEqualTo("Том 1");
        assertThat(vol1.downloadUrl())
                .isEqualTo("https://archive.org/download/fmhji/fmhji1.pdf");
        assertThat(vol1.sizeBytes()).isEqualTo(19518609L);

        assertThat(p.files().get(3).volumeNo()).isEqualTo(3);
        // тома отсортированы по номеру детерминированно
        assertThat(p.files().stream().map(VolumeGroup::volumeNo)).containsExactly(0, 1, 2, 3);
        // НИ одного _text.pdf не должно протечь в группы
        assertThat(p.files().stream().map(VolumeGroup::name))
                .noneMatch(n -> n.contains("_text"));
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
    void sahihBukhari_singleVolume_originalOnly_noCover() throws Exception {
        ArchiveOrgPreview p = mapper.toPreview("sahih-bukhari-arabic",
                load("sahih-bukhari-arabic.json"));

        // имя 99184.pdf НЕ начинается с identifier → fallback в один том, без обложки
        assertThat(p.files()).hasSize(1);
        VolumeGroup g = p.files().get(0);
        assertThat(g.role()).isEqualTo(VolumeGroup.ROLE_VOLUME);
        assertThat(g.volumeNo()).isEqualTo(1);
        assertThat(g.name()).isEqualTo("99184.pdf");
        assertThat(g.label()).isEqualTo("Книга"); // единственный том → «Книга»
        // OCR-вариант 99184_text.pdf отброшен
        assertThat(p.files().stream().map(VolumeGroup::name))
                .noneMatch(n -> n.contains("_text"));

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
        assertThat(g.name()).isEqualTo("kitab al-tawhid.pdf");
        assertThat(g.label()).isEqualTo("Книга");

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
