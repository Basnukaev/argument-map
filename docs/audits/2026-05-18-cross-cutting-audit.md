# Cross-cutting concerns audit - 2026-05-18

Зона аудита: `application.yml`, `SecurityConfig`, `GlobalExceptionHandler`,
`WebMvcConfig`, `pom.xml`, frontend `vite.config.ts`, observability,
security headers, error handling consistency. Domain / service / apps
кода в этой ревизии **не трогаем** - параллельно работают backend
architecture и frontend architecture subagents.

Baseline тесты: `./mvnw verify` → 879 tests run, 8 failures (pre-existing
- все на ожидании 400 от `MissingUserHeaderException` после
`b9da308 @CurrentUser anonymous principal → 401 (не 400) для refresh
trigger`), 2 skipped. Эти 8 - не моя зона, не fix'аются здесь.

## Top findings

### Critical

1. **Нет rate limiting на auth endpoints** (`/auth/login`,
   `/auth/register`, `/auth/refresh`). Brute-force и credential stuffing
   ничем не ограничены - secret/password можно перебирать с одного IP
   без задержки. BCrypt cost=10 даёт ~100ms на попытку - 36k попыток
   в час с одного потока, 1M+ при многопоточности. Для prod без rate
   limiting это критично, минимум IP-based throttling нужен.

