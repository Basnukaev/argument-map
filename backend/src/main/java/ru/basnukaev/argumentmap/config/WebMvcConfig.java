package ru.basnukaev.argumentmap.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import ru.basnukaev.argumentmap.web.CurrentUserArgumentResolver;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final String[] allowedOrigins;

    public WebMvcConfig(
            CurrentUserArgumentResolver currentUserArgumentResolver,
            @Value("${app.cors.allowed-origins:}") String allowedOriginsRaw
    ) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                // Range нужен для PDF Viewer (react-pdf делает range
                // requests через PDF.js). Без него preflight отклоняет
                // запрос или браузер блокирует Range, PDF.js не получит
                // partial content и упадёт с InvalidPDFException
                .allowedHeaders("Content-Type", "Authorization", "Idempotency-Key",
                        "X-User-Id", "Range")
                // Content-Range/Accept-Ranges/Content-Length нужны
                // PDF.js чтобы понять размер файла и прогресс. Без
                // expose - JS получает headers как `undefined`
                .exposedHeaders("Location", "Content-Range", "Accept-Ranges",
                        "Content-Length")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
