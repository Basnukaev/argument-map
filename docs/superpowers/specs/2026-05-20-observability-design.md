# Design spec: Observability - production readiness (logging, metrics, traces)

**Дата:** 2026-05-20
**Автор:** Абдула + brainstorming
**Статус:** approved, ожидает implementation plan
**Связанные ADR (existing):** ADR-040 (Auth), ADR-046 (Rate limit),
ADR-047 (Refresh rotation), ADR-048 (Actuator basic auth в prod)
**Связанные ADR (будут созданы):** ADR-051 (Structured JSON logging
+ MDC), ADR-052 (Prometheus metrics), ADR-053 (OpenTelemetry tracing),
ADR-054 (Frontend error reporting via Sentry), ADR-055 (SLO + alerting)

---

## Контекст

Проект переходит из MVP в production-grade. Сейчас observability ограничен:

- **Logging**: Logback default + custom pattern с MDC requestId/userId
  (`web/RequestContextLogFilter`). Plain-text - грепаемо локально, но
  не machine-parseable
- **Metrics**: Actuator подключён, Micrometer transitive. Expose
  ограничен `health,info,circuitbreakers`. `/actuator/prometheus`
  **отсутствует**, custom metrics нет (`MeterRegistry` нигде не used)
- **Tracing**: отсутствует. Async pipelines (OCR/AI/ETL) корреляция
  только через requestId, distributed picture не строится
- **Frontend**: `ErrorBoundary` → `console.error` (no remote sink).
  Window.onerror / unhandledrejection не captured
- **Health**: `objectStorage` indicator есть (HeadBucket ping), db
  auto-detect. Readiness/Liveness split не configured

В prod при первой реальной нагрузке: нет cross-link браузерной ошибки
с server log'ом, неясно где latency-затык (DB / AI / Storage / OCR),
очереди async pipelines невидимы, frontend exceptions теряются.

## Цель

Поднять observability до production-readiness:

1. Structured JSON logging для агрегаторов (Loki/ELK)
2. Prometheus metrics с custom business metrics
3. Distributed tracing async pipelines
4. Frontend error reporting в централизованный sink
5. SLO baseline + alerting rules

## Не входит

- Деплой агрегаторов (Loki/ELK/Grafana/Prometheus stack) - infra
  outside backend. Здесь только что **экспортирует** приложение
- APM full suite (Datadog/NewRelic) - SaaS vs self-host решение
  отложено. OTLP экспорт совместим с обоими
- RUM на фронте - Phase 2+. Минимум - error reporting
- Pre-built Grafana dashboards - отдельная задача после exposure

## 1. Current state inventory

- **Backend logging:** Logback `%5p [%X{requestId:-}] [%X{userId:-}]` +
  `RequestContextLogFilter` (Order = HIGHEST + 10) ставит MDC, кладёт
  `X-Request-Id` в response, `MDC.clear()` в finally.
- **Backend metrics:** actuator есть, Micrometer transitive. Custom
  `MeterRegistry` usage отсутствует (grep пустой). `resilience4j.metrics.enabled:
  true` - метрики готовы при наличии registry. `ThreadPoolTaskExecutor` beans
  (mvc/aiEdit/ocr) auto-instrumented при подключении registry.
- **Backend health:** `ObjectStorageHealthIndicator` (HeadBucket), db default.
  Liveness/Readiness не split. Anthropic API health нет.
- **Backend tracing:** полностью отсутствует.
- **Frontend:** `ErrorBoundary.componentDidCatch` → `console.error`, комментарий
  line 18 явно приглашает Sentry. Vite prod build без source maps по умолчанию.

## 2. Phase 1 - Structured logging + MDC enrichment

**Deliverable:** JSON логи с богатым MDC. Foundation для всего
остального (correlation через requestId/traceId).

### 2.1 Logback JSON encoder

Dependency: `net.logstash.logback:logstash-logback-encoder` (8.x).

`backend/src/main/resources/logback-spring.xml`:
- **default (local/dev/test)** - человекочитаемый pattern, цвета
- **prod** (`spring.profiles.active=prod`) - JSON через `LogstashEncoder`
  c includes для MDC, level, logger, thread, stack_trace

