package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgDescriptionParser.ParsedDescription;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgMetadata.FileEntry;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.CoverOption;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.PdfFileRef;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceField;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;

/**
 * Чистый маппер (без сети) сырых archive.org-метаданных
 * ({@link ArchiveOrgMetadata}) в {@link ArchiveOrgPreview} (ADR-056).
 *
 * <h2>Провенанс полей (gap-aware enrichment)</h2>
 * archive.org чисто отдаёт title / creator (→author) / language. Издатель,
 * год, число томов, издание чаще лежат только в арабском HTML
 * {@code description} - их вытягивает {@link ArchiveOrgDescriptionParser}
 * (ADR-056). Приоритет: чистое metadata-поле → парсинг description
 * ({@code archive_org}, т.к. значение всё равно из источника) → {@code
 * missing}. {@code rawDescription} отдаётся всегда (admin видит оригинал).
 *
 * <h2>Группировка PDF в тома</h2>
 * PDF-форматы archive.org: {@code Image Container PDF}/{@code Text PDF}
 * (source=original, скан) и {@code Additional Text PDF} (source=derivative,
 * OCR-слой, имя {@code *_text.pdf}).
 *
 * <p>Соглашение об именах (пример fmhji): {@code {id}0[_text].pdf} = обложка
 * (1 страница), {@code {id}1/2/3[_text].pdf} = тома. Группируем по
 * «basename без {@code _text} без расширения»:
 * <ul>
 *   <li>{@code {id}{N}} с N=0 → роль {@code cover};</li>
 *   <li>{@code {id}{N}} с N≥1 → роль {@code volume}, volumeNo=N;</li>
 *   <li>файл не матчит {@code {id}{N}} (одиночный PDF с произвольным
 *       именем) → один том volumeNo=1, без обложки;</li>
 *   <li>в группе original = без {@code _text}, ocr = {@code *_text.pdf};</li>
 *   <li>нет {@code _text}-варианта → {@code ocr==null} (флаг «только скан»).</li>
 * </ul>
 */
@Component
public class ArchiveOrgMetadataMapper {

    private static final String TEXT_SUFFIX = "_text";
    private static final String PDF_EXT = ".pdf";

    private final ArchiveOrgClient client;
    private final ArchiveOrgDescriptionParser descriptionParser;

    public ArchiveOrgMetadataMapper(ArchiveOrgClient client,
                                    ArchiveOrgDescriptionParser descriptionParser) {
        this.client = client;
        this.descriptionParser = descriptionParser;
    }

    public ArchiveOrgPreview toPreview(String identifier, ArchiveOrgMetadata raw) {
        Map<String, Object> m = raw.metadata() != null ? raw.metadata() : Map.of();
        String base = client.baseUrl();

        List<VolumeGroup> groups = groupPdfs(identifier, raw.files(), base);
        boolean hasPdf = !groups.isEmpty();
        boolean hasCoverPdf = groups.stream()
                .anyMatch(g -> VolumeGroup.ROLE_COVER.equals(g.role()));

        // Парсим арабский description: издатель/год/тома/издание/автор/место
        // чаще лежат там, не в чистых полях. Приоритет - чистое metadata-поле,
        // затем parsed (всё равно archive_org), затем missing.
        String description = scalar(m, "description");
        ParsedDescription parsed = descriptionParser.parse(description);

        return new ArchiveOrgPreview(
                identifier,
                ProvenanceField.of(scalar(m, "title")),
                ProvenanceField.of(firstNonBlank(scalar(m, "creator"), parsed.author())),
                ProvenanceField.of(firstNonBlank(scalar(m, "publisher"), parsed.publisher())),
                ProvenanceField.of(parsed.place()),
                // мухаккык в archive.org description обычно отсутствует
                ProvenanceField.missing(),
                ProvenanceField.of(toStr(parsed.editionNumber())),
                // год хиджры/григориан - чистого поля у archive.org нет
                // (только в арабском description), берём из parsed.
                ProvenanceField.of(toStr(parsed.yearHijri())),
                ProvenanceField.of(toStr(parsed.yearGregorian())),
                ProvenanceField.of(firstNonBlank(scalar(m, "volumes"), toStr(parsed.volumes()))),
                ProvenanceField.of(normalizeLanguage(scalar(m, "language"))),
                description,
                groups,
                buildCoverOptions(identifier, base, hasCoverPdf),
                hasPdf
        );
    }

