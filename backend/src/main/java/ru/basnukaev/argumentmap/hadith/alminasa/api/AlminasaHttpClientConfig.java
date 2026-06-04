package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link HttpClient} для alminasa ES-прокси (План 2, ADR-060). Опциональный
 * корп-прокси per-client (НЕ глобально — иначе внутренний S3/MinIO-трафик
 * пошёл бы через прокси). Authenticator-подход как у shamela: alminasa не
 * шлёт серверный Authorization, так что JDK-вырезание заголовка (gotcha
 * «LLM за корп-прокси») здесь не стреляет.
 *
 * <p>{@code alminasa.enabled=false} выключает ВСЕ alminasa-бины (клиент,
 * краулер, admin-endpoints → 404). Default on: прокси публичный read-only,
 * секретов для конструирования бинов не нужно.
 */
@Configuration
@EnableConfigurationProperties(AlminasaProperties.class)
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaHttpClientConfig {

    @Bean
    public HttpClient alminasaHttpClient(AlminasaProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()));
        String proxyUrl = props.httpProxy();
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI uri = URI.create(proxyUrl.trim());
            if (uri.getHost() != null && uri.getPort() > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(user, pass.toCharArray());
                        }
                    });
                }
            }
        }
        return builder.build();
    }
}