JSON структура:
```json
{
  "@timestamp": "...", "level": "INFO", "logger_name": "...",
  "thread_name": "...", "message": "...",
  "mdc": {"requestId": "uuid", "userId": "uuid", "traceId": "...",
          "spanId": "...", "operation": "..."},
  "stack_trace": "..."
}
```

### 2.2 MDC enrichment

Расширить `RequestContextLogFilter`:
- Добавить `httpMethod`, `httpPath` в MDC (упрощает grep по типу
  запроса). Cleanup в finally
- Извлекать `X-Correlation-Id` header если клиент прислал - использовать
  его вместо генерации UUID. Совместимо с Sentry request linking
- Пробрасывать correlation id в `X-Request-Id` response header

### 2.3 Per-context MDC helper

`MdcContext implements AutoCloseable` (push/pop): try-with-resources
для long-running операций.

```java
try (var ctx = MdcContext.with("operation", "OcrService.process",
                                "pageId", pageId.toString())) { ... }
```

Используется в `OcrService.processPage`, `AiEditService.editPage`,
`ShamelaBookImportService.importBook`, `ObjectStorageService`
upload/download (с `bucket`, без full key для privacy).

### 2.4 Уровень логирования

- Default INFO для `ru.basnukaev.*`, WARN для frameworks
- ENV override `LOGGING_LEVEL_RU_BASNUKAEV=DEBUG` (Spring Boot стандарт)

### 2.5 Sensitive data redaction

`SensitiveDataMaskingFilter` (Logback Filter) маскирует regex'ами:
- JWT pattern → `[JWT_REDACTED]`
- `Bearer\s+\S+` → `Bearer [REDACTED]`
- `sk-ant-api[\w-]+` → `[ANTHROPIC_KEY_REDACTED]`
- email regex (safety net, по политике emails в логи попадать
  не должны изначально)

**Запрещено в логах:** email, username, JWT raw, password hashes,
secrets, IP вне rate-limit/audit. **userId UUID разрешён** -
pseudo-anonymous.

### 2.6 Retention

В prod пишем в **stdout** - rotation делает Docker/k8s + aggregator.
Файловую rotation в приложении **не делаем** (избегаем duplicated state).

## 3. Phase 2 - Metrics (Micrometer + Prometheus)

**Deliverable:** `/actuator/prometheus` endpoint + custom business metrics.

### 3.1 Dependency + expose

`io.micrometer:micrometer-registry-prometheus` - auto-detection Spring
Boot. `application.yml`:
```yaml
management:
  endpoints.web.exposure.include: health,info,circuitbreakers,prometheus,metrics
  metrics:
    distribution:
      percentiles-histogram: { http.server.requests: true }
      sla: { http.server.requests: 100ms,300ms,500ms,1s,3s }
    tags: { application: argument-map, env: ${SPRING_PROFILES_ACTIVE:local} }
```

`/actuator/prometheus` под тем же basic auth что остальной actuator
(ADR-048).

### 3.2 Auto-collected (без кода)

- `http.server.requests` - latency histogram per URI template + status
- `jvm.{memory,gc,threads,classes}.*`
- `hikaricp.connections.*`, `jdbc.connections.*`
- `executor.*` для всех `ThreadPoolTaskExecutor` beans (queue, active,
  completed)
- `tomcat.{sessions,threads}.*`
- Resilience4j circuit breaker / retry метрики (уже enabled)

### 3.3 Custom business metrics

**Counter:**
- `argmap.auth.login.attempts{result=success|failure|rate_limited}`
- `argmap.auth.register.attempts{...}`
- `argmap.auth.refresh.rotations{result=success|reuse_detected}` -
  ADR-047 steal detection видна
- `argmap.permission.denied{entity=topic|book|qa, action=read|write}`
- `argmap.shamela.books.imported{result=success|failure}`
- `argmap.audit.events{type=create|update|delete|visibility|member}`
- `argmap.rate_limit.hits{endpoint=login|register, result=allowed|blocked}`

