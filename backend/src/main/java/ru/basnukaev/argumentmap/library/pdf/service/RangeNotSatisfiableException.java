package ru.basnukaev.argumentmap.library.pdf.service;

/**
 * Клиент запросил Range за пределами файла - например
 * {@code Range: bytes=50000-} для файла размером 10000. Маппится в
 * HTTP 416 Range Not Satisfiable (RFC 7233) через
 * {@code GlobalExceptionHandler}.
 */
public class RangeNotSatisfiableException extends RuntimeException {

    private final long start;
    private final long totalSize;

    public RangeNotSatisfiableException(long start, long totalSize) {
        super("Range start=" + start + " вне диапазона файла (size=" + totalSize + ")");
        this.start = start;
        this.totalSize = totalSize;
    }

    private RangeNotSatisfiableException(String detail, long start, long totalSize) {
        super(detail);
        this.start = start;
        this.totalSize = totalSize;
    }

    /**
     * Маркерный конструктор для suffix-range request'ов ({@code Range:
     * bytes=-N}). Parser в controller не имеет totalSize чтобы перевести
     * suffix в абсолютный диапазон, а PDF.js suffix-ranges не использует -
     * проще явно отказать с понятным detail, чем плодить overflow или
     * новый режим в {@code RangeSpec}.
     */
    public static RangeNotSatisfiableException unsupportedSuffix() {
        return new RangeNotSatisfiableException(
                "Suffix-range (Range: bytes=-N) не поддерживается этим эндпоинтом - "
                        + "используйте абсолютный диапазон Range: bytes=START-END",
                -1L, -1L);
    }

    public long start() {
        return start;
    }

    public long totalSize() {
        return totalSize;
    }
}