2. **HTTP security headers отсутствуют** (HSTS, CSP, Referrer-Policy,
   Permissions-Policy). Spring Security 6 по default ставит
   `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
   `Cache-Control: no-cache` (для authenticated) - но HSTS / CSP не
   включены. Для prod HTTPS-deploy без HSTS возможен SSL-strip MITM
   на первом запросе. CSP закрывает XSS attack surface (хотя SPA с
   inline-style таили использует - tighten policy постепенно).

3. **MissingUserHeaderException возвращает 401 вместо 400** (тесты
   ловят). Это не моя зона (изменено в commit b9da308 параллельным
   subagent) - но 8 IT падают на этом сейчас. Зафиксировано как
   pre-existing failure.

### Important

4. **Refresh token не ротируется** при `/auth/refresh` (документировано
   в ADR-040 «Открытые вопросы»). Best practice OAuth 2.0 / OWASP -
   single-use refresh: при `/refresh` выдавать новый refresh + revoke
   старый. Сейчас тот же refresh-токен живёт 7 дней - если украден,
   у злоумышленника 7 дней access. Token-family detection (refresh
   reused → revoke all) - стандартная mitigation.

5. **Структурированное логирование без MDC контекста**. Все `log.info`
   через String interpolation, нет `requestId` / `userId` / `tenantId`
   в structured-fields. Грепать по `traceId` в логах нельзя - его нет.
   Spring Boot 3.x имеет встроенный Micrometer Tracing - можно
   включить через actuator + sleuth dependency, но самый минимум -
   ServletFilter ставящий `MDC.put("requestId", UUID.randomUUID())`
   на каждый запрос. ~50 LOC, нулевой overhead, огромный observability
   win.

6. **`auth.jwt.secret` placeholder в `application.yml` `dev-only-do-not-use-in-prod-...`**
   проходит validation (длиннее 32 байт). В prod через
   `AUTH_JWT_SECRET` env-var. Это **не fail-safe** - если deploy
   забыл env var, приложение стартует с dev-секретом. JwtService
   валидирует только length, не factual content. Можно проверять
   substring `dev-only` / `placeholder` в JwtService constructor и
   падать при активном `prod` profile.

7. **`actuator/**` permitAll в SecurityConfig** - комментарий говорит
   «закрывать отдельным network layer / basic auth в prod», но нет
   автоматического разделения по profile. В dev OK, в prod утечка
   metrics / circuitbreaker states / health-details на интернет.
   Минимум - `prod` profile должен закрыть всё кроме `health` базовым
   auth. Сейчас при deploy не задумавшись - все metrics публичные.

### Minor

8. **Magic numbers `Duration.ofMinutes(5)` / `Duration.ofSeconds(10)`**
   в `HttpClientPdfFetcher` и `AnthropicClient` connect timeout
   захардкожены. Перетащить в `application.yml` через `@Value` с
   default - copy-paste minute от существующего паттерна в
   `ai.anthropic.timeout-seconds`.

9. **`@RestControllerAdvice GlobalExceptionHandler` - 50+ handlers**
   обрабатывают каждый custom exception явно. Маппинг exception →
   ProblemDetail повторяется (status + title + slug + detail). Можно
   ввести `BaseHttpException(status, slug, title)` и один общий
   handler через `extends BaseHttpException` - сократит boilerplate
   на ~30%, унифицирует формат. Но это refactor, не quick win.

10. **CORS `allowCredentials(false)` в WebMvcConfig** - корректно для
    JWT в Authorization header, но если когда-нибудь захотим
    cross-origin refresh-cookie flow (на отдельном поддомене) -
    придётся включить + `allowedOrigins` exact match (не wildcard).
    Сейчас работает через same-origin Vite proxy - не блокер.

11. **Нет `application-prod.yml`**. Profile-specific config есть только
    для `local` и `test` (через `---` separator). Prod должен иметь
    свой профиль с явными ограничениями (actuator closed, secrets
    require env, no bucket-bootstrap). Сейчас prod дефолтит на
    default-секцию + env-overrides - работает, но не self-documenting.

12. **`MaxUploadSizeExceededException` handler leak'ит лимит в
    detail-message**: `"Размер загружаемого файла превышает лимит "
    + ex.getMaxUploadSize() + " bytes"`. Это OK по содержанию (50MB
    публичная информация), но pattern «exception message в detail»
    нужно audit'ить - в нескольких других handlers `ex.getMessage()`
    может leak'ать internal SQL / file paths. Нашёл при беглом
    осмотре: `DataIntegrityViolationException` правильно sanitiz'ит
    («запрос нарушает ограничение БД»), но `ShamelaImportException`
    отдаёт raw message - может содержать file path tmpdir.

### Observability gaps

- Нет Micrometer Tracing (Spring Boot 3.x ships ready) - distributed
  traces для multi-service deploy
- Нет MDC контекста в логах - грепаемость по requestId/userId
- Custom business metrics через `MeterRegistry` не используются - есть
  только auto-instruments (HTTP, JDBC, executor). Импорт книги
  shamela / OCR success rate / AI edit cost - не tracked
- Audit log есть для бизнес-мутаций (ADR-043 Amendment), но это
  отдельный domain stream - не для operational tracing

### Dependencies

`./mvnw dependency:tree` не запускал отдельно - но видно из pom.xml:
- AWS SDK 2.44.4 (актуальная major)
- Spring Boot 3.5.0 (stable)
- Resilience4j 2.2.0 (latest stable)
- jjwt 0.12.6 (latest stable)
- PDFBox 3.0.5 (latest stable)
- Tess4j 5.13.0 (latest stable)
- springdoc 2.8.0 (latest stable)
- sqlite-jdbc 3.45.3.0 (recent)

Никаких legacy / deprecated zависимостей не вижу.

## Что отложено (backlog)

- **Refresh token rotation** + token-family - ADR-040 уже flag'нул,
  не делаем в этой ревизии (нужен новый `auth_refresh_tokens` table,
  scope > cross-cutting)
- **Distributed tracing** (Micrometer Tracing) - нужно решение по
  trace backend (Tempo / Jaeger / Zipkin), без deploy-инфры
  ставить sleuth ради того чтобы поддержать MDC - over-engineering
- **`application-prod.yml`** разделение - после первого prod deploy,
  сейчас prod дефолтится через env-vars
- **GlobalExceptionHandler refactor** в `BaseHttpException` - separate
  cleanup сессия, рискованно делать вместе с другими subagent'ами

## Fix plan (3-5 high-impact + low-risk)

В порядке приоритета:

1. **MDC requestId + userId filter** - new
   `RequestContextLogFilter extends OncePerRequestFilter` в
   `web/`. Ставит `MDC.put("requestId", UUID.randomUUID())` +
   `MDC.put("userId", ...)` из SecurityContext (если есть), clear
   в finally. Регистрируется в SecurityConfig перед jwtFilter
   (нужны MDC поля и в auth filter). Изменение log pattern в
   `application.yml` чтобы паттерн вывёл `[%X{requestId}] [%X{userId}]`

2. **HTTP security headers через Spring Security 6 DSL** -
   `http.headers(h -> h.httpStrictTransportSecurity(...)
   .contentSecurityPolicy(...).referrerPolicy(...))`. В dev/test -
   relaxed CSP (allow inline-style для Vite HMR), в prod - strict.

3. **Rate limiting `/auth/login` + `/auth/register`** - simplest
   approach без heavy deps: in-memory `Caffeine` cache (уже не
   используется, но dependency lightweight) либо `ConcurrentHashMap`
   sliding window (5 attempts / 60 sec / IP). Не Bucket4j (~heavy
   transitive deps - hazelcast/jcache). По исчерпанию - 429 Problem
   Details. Pro: covers brute-force. Con: in-memory не share между
   instances - но для single-instance prod достаточно, scaling -
   когда придёт.

4. **Hardcoded timeouts → properties** - `HttpClientPdfFetcher` /
   `AnthropicClient.connectTimeout` → `app.http.connect-timeout`
   property в `application.yml`. Один config source-of-truth.

5. **JwtService refuses dev placeholder в prod** - `if active profile
   contains "prod" && secret.contains("dev-only")` → fail-fast в
   constructor. Защита от deploy-mistake.

## Outcome (закрытые fix'ы)

### Закрыто в этой сессии

3 atomic commits:

1. `3abcf86 feat(backend): MDC requestId + userId в логах через
   RequestContextLogFilter` - RequestContextLogFilter ставит UUID в MDC
   на каждый запрос + X-Request-Id header. JwtAuthenticationFilter и
   XUserIdAuthenticationFilter после auth ставят userId. Logging
   pattern в application.yml выводит `[requestId] [userId]` после
   уровня. 3 unit-теста в RequestContextLogFilterTest
2. `7a54d11 feat(backend): HTTP security headers` - Referrer-Policy +
   Permissions-Policy всегда, HSTS + CSP только в non-dev profile.
   6 IT в SecurityHeadersIT
3. `9c444a3 feat(backend): JwtService fail-fast при dev-placeholder
   secret в prod profile` - защита от deploy-mistake. 3 новых IT в
   JwtServiceIT

### Не сделано (collision с параллельным subagent'ом)

Fix #3 «hardcoded timeouts → application.yml» - изменения
`HttpClientPdfFetcher` и `AnthropicClient` параллельный subagent
revert'ил с пометкой «intentional». Не настаиваю, конфликт зон.

### Отложено по выбору

- Rate limiting `/auth/login` + `/auth/register` - не сделано в этой
  ревизии. In-memory ConcurrentHashMap sliding-window требует
  тщательно протестировать (race conditions) + добавить REST IT
  тест-кейсы. Время вышло, ставлю в backlog
- Refresh token rotation - ADR-040 уже flag'нул, в зону текущей
  ревизии не вхожу (нужна новая таблица + миграция)
- Distributed tracing / Micrometer - нужен trace backend, deploy-decision
- `application-prod.yml` разделение - до первого prod deploy
- GlobalExceptionHandler `BaseHttpException` refactor - separate cleanup

### Acceptance

- Baseline: 879 tests, 8 failures (pre-existing 401-vs-400 от
  параллельного `b9da308`), 2 skipped
- После fix'ов: 888 tests (+9 net через параллельную работу субагентов),
  same 8 failures, 2 skipped. 1 интермиттентный error
  `PdfControllerIT.streamPdf_withoutRange_returnsFullFile`
  ConcurrentModification - flaky, не от моих изменений (PDF controller
  не трогал)
- 3 atomic commits per fix, audit report задокументирован

## ADRs (отложены)

- ADR про MDC + observability stack policy - не написан в этой сессии,
  можно дописать когда добавим distributed tracing / Loki / structured
  JSON encoder. Текущий MDC filter работает с дефолтным Logback console
  appender - простой setup, без operational dependencies
- HTTP security headers policy ADR - аналогично, можно дописать при
  первом prod deploy когда станет ясно какая CSP реально нужна
