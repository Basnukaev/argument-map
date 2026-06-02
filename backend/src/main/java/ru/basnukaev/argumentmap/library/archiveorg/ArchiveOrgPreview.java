package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.List;

/**
 * Превью импорта archive.org-книги «как она ляжет в наш формат»
 * (ADR-056). Чистый результат {@link ArchiveOrgMetadataMapper} - без
 * записи в БД.
 *
 * <p><b>Gap-aware enrichment</b>: каждое «наше» поле несёт провенанс
 * {@link ProvenanceField} {@code (value, source)} - {@code archive_org}
 * (взято из метаданных) либо {@code missing} (нет в источнике, фронт
 * подсвечивает «дообогати»). Обобщаемый паттерн для будущих источников.
 *
 * @param archiveOrgId    natural key (identifier) - для idempotency
 * @param title           заголовок
 * @param author          автор (archive.org {@code creator})
 * @param publisher       издатель (обычно только в description → missing)
 * @param place           место издания (обычно missing)
 * @param muhaqqiq        мухаккык (обычно missing)
 * @param edition         номер издания (обычно missing)
 * @param yearHijri       год по хиджре (обычно missing)
 * @param yearGregorian   год григорианский (обычно missing)
 * @param volumes         число томов (обычно missing - в description)
 * @param language        язык (нормализованный ISO-код)
 * @param rawDescription  сырое HTML-описание (арабское) для копипасты
 * @param files           сгруппированные тома (cover/volume × original/ocr)
 * @param coverOptions    варианты обложки (thumbnail/cover_pdf_page/upload)
 * @param hasPdf          false если у item'а нет ни одного PDF
 */
public record ArchiveOrgPreview(
        String archiveOrgId,
        ProvenanceField title,
        ProvenanceField author,
        ProvenanceField publisher,
        ProvenanceField place,
        ProvenanceField muhaqqiq,
        ProvenanceField edition,
        ProvenanceField yearHijri,
        ProvenanceField yearGregorian,
        ProvenanceField volumes,
        ProvenanceField language,
        String rawDescription,
        List<VolumeGroup> files,
        List<CoverOption> coverOptions,
        boolean hasPdf
) {

    /** Источник значения поля превью. */
    public enum ProvenanceSource {
        archive_org,
        missing
    }

    /**
     * Значение поля + его провенанс. {@code value==null} ⇔
     * {@code source==missing} (поля нет в источнике). Для непустого
     * значения {@code source==archive_org}.
     */
    public record ProvenanceField(String value, ProvenanceSource source) {

        /** Поле взято из archive.org (или missing если value пустой). */
        public static ProvenanceField of(String value) {
            return (value == null || value.isBlank())
                    ? missing()
                    : new ProvenanceField(value.trim(), ProvenanceSource.archive_org);
        }

        /** Поле отсутствует в источнике - фронт зовёт дообогатить. */
        public static ProvenanceField missing() {
            return new ProvenanceField(null, ProvenanceSource.missing);
        }
    }

    /**
     * Один том (или обложка) после авто-группировки PDF по {@code {id}{N}}.
     * {@code original} - Image-PDF (точный просмотр скана); {@code ocr} -
     * {@code *_text.pdf} (источник текста). Любая ветвь nullable:
     * только-скан без OCR → {@code ocr==null}; редкий случай только-OCR →
     * {@code original==null}.
     *
     * @param role      {@code cover} ({id}0) либо {@code volume}
     * @param volumeNo  номер тома (1-based); для cover - 0
     */
    public record VolumeGroup(
            String role,
            int volumeNo,
            PdfFileRef original,
            PdfFileRef ocr
    ) {
        public static final String ROLE_COVER = "cover";
        public static final String ROLE_VOLUME = "volume";
    }

    /** Ссылка на конкретный PDF-файл с готовым download-URL. */
    public record PdfFileRef(String name, Long size, String downloadUrl) {
    }

    /**
     * Вариант обложки. {@code thumbnail} - готовый archive.org
     * thumbnail; {@code cover_pdf_page} - первая страница cover-PDF
     * (если есть {id}0); {@code upload} - пользователь загрузит свою
     * (url отсутствует).
     */
    public record CoverOption(String kind, String url) {
        public static final String KIND_THUMBNAIL = "thumbnail";
        public static final String KIND_COVER_PDF_PAGE = "cover_pdf_page";
        public static final String KIND_UPLOAD = "upload";
    }
}