**Timer / Histogram:**
- `argmap.ai.edit.duration{result=success|failure}` - в `AiEditService`
- `argmap.ocr.duration{result, language=ara|rus|eng}`
- `argmap.shamela.import.duration` per book
- `argmap.storage.operation.duration{bucket, operation=upload|download|head}`

**Gauge:**
- `argmap.ai.edit.queue.depth` (`aiEditTaskExecutor` queue size)
- `argmap.ocr.queue.depth`
- `argmap.refresh_tokens.active` (scheduled @5min, не на каждый scrape)
- `argmap.users.total`, `argmap.library.files.total`

Helper `ArgmapMetrics` собирает named meters в `@PostConstruct`,
сервисы injectят и вызывают `metrics.recordAiEditDuration(...)`,
`metrics.incrementLoginAttempt(...)`.

**Anti-pattern:** UUID-ы в label values **запрещены** (unbounded
cardinality kills Prometheus). userId/bookId/requestId - только
в trace attributes / log MDC, не в metrics labels.

### 3.4 Health checks - readiness + liveness split

```yaml
management.endpoint.health:
  probes.enabled: true
  group:
    readiness: { include: db,objectStorage }
    liveness: { include: ping }
```

- `/actuator/health/liveness` - JVM жив
- `/actuator/health/readiness` - готов принимать траффик (db + S3)

**Anthropic API health - НЕ делаем:** ключ может отсутствовать
(ADR-042 graceful degradation), `/messages` ping стоит денег,
external HTTP в liveness probe - anti-pattern. Детектируется через
`argmap.ai.edit.duration{result=failure}` гистограмму.

## 4. Phase 3 - Distributed tracing (OpenTelemetry)

**Deliverable:** OTLP-export trace coverage endpoints + async pipelines.

### 4.1 Подключение

`io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`
(2.x stable). Auto-instruments:
- Spring WebMVC (server spans)
- HTTP client (Anthropic, Shamela API calls)
- JDBC (queries auto-trace)
- Spring `@Async` propagation - **критично** для AI/OCR пайплайнов
- Logback MDC - `traceId`/`spanId` automatic → JSON log включает
  их → cross-link с trace UI

### 4.2 Manual spans

`@WithSpan("AiEditService.editPage")` + `@SpanAttribute` на
key параметрах. Места: `AiEditService.editPage`, `OcrService.processPage`,
`ShamelaBookImportService.importBook`, `ObjectStorageService.upload/download`.

### 4.3 Configuration

```yaml
otel:
  service.name: argument-map-backend
  exporter.otlp.endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
  traces:
    sampler: ${OTEL_TRACES_SAMPLER:parentbased_traceidratio}
    sampler.arg: ${OTEL_TRACES_SAMPLER_ARG:0.1}
  metrics.exporter: none  # уже через Prometheus
  logs.exporter: none     # уже через Logback stdout
```

10% sampling - reasonable для prod. При дебаге - `OTEL_TRACES_SAMPLER_ARG=1.0`
либо `curl -H "traceparent: ..."` для принудительной выборки.

Сервер receiver-агностичен (Jaeger/Tempo/Honeycomb/Datadog - всё
OTLP).

### 4.4 Frontend tracing - **отложено** (Phase 5+)

`@opentelemetry/sdk-trace-web` даёт automatic spans на `fetch` -
**не делаем сейчас** (bundle penalty ~50KB, low ROI пока нет real
perf issues). Готовность: один fetch wrapper `shared/api/client.ts` -
точка подключения когда понадобится.

## 5. Phase 4 - Frontend error reporting (Sentry)

**Deliverable:** runtime exceptions → централизованный sink со
stack-trace, breadcrumbs, request id для cross-link с backend.

### 5.1 Setup

`@sentry/react`. `src/shared/observability/sentry.ts`:

