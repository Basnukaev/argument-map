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

    public long start() {
        return start;
    }

    public long totalSize() {
        return totalSize;
    }
}
