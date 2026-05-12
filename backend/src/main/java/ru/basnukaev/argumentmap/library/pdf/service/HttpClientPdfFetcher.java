package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;

/**
 * Production реализация {@link PdfFetcher} через
 * {@code java.net.http.HttpClient}. Использует тот же
 * {@code shamelaHttpClient} bean что и shamela ETL - с corporate
 * proxy support из {@code ShamelaHttpClientConfig}.
 */
@Component
public class HttpClientPdfFetcher implements PdfFetcher {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;

    public HttpClientPdfFetcher(@Qualifier("shamelaHttpClient") HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void fetch(URI url, Path target) {
        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() / 100 != 2) {
                Files.deleteIfExists(target);
                throw new ShamelaApiException(
                        "PDF download вернул HTTP " + resp.statusCode() + " на " + url);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // частично скачанный файл оставляем для диагностики
            }
            throw new ShamelaApiException(
                    "ошибка PDF download: " + url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван PDF download: " + url, e);
        }
    }
}
