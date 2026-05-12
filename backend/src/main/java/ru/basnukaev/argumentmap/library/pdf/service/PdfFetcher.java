package ru.basnukaev.argumentmap.library.pdf.service;

import java.net.URI;
import java.nio.file.Path;

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
     * Скачивает {@code url} в {@code target} file.
     *
     * @throws ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException
     *         при HTTP-ошибке или IO-проблеме. Частично скачанный файл
     *         удаляется при ошибке (best-effort)
     */
    void fetch(URI url, Path target);
}
