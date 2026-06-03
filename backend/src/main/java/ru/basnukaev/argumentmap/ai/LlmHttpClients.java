package ru.basnukaev.argumentmap.ai;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Фабрика {@link HttpClient} для LLM-клиентов с опциональным прокси (ADR-058).
 *
 * <p>Прокси задаётся per-client через {@code ai.http.proxy}
 * ({@code http://[user:pass@]host:port}) и навешивается ТОЛЬКО на
 * LLM-HttpClient — НЕ глобально через {@code https.proxyHost} (иначе через
 * прокси пошёл бы и внутренний S3/MinIO-трафик на localhost → 503 на старте).
 *
 * <p><b>Аутентификация прокси — ПРЕВЕНТИВНАЯ</b> ({@link #proxyAuthHeader}):
 * клиент шлёт {@code Proxy-Authorization: Basic ...} на CONNECT сам. Мы НЕ
 * используем builder {@link java.net.Authenticator}, потому что при заданном
 * Authenticator JDK HttpClient ВЫРЕЗАЕТ пользовательский заголовок
 * {@code Authorization} (серверный Bearer LLM-ключа) — DeepSeek/OpenAI тогда
 * отвечают 401. Превентивный Proxy-Authorization требует
 * {@code jdk.httpclient.allowRestrictedHeaders=proxy-authorization} +
 * {@code jdk.http.auth.tunneling.disabledSchemes=} (оба в
 * {@code ArgumentMapApplication} static-блоке).
 */
final class LlmHttpClients {

    private LlmHttpClients() {
    }

    static HttpClient build(int connectTimeoutSeconds, String proxyUrl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        InetSocketAddress addr = proxyAddress(proxyUrl);
        if (addr != null) {
            builder.proxy(ProxySelector.of(addr));
        }
        return builder.build();
    }

    /**
     * Значение заголовка {@code Proxy-Authorization} ({@code Basic base64(user:pass)})
     * из {@code ai.http.proxy}, либо {@code null} если прокси/креды не заданы.
     * Креды URL-декодируются (как в ShamelaHttpClientConfig).
     */
    static String proxyAuthHeader(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(proxyUrl.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            return null;
        }
        String[] parts = userInfo.split(":", 2);
        String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        String pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        String token = Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static InetSocketAddress proxyAddress(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(proxyUrl.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (uri.getHost() == null || uri.getPort() < 0) {
            return null;
        }
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }
}
