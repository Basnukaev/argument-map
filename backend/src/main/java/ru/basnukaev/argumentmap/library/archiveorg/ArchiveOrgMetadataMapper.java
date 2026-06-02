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
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceField;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;
import ru.basnukaev.argumentmap.library.imports.HtmlText;

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
 * <h2>Группировка PDF в тома (ADR-056 amendment b)</h2>
 * Регистрируем ТОЛЬКО original Image-Container PDF. OCR-варианты
 * archive.org ({@code *_text.pdf}, format {@code Additional Text PDF}) -
 * это их собственный Tesseract-слой, который портит арабский (источник
 * «абракадабры»); их мы полностью отбрасываем. archive.org-книги читаются
 * как сканы (FILE_ONLY), текст не извлекаем (согласовано с удалением
 * нашего OCR в ADR-057).
 *
 * <p>Соглашение об именах (пример fmhji): {@code {id}0.pdf} = обложка
 * (1 страница), {@code {id}1/2/3.pdf} = тома:
 * <ul>
 *   <li>{@code {id}{N}} с N=0 → роль {@code cover};</li>
 *   <li>{@code {id}{N}} с N≥1 → роль {@code volume}, volumeNo=N;</li>
 *   <li>файл не матчит {@code {id}{N}} (одиночный PDF с произвольным
 *       именем) → один том volumeNo=1, без обложки.</li>
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
        // затем parsed (всё равно archive_org), затем missing. Парсер сам
        // снимает HTML, поэтому передаём сырое описание.
        String rawDescription = scalar(m, "description");
        ParsedDescription parsed = descriptionParser.parse(rawDescription);

        // В превью и в БД отдаём description plain-text (HTML снят): reader
        // иначе показывает буквальные <div> теги (ADR-056 amendment b).
        String description = HtmlText.stripTags(rawDescription);

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
        // Регистрируем только original Image-Container PDF. OCR-варианты
        // archive.org (*_text.pdf) полностью отбрасываем - их Tesseract-слой
        // портит арабский (ADR-056 amendment b). Дедуп по stem на случай
        // дублей. Сохраняем порядок появления (LinkedHashMap), сортируем потом.
        Map<String, RawPdf> byStem = new LinkedHashMap<>();
        for (FileEntry f : files) {
            if (!isPdf(f)) {
                continue;
            }
            String name = f.name();
            String stem = stripPdfExt(name);
            if (stem.toLowerCase(Locale.ROOT).endsWith(TEXT_SUFFIX)) {
                continue; // OCR-слой archive.org - не регистрируем
            }
            byStem.putIfAbsent(stem, new RawPdf(name, f.sizeBytes()));
        }
        if (byStem.isEmpty()) {
            return List.of();
        }

        // Сначала определяем роли/номера, затем формируем метки (число томов
        // влияет на «Том N» vs «Книга»).
        List<Resolved> resolved = new ArrayList<>(byStem.size());
        int fallbackVolumeNo = 1;
        for (Map.Entry<String, RawPdf> e : byStem.entrySet()) {
            Integer parsedNo = parseVolumeNumber(identifier, e.getKey());
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
            resolved.add(new Resolved(role, volumeNo, e.getValue()));
        }

        long volumeCount = resolved.stream()
                .filter(r -> VolumeGroup.ROLE_VOLUME.equals(r.role()))
                .count();

        List<VolumeGroup> result = new ArrayList<>(resolved.size());
        for (Resolved r : resolved) {
            String label = VolumeGroup.ROLE_COVER.equals(r.role())
                    ? "Обложка"
                    : (volumeCount > 1 ? "Том " + r.volumeNo() : "Книга");
            result.add(new VolumeGroup(
                    r.role(), r.volumeNo(),
                    r.pdf().name(), label, r.pdf().size(),
                    downloadUrl(base, identifier, r.pdf().name())));
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

    /** Сырой original PDF (имя + размер) до резолва роли/метки. */
    private record RawPdf(String name, Long size) {
    }

    /** PDF с уже определённой ролью и номером тома (до формирования метки). */
    private record Resolved(String role, int volumeNo, RawPdf pdf) {
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
