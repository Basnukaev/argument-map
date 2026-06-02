package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceSource;
import ru.basnukaev.argumentmap.library.imports.BookMetadataExtractionService;

/**
 * Unit-тесты слияния AI-извлечения метаданных (ADR-058) с regex-baseline
 * в {@link ArchiveOrgImportService#preview(String)} (ADR-056 amendment b).
 * Без Spring / БД: {@link ArchiveOrgClient} замокан, {@link LlmClient}
 * подменяется fake'ом. Мапперу нужен только {@code baseUrl()}.
 *
 * <p>Проверяем: (1) при disabled LLM превью = regex-only; (2) при enabled
 * LLM AI-значения предпочитаются над regex, provenance остаётся
 * {@code archive_org}.
 */
class ArchiveOrgImportServicePreviewTest {

    private static final String BASE = "https://archive.org";
    private static final String ID = "fmhji";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** description с regex-распознаваемыми полями (издатель/тома/издание). */
    private static final String RAW_DESC =
            "<div>المؤلف: مؤلف ريجيكس<br/>الناشر: ناشر ريجيكس<br/>"
            + "عدد المجلدات: 2<br/>رقم الطبعة: الأولى</div>";

    private ArchiveOrgMetadata fixtureMetadata() {
        // 1 том (original) + его OCR (должен отброситься) - минимально для hasPdf
        return new ArchiveOrgMetadata(
                Map.of("title", "عنوان", "language", "Arabic", "description", RAW_DESC),
                List.of(
                        new ArchiveOrgMetadata.FileEntry(
                                ID + "1.pdf", "Image Container PDF", "original", "1000"),
                        new ArchiveOrgMetadata.FileEntry(
                                ID + "1_text.pdf", "Additional Text PDF", "derivative", "2000")));
    }

    private ArchiveOrgClient mockClient() {
        ArchiveOrgClient client = mock(ArchiveOrgClient.class);
        when(client.baseUrl()).thenReturn(BASE);
        when(client.extractIdentifier(ID)).thenReturn(ID);
        when(client.fetchMetadata(ID)).thenReturn(fixtureMetadata());
        return client;
    }

    private ArchiveOrgMetadataMapper mapper(ArchiveOrgClient client) {
        return new ArchiveOrgMetadataMapper(client, new ArchiveOrgDescriptionParser());
    }

    private static LlmClient disabledLlm() {
        return new LlmClient() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("disabled");
            }
        };
    }

    private static LlmClient fakeLlm(String cannedJson) {
        return new LlmClient() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return cannedJson;
            }
        };
    }

    private ArchiveOrgImportService service(LlmClient llm) {
        ArchiveOrgClient client = mockClient();
        BookMetadataExtractionService extraction =
                new BookMetadataExtractionService(llm, objectMapper);
        // BookService/BookRepository не нужны для preview - передаём mock'и
        return new ArchiveOrgImportService(
                client, mapper(client), extraction,
                mock(ru.basnukaev.argumentmap.library.service.BookService.class),
                mock(ru.basnukaev.argumentmap.library.repository.BookRepository.class),
                objectMapper);
    }

    @Test
    void preview_llmDisabled_regexOnly() {
        ArchiveOrgPreview p = service(disabledLlm()).preview(ID);

        // OCR отброшен → 1 том
        assertThat(p.files()).hasSize(1);
        assertThat(p.files().get(0).name()).isEqualTo("fmhji1.pdf");
        // regex-значения из description
        assertThat(p.author().value()).isEqualTo("مؤلف ريجيكس");
        assertThat(p.publisher().value()).isEqualTo("ناشر ريجيكس");
        assertThat(p.volumes().value()).isEqualTo("2");
        assertThat(p.edition().value()).isEqualTo("1"); // الأولى → 1
        assertThat(p.publisher().source()).isEqualTo(ProvenanceSource.archive_org);
    }

    @Test
    void preview_llmEnabled_aiPreferredOverRegex_provenanceStaysArchiveOrg() {
        String aiJson = """
                {
                  "titleAr": "عنوان ذكي",
                  "authors": ["مؤلف ذكي أول", "مؤلف ذكي ثاني"],
                  "publisher": "ناشر ذكي",
                  "place": "دمشق",
                  "editionText": "الطبعة الثالثة",
                  "editionNumber": 3,
                  "yearHijri": 1440,
                  "yearGregorian": 2019,
                  "volumes": 5
                }""";

        ArchiveOrgPreview p = service(fakeLlm(aiJson)).preview(ID);

        // AI-значения выигрывают
        assertThat(p.author().value()).isEqualTo("مؤلف ذكي أول ، مؤلف ذكي ثاني");
        assertThat(p.publisher().value()).isEqualTo("ناشر ذكي");
        assertThat(p.place().value()).isEqualTo("دمشق"); // regex place был missing
        assertThat(p.edition().value()).isEqualTo("3");
        assertThat(p.yearHijri().value()).isEqualTo("1440");
        assertThat(p.yearGregorian().value()).isEqualTo("2019");
        assertThat(p.volumes().value()).isEqualTo("5");
        // provenance остаётся archive_org (значение всё равно из источника)
        assertThat(p.author().source()).isEqualTo(ProvenanceSource.archive_org);
        assertThat(p.place().source()).isEqualTo(ProvenanceSource.archive_org);
        // язык - чистое metadata-поле, AI не трогает
        assertThat(p.language().value()).isEqualTo("ar");
        // OCR по-прежнему отброшен
        assertThat(p.files()).hasSize(1);
    }
}
