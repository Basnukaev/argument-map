# AI Editing (ADR-042, Этап 17.e; provider abstraction ADR-058)

LLM расставляет структуру (хадис-боксы, ayah-боксы, decorated
headings) поверх OCR raw text. **Без LLM работы платформа продолжает
функционировать** — просто `formatted_content` остаётся `null` и
фронт рендерит plain `text_content` (как до Этапа 17.e). AI edit —
optional enhancement, не блокер.

## Provider (swappable, ADR-058)

AI editing зависит от провайдер-агностичного интерфейса
`ru.basnukaev.argumentmap.ai.LlmClient` (тот же интерфейс использует
`BookMetadataExtractionService`). Активная реализация выбирается через
property `ai.provider` (env `AI_PROVIDER`):

- `anthropic` (default, matchIfMissing) — `AnthropicLlmClient`,
  Messages API, `claude-sonnet-4-6`
- `openai` — `OpenAiCompatibleLlmClient`, Chat Completions API, `gpt-4o`
- `deepseek` — `DeepSeekLlmClient` (subclass OpenAI-совместимого),
  `deepseek-chat`

Все клиенты — тонкие обёртки поверх `java.net.http.HttpClient` (без
heavy SDK). Ровно ОДИН `LlmClient` bean активен (conditionals
взаимоисключающие).

## Configuration через env vars

Переключение провайдера: `AI_PROVIDER=anthropic|openai|deepseek` +
соответствующий `*_API_KEY`. Каждый провайдер читает свой блок
`ai.{anthropic,openai,deepseek}.*`:

- `*_API_KEY` — default sentinel `disabled` → `LlmClient.isEnabled()`
  = false → AI endpoint вернёт 503, метаданные fall back на regex.
  Anthropic key — https://console.anthropic.com/settings/keys
- `*_MODEL` — default по провайдеру (claude-sonnet-4-6 / gpt-4o /
  deepseek-chat)
- `*_MAX_TOKENS` (default 4096)
- `*_TIMEOUT_SECONDS` (default 60)
- `*_BASE_URL` — override для testing / mock server (ANTHROPIC_BASE_URL
  / OPENAI_BASE_URL / DEEPSEEK_BASE_URL)

## Async pipeline

`AiEditService.enhanceAsync` уходит в `aiEditTaskExecutor` (core=2,
max=4, queue=50). Меньше OCR queue (50 vs 100) потому что задачи
дороже cost + блокированы LLM rate limits.

## Retry

Resilience4j `llmApi` instance (ADR-058, был `anthropicApi`) —
3 attempts с exponential backoff. `LlmTransientFailurePredicate`
ограничивает retry только transient ошибками (`IOException`,
`LlmApiException` со statusCode 0/429/5xx); permanent 4xx (400/401/
403/404) не повторяются.

## State machine

`lib_pages.ai_edit_status` (миграция 35): PENDING → PROCESSING →
DONE/FAILED. При DONE результат — валидный ProseMirror JSON в
`formatted_content`.

## Prompt template

В `resources/prompts/ai-edit-tahqiq.txt`. Few-shot examples + правила
распознавания. Изменения промпта — отдельный коммит + регрессия
через `AiEditServiceLiveIT` (опц).

## Graceful degradation

Если ключа активного провайдера нет, backend стартует нормально, AI
edit endpoint отдаёт 503 `ai-edit-not-configured`. Upstream-сбои LLM
маппятся в 502/503 `llm-api-error` (ADR-058, был `anthropic-api-error`).

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

- **Live IT тест** `ai.AiEditServiceLiveIT` (опционально через
  `mvn -Dgroups=live test -Dtest=AiEditServiceLiveIT`) — реальный
  вызов Anthropic API. Стоимость ~$0.01 на прогон. Запускать только
  при изменении prompt template / AnthropicLlmClient / model
- **IT через @MockBean** `AiEditServiceIT` + `AiEditControllerIT` —
  мокают `LlmClient`, проверяют state machine + JSON validation + REST
  mapping без реальных вызовов
- **HTTP-уровневые** тесты в `ai.AnthropicLlmClientStubIT` и
  `ai.OpenAiCompatibleLlmClientStubIT` через JDK HttpServer stub (тот
  же подход что у `HttpClientPdfFetcherRangeStreamingIT`)
