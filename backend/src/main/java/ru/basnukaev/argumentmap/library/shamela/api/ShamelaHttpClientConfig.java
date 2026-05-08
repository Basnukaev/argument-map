package ru.basnukaev.argumentmap.library.shamela.api;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация HTTP-клиента для shamela API. Подхватывает прокси
 * из env-переменных {@code HTTPS_PROXY} / {@code SHAMELA_PROXY}
 * автоматически - стандартный {@link ProxySelector#getDefault()} в Java
 * читает только {@code -Dhttps.proxyHost} JVM-property, env-переменные
 * сам игнорирует. Для удобства разработчика (особенно за корпоративным
 * прокси) разворачиваем {@code HTTPS_PROXY} вручную.
 *
 * <p>Приоритет: {@code SHAMELA_PROXY} (точечный override для shamela)
 * &gt; {@code HTTPS_PROXY} (общий env-прокси) &gt; direct connection.
 *
 * <p>Поддерживается формат {@code http://user:pass@host:port}
 * (HTTPS-прокси с basic auth). Креды извлекаются из URI и подаются
 * через {@link Authenticator}, потому что Chromium-style передача
 * {@code user:pass@} в URL прокси Java HttpClient не поддерживает.
 */
@Configuration
@EnableConfigurationProperties(ShamelaApiProperties.class)
public class ShamelaHttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ShamelaHttpClientConfig.class);

    @Bean
    public HttpClient shamelaHttpClient(ShamelaApiProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL);

        String proxyEnv = firstNonBlank(System.getenv("SHAMELA_PROXY"), System.getenv("HTTPS_PROXY"));
        if (proxyEnv != null) {
            applyProxy(builder, proxyEnv);
        } else {
            log.info("shamela HTTP-клиент: прямое соединение (HTTPS_PROXY не задан)");
        }
        return builder.build();
    }

    private static void applyProxy(HttpClient.Builder builder, String proxyUrl) {
        URI uri;
        try {
            uri = URI.create(proxyUrl);
        } catch (IllegalArgumentException e) {
            log.warn("shamela HTTP-клиент: HTTPS_PROXY невалидный URL ({}), используется прямое соединение", e.getMessage());
            return;
        }
        if (uri.getHost() == null || uri.getPort() < 0) {
            log.warn("shamela HTTP-клиент: HTTPS_PROXY без host/port - используется прямое соединение");
            return;
        }
        builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
        log.info("shamela HTTP-клиент: прокси {}:{} (auth={})",
                uri.getHost(), uri.getPort(), uri.getUserInfo() != null);

        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            return;
        }
        String[] parts = userInfo.split(":", 2);
        String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        char[] pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8).toCharArray();
        builder.authenticator(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // отвечаем только на запросы прокси-сервера, не на target-сайт
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(user, pass);
                }
                return null;
            }
        });
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