```ts
if (import.meta.env.VITE_SENTRY_DSN) {
  Sentry.init({
    dsn: ..., release: VITE_APP_VERSION, environment: MODE,
    tracesSampleRate: 0.1,
    integrations: [Sentry.browserTracingIntegration(),
                   Sentry.replayIntegration({maskAllText: true, blockAllMedia: true})],
    beforeSend(event) {
      // strip PII - email/username/IP. userId UUID оставляем
      if (event.user) { delete event.user.email; delete event.user.username;
                        delete event.user.ip_address; }
      return event;
    },
  });
}
```

При `VITE_SENTRY_DSN` пустом - не инициализируется, zero overhead.
Default в dev/local.

### 5.2 ErrorBoundary integration

`componentDidCatch` дополняется:
```ts
Sentry.captureException(error, { contexts: { react: { componentStack: info.componentStack } } });
```

### 5.3 API client integration

`shared/api/client.ts`: на 5xx → breadcrumb с request id из
`X-Request-Id` header. На network error → `captureException` с tag
`api.endpoint`. **Не** capture 4xx (expected по бизнес-логике).

### 5.4 Window error handlers

Sentry.init подцепляет `window.onerror` + `unhandledrejection`
automatic. ErrorBoundary ловит только React render, остальное -
Sentry.

### 5.5 Source maps

`vite.config.ts`: `build.sourcemap: 'hidden'`. Map files генерятся
без reference в bundle. Загружаются в Sentry через `@sentry/vite-plugin`
при `SENTRY_AUTH_TOKEN`. Если token не задан - билд работает без
upload (graceful).

### 5.6 Provider choice

**Sentry self-hosted** recommended для приватности (исламский
use-case, PII concerns). Альтернативы: Sentry SaaS (third-party
data), Glitchtip (OSS minimal). Спека пишет абстрактно через
стандартный capturer; deployment решается на момент prod setup.

### 5.7 Web Vitals - **отложено** YAGNI до perf regression.

## 6. Phase 5 - Alerting + SLO

**Deliverable:** Prometheus alerts + runbooks. Без alerts metrics
- dead data.

### 6.1 SLO baseline

| Indicator | Target | Window |
|---|---|---|
| Availability (readiness uptime) | 99.5% | 30d |
| HTTP p95 latency (ex AI/OCR) | < 500ms | 30d |
| HTTP p99 latency | < 1.5s | 30d |
| HTTP error rate (5xx/total) | < 0.5% | 30d |
| AI edit success rate (когда enabled) | > 90% | 7d |
| OCR success rate | > 95% | 7d |
| Storage p99 latency | < 2s | 7d |

Availability - через blackbox prober (infra-side, не приложение).

### 6.2 Alerting rules

`prometheus/alerts.yml` (infra repo deploy):

- **HighErrorRate** - 5xx rate > 5% for 5m → warn
- **HighP99Latency** - p99 > 2s for 10m → warn
- **DatabaseDown** - health probe DOWN for 1m → critical
- **StorageDown** - аналогично → critical
- **CircuitBreakerOpen** - `state==open` for 5m → warn
- **AiEditQueueBackup** / **OcrQueueBackup** - queue > 100 for 15m → warn
- **RateLimitSurge** - blocked rate > 10/s → warn (атака?)
- **RefreshTokenReuse** - any reuse detected → critical (ADR-047)
- **AuthFailureSurge** - login failure 3x baseline for 5m → warn (brute force)

### 6.3 Routing

Prometheus → AlertManager → **Telegram bot** primary (solo-проект,
24/7 на телефоне). Email fallback. Slack/Discord не нужно.

Alert payload: rule + severity + traceId (link Jaeger) + Grafana
link + runbook URL.

### 6.4 Runbooks

`docs/runbooks/<alert-name>.md`: Symptom / Likely causes / Diagnostic
steps / Remediation. Минимум на старте: DatabaseDown, StorageDown,
CircuitBreakerOpen, RefreshTokenReuse (security incident protocol).

## 7. Phasing