    private static String toStr(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    // ---------------- PDF grouping ----------------

    private List<VolumeGroup> groupPdfs(String identifier, List<FileEntry> files, String base) {
        if (files == null) {
            return List.of();
        }
        // Сохраняем порядок появления групп (LinkedHashMap), сортируем потом
        Map<String, GroupAccumulator> byKey = new LinkedHashMap<>();
        for (FileEntry f : files) {
            if (!isPdf(f)) {
                continue;
            }
            String name = f.name();
            String basename = stripPdfExt(name);
            boolean isOcr = basename.toLowerCase(Locale.ROOT).endsWith(TEXT_SUFFIX);
            String stem = isOcr
                    ? basename.substring(0, basename.length() - TEXT_SUFFIX.length())
                    : basename;

            byKey.computeIfAbsent(stem, k -> new GroupAccumulator())
                    .add(isOcr, new PdfFileRef(name, f.sizeBytes(), downloadUrl(base, identifier, name)));
        }
        if (byKey.isEmpty()) {
            return List.of();
        }

        List<VolumeGroup> result = new ArrayList<>(byKey.size());
        int fallbackVolumeNo = 1;
        for (Map.Entry<String, GroupAccumulator> e : byKey.entrySet()) {
            String stem = e.getKey();
            GroupAccumulator acc = e.getValue();
            Integer parsedNo = parseVolumeNumber(identifier, stem);
            String role;
            int volumeNo;
            if (parsedNo == null) {
                // имя не матчит {id}{N} - одиночный/нестандартный PDF: трактуем
                // как том. Несколько таких → нумеруем по порядку появления.
                role = VolumeGroup.ROLE_VOLUME;
                volumeNo = fallbackVolumeNo++;
            } else if (parsedNo == 0) {
                role = VolumeGroup.ROLE_COVER;
                volumeNo = 0;
            } else {
                role = VolumeGroup.ROLE_VOLUME;
                volumeNo = parsedNo;
            }
            result.add(new VolumeGroup(role, volumeNo, acc.original, acc.ocr));
        }

        // Детерминированный порядок: cover (volumeNo=0) первым, дальше по номеру.
        result.sort(Comparator.comparingInt(VolumeGroup::volumeNo));
        return result;
    }

    /**
     * Парсит номер тома из stem'а вида {@code {identifier}{N}}. Возвращает
     * {@code N} (включая 0 = обложка), либо {@code null} если stem не
     * начинается с identifier либо «хвост» не является целым числом.
     */
    private static Integer parseVolumeNumber(String identifier, String stem) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        // сравнение без учёта регистра (archive.org identifier обычно lower)
        String stemLower = stem.toLowerCase(Locale.ROOT);
        String idLower = identifier.toLowerCase(Locale.ROOT);
        if (!stemLower.startsWith(idLower)) {
            return null;
        }
        String tail = stem.substring(identifier.length());
        if (tail.isEmpty() || !tail.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Файл является PDF если расширение {@code .pdf} или format содержит
     * «PDF». EPUB/изображения/метаданные отсеиваются.
     */
    private static boolean isPdf(FileEntry f) {
        if (f == null || f.name() == null) {
            return false;
        }
        if (f.name().toLowerCase(Locale.ROOT).endsWith(PDF_EXT)) {
            return true;
        }
        String fmt = f.format();
        return fmt != null && fmt.toUpperCase(Locale.ROOT).contains("PDF");
    }

    private static String stripPdfExt(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(PDF_EXT)) {
            return name.substring(0, name.length() - PDF_EXT.length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String downloadUrl(String base, String identifier, String name) {
        return base + "/download/" + identifier + "/" + name;
    }

    /** Аккумулятор original/ocr ветвей внутри одной группы (mutable, локальный). */
    private static final class GroupAccumulator {
        private PdfFileRef original;
        private PdfFileRef ocr;

        void add(boolean isOcr, PdfFileRef ref) {
            if (isOcr) {
                if (ocr == null) {
                    ocr = ref;
                }
            } else {
                if (original == null) {
                    original = ref;
                }
            }
        }
    }

    // ---------------- cover options ----------------

    private static List<CoverOption> buildCoverOptions(String identifier, String base, boolean hasCoverPdf) {
        List<CoverOption> options = new ArrayList<>(3);
        options.add(new CoverOption(
                CoverOption.KIND_THUMBNAIL, base + "/services/img/" + identifier));
        if (hasCoverPdf) {
            options.add(new CoverOption(CoverOption.KIND_COVER_PDF_PAGE, null));
        }
        options.add(new CoverOption(CoverOption.KIND_UPLOAD, null));
        return options;
    }

    // ---------------- metadata field extraction ----------------

    /**
     * Скаляр из metadata-словаря. Значение может быть строкой ИЛИ
     * массивом (collection/subject) - для массива берём первый элемент.
     * Возвращает null для отсутствующего/пустого.
     */
    private static String scalar(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s.isBlank() ? null : s;
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first != null ? first.toString() : null;
        }
        return v.toString();
    }

    /**
     * Нормализация языка в ISO-639 код который ожидает наша модель
     * ({@code ar}/{@code en}/...). archive.org отдаёт {@code Arabic},
     * {@code ara}, {@code ar} - все приводим к {@code ar}. Неизвестные
     * остаются как есть (lower-cased), missing → null.
     */
    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "arabic", "ara", "ar" -> "ar";
            case "english", "eng", "en" -> "en";
            default -> v;
        };
    }
}
