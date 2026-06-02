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
 * @param rawDescription  описание (plain text - HTML снят) для копипасты
 * @param files           PDF-тома (только original Image-Container PDF;
 *                        OCR {@code _text} варианты отброшены - ADR-056
 *                        amendment b). files[0] - обложка ({@code role=cover},
 *                        если есть {id}0), остальные - тома по номеру
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
     * Только original Image-Container PDF - OCR {@code _text.pdf} варианты
     * archive.org мы НЕ регистрируем (их Tesseract-слой портит арабский,
     * ADR-056 amendment b; archive.org-книги читаются как сканы, FILE_ONLY).
     *
     * @param role      {@code cover} ({id}0) либо {@code volume}
     * @param volumeNo  номер тома (1-based); для cover - 0
     * @param name      имя PDF-файла (например {@code fmhji1.pdf})
     * @param label     человекочитаемая метка («Том 1» / «Обложка» / «Книга»)
     * @param sizeBytes размер файла в байтах (nullable)
     */
    public record VolumeGroup(
            String role,
            int volumeNo,
            String name,
            String label,
            Long sizeBytes,
            String downloadUrl
    ) {
        public static final String ROLE_COVER = "cover";
        public static final String ROLE_VOLUME = "volume";
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
