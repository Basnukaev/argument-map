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
 * Конфигурация HTTP-клиента для shamela API.
 *
 * <p><b>По умолчанию - прямое соединение</b>. HTTPS_PROXY env var,
 * JVM-property {@code -Dhttps.proxyHost} и {@link ProxySelector#getDefault()}
 * системные настройки <b>игнорируются</b> через явный
 * {@code .proxy(ProxySelector.of(null))}. Причина: shamela.ws - внешний
 * домен, не за corporate firewall, прокси для него обычно не нужен.
 * Если корпоративный/paid прокси перехватывает запрос - получаем 407
 * Proxy Authentication Required (даже если HTTPS_PROXY содержит
 * credentials - они могут быть stale либо прокси не whitelist'ит
 * shamela). См. gotcha «shamela API из WSL2 требует VPN/прокси».
 *
 * <p><b>Override через {@code SHAMELA_PROXY} env var</b> - если
 * действительно нужно проксировать shamela трафик (network egress
 * restriction). Формат {@code http://user:pass@host:port}, креды
 * извлекаются и подаются через {@link Authenticator} (Java HttpClient
 * не поддерживает {@code user:pass@} в URL прокси напрямую).
 *
 * <p>Sentinel values для no-op: {@code SHAMELA_PROXY=direct} или
 * {@code SHAMELA_PROXY=none} - явный direct connect (полезно для
 * отладки если непонятно почему прокси активен).
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

        // SHAMELA_PROXY - точечный override ТОЛЬКО для shamela трафика.
        // HTTPS_PROXY НЕ читается - это глобальный corp/paid прокси, который
        // для shamela.ws обычно не нужен (домен внешний, доступен напрямую).
        // Если задан HTTPS_PROXY с credentials - мы получали бы 407 потому что
        // proxy server либо не разрешает shamela, либо креды stale.
        // Чтобы принудительно проксировать shamela - SHAMELA_PROXY=http://...
        String proxyEnv = System.getenv("SHAMELA_PROXY");
        boolean useProxy = proxyEnv != null && !proxyEnv.isBlank()
                && !"direct".equalsIgnoreCase(proxyEnv)
                && !"none".equalsIgnoreCase(proxyEnv);
        if (useProxy) {
            applyProxy(builder, proxyEnv);
        } else {
            // ProxySelector.of(null) форсит прямое подключение - игнорирует
            // ProxySelector.getDefault() (читает JVM -Dhttps.proxyHost и
            // системные настройки), а также игнорирует HTTPS_PROXY env var
            builder.proxy(ProxySelector.of(null));
            log.info("shamela HTTP-клиент: прямое соединение (SHAMELA_PROXY={}, HTTPS_PROXY игнорируется)",
                    proxyEnv == null ? "не задан" : proxyEnv);
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
        // Java HttpClient с 8u11+ блокирует Basic auth для HTTPS-туннеля
        // (CONNECT method) по умолчанию через jdk.http.auth.tunneling.disabledSchemes=Basic.
        // Без этого Authenticator не вызывается на 407 challenge и запрос фейлится.
        // Снимаем блок именно для tunneling - это глобально на JVM, но безопасно
        // (Basic через TLS защищён шифрованием канала).
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
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
