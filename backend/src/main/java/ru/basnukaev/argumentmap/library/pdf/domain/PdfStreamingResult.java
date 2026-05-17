package ru.basnukaev.argumentmap.library.pdf.domain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Результат {@code PdfSourceProvider.openStream}: открытый
 * {@link InputStream} с метаданными для HTTP response. Caller обязан
 * закрыть stream (try-with-resources или явный {@link #close()}).
 *
 * <p>Если {@link #isPartial()} = {@code true} - controller возвращает
 * {@code 206 Partial Content} с {@code Content-Range} header. Если
 * {@code false} - {@code 200 OK} с full {@code Content-Length}.
 *
 * <p>Для MinIO-backed sources (UserUploadProvider, cached PdfLinks) -
 * stream это {@code ResponseInputStream<GetObjectResponse>}. Для
 * upstream forwarding (PdfLinks cache miss с Range) - stream напрямую
 * от {@code HttpClient.send(BodyHandlers.ofInputStream)}. Оба
 * прозрачно стримятся через {@code StreamingResponseBody}.
 *
 * @param stream открытый поток PDF-bytes. Caller закрывает
 * @param contentLength длина данных которые stream вернёт
 *                      (для {@code Content-Length} header)
 * @param startInclusive первый байт response (для {@code Content-Range})
 * @param endInclusive последний байт response inclusive (для
 *                     {@code Content-Range})
 * @param totalSize полный размер файла в байтах (для {@code Content-Range}
 *                  total часть и для frontend chunk-size планирования)
 * @param isPartial true если это 206 Partial Content; false если full 200 OK
 */
public record PdfStreamingResult(
        InputStream stream,
        long contentLength,
        long startInclusive,
        long endInclusive,
        long totalSize,
        boolean isPartial
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
}