| Фаза | Что | Зависит | Часы |
|---|---|---|---|
| **51.a** | Logback JSON + MDC ext + redaction + ADR-051 | - | 3.5 |
| **51.b** | MdcContext + AI/OCR/Shamela integration + IT | 51.a | 2.5 |
| **52.a** | Prometheus dep + actuator expose | 51.a | 1.5 |
| **52.b** | ArgmapMetrics + 8 counters | 52.a | 3 |
| **52.c** | Timers + gauges + IT | 52.b | 3 |
| **52.d** | Liveness/Readiness probes + ADR-052 | 52.a | 1.5 |
| **53.a** | OTel SDK + auto-instrumentation + OTLP | 51.a | 3 |
| **53.b** | @WithSpan manual + ADR-053 | 53.a | 2 |
| **54.a** | Sentry setup + ErrorBoundary + ADR-054 | - | 2.5 |
| **54.b** | API client breadcrumbs + Vite source maps | 54.a | 2 |
| **55.a** | SLO doc + 5 rules + Telegram routing | 52.c, 53.b | 3 |
| **55.b** | Runbooks (4 critical) + ADR-055 | 55.a | 2 |
| **Итого** | | **21 commits** | **~29.5 ч** |

Реалистично - **3-4 сессии**. Естественные handoff: после 52.d
(metrics готовы), после 53.b (traces готовы), после 54.b (фронт
reporting), 55 - финал.

## 8. Acceptance criteria

### Phase 1 (Logging + MDC)

- [ ] `logback-spring.xml` с prod-profile JSON encoder
- [ ] `RequestContextLogFilter` пишет httpMethod/httpPath + accept X-Correlation-Id
- [ ] `MdcContext` AutoCloseable используется в AI/OCR/Shamela
- [ ] `SensitiveDataMaskingFilter` маскирует JWT/Bearer/Anthropic key
- [ ] IT: запрос с X-Correlation-Id попадает в логи как requestId
- [ ] `SPRING_PROFILES_ACTIVE=prod` - первая строка валидный JSON `jq`'абельна
- [ ] ADR-051

### Phase 2 (Metrics)

- [ ] `/actuator/prometheus` с basic auth возвращает text/plain Prometheus формат
- [ ] `http_server_requests_seconds_bucket` histogram buckets
- [ ] `argmap_auth_login_attempts_total{result="success"}` инкрементируется (IT)
- [ ] `argmap_ai_edit_queue_depth` / `argmap_ocr_queue_depth` экспонированы
- [ ] `/actuator/health/{liveness,readiness}` отвечают раздельно
- [ ] ADR-052

### Phase 3 (Tracing)

- [ ] OTel SDK подключён, `service.name=argument-map-backend`
- [ ] Запрос с traceparent header пробрасывается через @Async (controller →
  AI edit → Anthropic call) - один traceId
- [ ] MDC получает traceId/spanId в каждой JSON записи
- [ ] При пустом `OTEL_EXPORTER_OTLP_ENDPOINT` - SDK молчит, no startup fail
- [ ] ADR-053

### Phase 4 (Frontend reporting)

- [ ] При `VITE_SENTRY_DSN` пустом - bundle не растёт (before/after compare)
- [ ] Искусственная ошибка из ErrorBoundary попадает в Sentry с component stack
- [ ] PII (email/username) не попадает - beforeSend проверка
- [ ] Vite prod: source maps в `dist/.sourcemaps/`, не referenced в bundle
- [ ] ADR-054

### Phase 5 (Alerting + SLO)

- [ ] `docs/slo.md` фиксирует baseline
- [ ] `prometheus/alerts.yml` имеет 5 правил минимум
- [ ] AlertManager routing на Telegram (env-config описан)
- [ ] Runbooks для 4 critical в `docs/runbooks/`
- [ ] ADR-055

### Cross-phase

- [ ] `docs/architecture.md` Observability раздел
- [ ] `docs/gotchas.md` обновлён (logback profile conflict, micrometer
  naming, OTel async propagation pitfall)
- [ ] Code review через `/superpowers:requesting-code-review` после каждой фазы

## 9. Risks / open questions

### Q1: PII в логах

