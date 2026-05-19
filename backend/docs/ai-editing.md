# AI Editing (ADR-042, Этап 17.e)

LLM расставляет структуру (хадис-боксы, ayah-боксы, decorated
headings) поверх OCR raw text. **Без LLM работы платформа продолжает
функционировать** — просто `formatted_content` остаётся `null` и
фронт рендерит plain `text_content` (как до Этапа 17.e). AI edit —
optional enhancement, не блокер.

## Provider

Anthropic Claude (`claude-sonnet-4-6`) через raw
`java.net.http.HttpClient` (~100 LOC). Без Anthropic Java SDK — не
оправдывает heavy dep для одного endpoint.

## Configuration через env vars

- `ANTHROPIC_API_KEY` — получить на
  https://console.anthropic.com/settings/keys. Default `disabled` —
  endpoint вернёт 503 пока не установлен
- `ANTHROPIC_MODEL` (default `claude-sonnet-4-6`)
- `ANTHROPIC_MAX_TOKENS` (default 4096)
- `ANTHROPIC_TIMEOUT_SECONDS` (default 60)
- `ANTHROPIC_BASE_URL` — override для testing / mock server

## Async pipeline

`AiEditService.enhanceAsync` уходит в `aiEditTaskExecutor` (core=2,
max=4, queue=50). Меньше OCR queue (50 vs 100) потому что задачи
дороже cost + блокированы Anthropic rate limits.

## Retry

Resilience4j `anthropicApi` instance — 3 attempts с exponential
backoff на `AnthropicApiException` + `IOException`. 401/403 формально
retry'ются, но повторно fail (acceptable).

## State machine

`lib_pages.ai_edit_status` (миграция 35): PENDING → PROCESSING →
DONE/FAILED. При DONE результат — валидный ProseMirror JSON в
`formatted_content`.

## Prompt template

В `resources/prompts/ai-edit-tahqiq.txt`. Few-shot examples + правила
распознавания. Изменения промпта — отдельный коммит + регрессия
через `AiEditServiceLiveIT` (опц).

## Graceful degradation

Если ключа нет, backend стартует нормально, AI edit endpoint отдаёт
503 `ai-edit-not-configured`.

## Curl example

Для smoke (после установки ANTHROPIC_API_KEY + рестарт backend):

```bash
PAGE_ID=$(psql -h localhost -U argmap argumentmap -tA \
  -c "select id from lib_pages where text_content is not null limit 1")
# Триггер
curl -X POST "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
# Polling
curl "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
```

## Тестирование

- **Live IT тест** `AiEditServiceLiveIT` (опционально через
  `mvn -Dgroups=live test -Dtest=AiEditServiceLiveIT`) — реальный
  вызов Anthropic API. Стоимость ~$0.01 на прогон. Запускать только
  при изменении prompt template / AnthropicClient / model
- **IT через @MockBean** `AiEditServiceIT` + `AiEditControllerIT` —
  не делают реальных вызовов, проверяют state machine + JSON
  validation + REST mapping
- **HTTP-уровневые** тесты в `AnthropicClientStubIT` через JDK
  HttpServer stub (тот же подход что у
  `HttpClientPdfFetcherRangeStreamingIT`)
