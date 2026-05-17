package ru.basnukaev.argumentmap.library.pdf.domain;

/**
 * Спецификация HTTP Range request (RFC 7233). Семантика - обе границы
 * inclusive, как требует HTTP стандарт. {@code endInclusive == null}
 * означает open-ended {@code "bytes=N-"} (от {@code startInclusive}
 * до конца файла).
 *
 * <p>Используется для lazy PDF streaming (25.d.5, ADR-023 amendment) -
 * frontend (PDF.js) посылает Range header, controller парсит в этот
 * record и передаёт в {@code PdfSourceProvider.openStream}. Provider
 * сам решает как форвардить: к archive.org через HTTP Range header или
 * к MinIO через {@code GetObjectRequest.range()}.
 *
 * @param startInclusive первый байт (0-based)
 * @param endInclusive последний байт (включительно), либо {@code null}
 *                     для open-ended до конца файла
 */
public record RangeSpec(
        long startInclusive,
        Long endInclusive
) {

    public RangeSpec {
        if (startInclusive < 0) {
            throw new IllegalArgumentException(
                    "startInclusive должен быть >= 0, получено: " + startInclusive);
        }
        if (endInclusive != null && endInclusive < startInclusive) {
            throw new IllegalArgumentException(
                    "endInclusive (" + endInclusive + ") < startInclusive (" + startInclusive + ")");
        }
    }

    /**
     * Резолвит open-ended end в конкретное значение на основе известного
     * размера файла. Используется когда требуется конкретный range для
     * MinIO {@code GetObjectRequest.range()}.
     */
    public long resolvedEndInclusive(long totalSize) {
        if (endInclusive != null) {
            return Math.min(endInclusive, totalSize - 1);
        }
        return totalSize - 1;
    }

    /**
     * Длина запрошенного диапазона в байтах с учётом резолва open-ended
     * через {@link #resolvedEndInclusive}.
     */
    public long length(long totalSize) {
        return resolvedEndInclusive(totalSize) - startInclusive + 1;
    }
}
