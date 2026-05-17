package ru.basnukaev.argumentmap.library.pdf.service;

import java.net.URI;
import java.nio.file.Path;

import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;

/**
 * Абстракция fetch'а PDF из upstream URL в local file. Существует
 * чтобы {@link PdfLinksSourceProvider} был unit-testable без
 * mock'а {@code HttpClient} (Java 21 strict modules не дают
 * reflection на {@code BodyHandler} internals).
 *
 * <p>Production реализация - {@link HttpClientPdfFetcher} через
 * {@code java.net.http.HttpClient}. В тестах легко заменить mock'ом
 * который пишет известный content в target file.
 */
public interface PdfFetcher {

    /**
     * Скачивает {@code url} в {@code target} file полностью. Используется
     * для cache fill в {@link PdfLinksSourceProvider#locateFile}.
     *
     * @throws ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException
     *         при HTTP-ошибке или IO-проблеме. Частично скачанный файл
     *         удаляется при ошибке (best-effort)
     */
    void fetch(URI url, Path target);

    /**
     * Открывает streaming connection к upstream URL с поддержкой Range
     * header (25.d.5, ADR-023 amendment). Если {@code range != null} -
     * добавляет HTTP {@code Range: bytes=N-M} header, ожидает 206
     * Partial Content в ответ. Если upstream вернул 200 OK вместо 206
     * (Range не поддерживается) - стрим всё равно валиден,
     * {@link PdfStreamingResult#isPartial()} = false.
     *
     * <p>Caller обязан закрыть возвращённый stream
     * (try-with-resources). Bytes стримятся напрямую от upstream без
     * буферизации полного content в памяти.
     *
     * @throws ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException
     *         при HTTP-ошибке (4xx/5xx кроме 206/200) или IO-проблеме
     */
    PdfStreamingResult openStream(URI url, RangeSpec range);
}