userId UUID - **разрешён** (pseudo-anonymous). Email/username -
**запрещены** (`SensitiveDataMaskingFilter` + code review). IP -
**только** в rate-limit/audit context (security legitimate interest).
Tomcat default access logs - **выключить**.

Arabic content из book pages - truncate string args на 200 символов
через Logback `%msg(200)` (storage size, не privacy).

### Q2: Log volume / cost

JSON logs ~3x объём plain. INFO + active users → ГБ/день.

**Mitigation:** auth/permission/AI/OCR - INFO; CRUD endpoints
downgrade на DEBUG. Prod aggregator: 7d hot + 30d cold (infra-level).
При первой реальной нагрузке - переосмыслить sampling.

### Q3: Self-hosted vs SaaS

- **Datadog** - всё в одном, дорого, sample-based pricing хитрый
- **Grafana Cloud free tier** - 50GB logs / 10K series / 50GB traces -
  достаточно для ~100 active users
- **Self-host** (Prom+Loki+Tempo+Grafana) - max control, требует ops

**Решение:** пишем абстрактно (OTLP/Prom/JSON stdout) - любой backend
подцепится. Phase 4-5 - **Grafana Cloud free**, zero ops для solo.

### Q4: Frontend bundle penalty

Sentry full SDK + replay → +130KB gzip. **Mitigation:** lazy init
если DSN задан (dynamic import), replay сменяемый. Target бюджет:
total observability < +180KB gzip. OTel frontend - не делаем пока
бюджет не освобождён.

### Q5: OTel vs Sleuth

Spring Cloud Sleuth deprecated в Spring Boot 3. **OTel напрямую** -
актуальный путь.

### Q6: Cardinality explosion

UUID-ы в Prometheus labels - **запрещены**. Whitelist label values:
`endpoint` (URI template), `result` (enum), `kind` (enum),
`bucket` (имена ограничены 4). User/book/topic/request id - в trace
attributes / log MDC.

### Q7: Тестирование metrics в IT - flaky?

Counter assertions могут разъезжаться при shared MeterRegistry.

**Решение:** `meterRegistry.clear()` в `@BeforeEach` или новый
registry per test через `@TestConfiguration`. Assertions через
`>= 1.0` не точное равенство.

### Q8: Cold start latency

Lazy init `MeterRegistry`/Sentry/OTel exporter даёт выброс первого
запроса.

**Mitigation:** `@PostConstruct` warmup в `ArgmapMetrics` (регистрация
meters заранее). OTel exporter прогревается через synthetic startup
trace. Sentry init - до first React render в `main.tsx`.

### Q9: Trace sampling sticky?

10% parentbased: если upstream прислал `traceparent` - respect его
decision (deterministic). Иначе 10% random. Edge case для дебага:
`curl -H "traceparent: ..."` принудительная выборка конкретного
trace.

### Q10: Arabic encoding в JSON логах

`logstash-encoder` default UTF-8, `escape-non-ascii=false`. Arabic
**не** превращается в `\uXXXX`. Проверить вручную на первом prod
log'е (gotcha если default изменится в major version).

## 10. Decomposition (для implementation plan)

1. **51.a** - Logback JSON config (prod profile) + MDC extension +
   redaction filter + ADR-051
2. **51.b** - MdcContext AutoCloseable + integration в Async services
3. **52.a** - Micrometer Prometheus registry + actuator expose + tags
4. **52.b** - ArgmapMetrics helper + business counters (auth/permission/shamela)
5. **52.c** - Custom timers (AI/OCR/storage) + gauges (queue depth) + IT
6. **52.d** - Liveness/Readiness probes split + ADR-052
7. **53.a** - OpenTelemetry SDK + auto-instrumentation + OTLP + smoke test
8. **53.b** - Manual `@WithSpan` на AI/OCR/Shamela/Storage + ADR-053
9. **54.a** - Sentry frontend + ErrorBoundary integration + ADR-054
10. **54.b** - API client breadcrumbs + Vite source maps plugin
11. **55.a** - SLO doc + 5 alerting rules + AlertManager Telegram
12. **55.b** - Runbooks (4 critical) + ADR-055 + handoff
