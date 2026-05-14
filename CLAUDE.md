# CLAUDE.md - argument-map quick start

Быстрый старт для новых сессий Claude Code в этом проекте. Полный
контекст с автономным режимом и протоколом сессии - в
`docs/SESSION_START_PROMPT.md`.

## Стэк

- **Backend:** Java 21, Spring Boot 3.5, JDBC Template (без JPA),
  Postgres 16, Liquibase, Testcontainers
- **Frontend:** React 19, Vite 6, Tailwind v4, React Flow, Zustand 5,
  openapi-typescript, Vitest
- **Инфра:** Docker Compose для Postgres, опционально MinIO

## Структура

```
backend/                     - Java модуль (Maven)
  CLAUDE.md                  - backend-specific правила и команды
  docs/                      - coding-standards, antipatterns, testing-strategy
frontend/                    - React/TS модуль
  CLAUDE.md                  - frontend-specific правила и команды
  src/apps/                  - три приложения под ADR-018 platform pivot
    argument-map/            - граф аргументации (pages, components/graph, utils)
    library/                 - библиотека книг (BookList, BookReader, PdfViewer)
    admin/                   - admin tooling (AdminShamelaPage)
  src/shared/                - cross-app код (api, components/{layout,ui}, stores, utils, types)
  design-reference/          - статические дизайн-референсы (НЕ ТРОГАТЬ)
docs/                        - вся общая документация
  archive/                   - архив старых сессий progress
  superpowers/               - specs, plans, audits для крупных работ
scripts/                     - утилиты (seed-mawlid.sh и др.)
docker-compose.yml           - postgres (опционально minio)
```

## Команды

```bash
# Backend (все из backend/, всегда в WSL2)
./mvnw verify              # полный билд + тесты
./mvnw test                # только unit + IT через Testcontainers
./mvnw -DskipTests compile # быстрая компиляция

# Backend dev-сервер (Claude запускает сам в фоне, с JDWP для дебага)
# Абдула подключается IntelliJ Remote JVM Debug к localhost:5005
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
# готовность: until curl -sf http://localhost:9090/actuator/health; do sleep 2; done

# Frontend (все из frontend/, всегда в WSL2)
npm test -- --run          # все Vitest тесты
npm run build              # production build
npm run generate-api       # регенерация types.ts (требует backend running)
npx tsc --noEmit -p tsconfig.app.json   # typecheck
npm run dev > /tmp/frontend.log 2>&1 &  # dev на :5173 (Claude запускает сам)

# Инфра
docker compose up -d       # postgres
scripts/seed-mawlid.sh     # тестовая тема для argument-map
```

## Документация (в порядке важности для новой сессии)

1. **`docs/SESSION_START_PROMPT.md`** - стартовый промпт: автономный
   режим, эскалация, red lines, текущий приоритет
2. **`docs/roadmap.md`** - активные этапы (закрытые свёрнуты в строку)
3. **`docs/backlog.md`** - идеи без привязки к этапу
4. **`docs/decisions.md`** - ADR'ы (фундаментальные решения, последние
   важные: ADR-018 platform pivot, ADR-022 frontend reorg, ADR-024
   object storage, ADR-028 academic citation)
5. **`docs/gotchas.md`** - известные ловушки и обходы
6. **`docs/architecture.md`** + **`docs/architecture-platform.md`**
7. **`docs/api-contract.md`** - источник истины REST контракта
8. **`docs/glossary.md`** - термины проекта
9. **`docs/progress.md`** - актуальный лог последних сессий
   (`docs/archive/progress-sessions-*.md` - архив, читать только при
   поиске исторического контекста)
10. **`docs/doc-hygiene.md`** - правила поддержания документации
    в порядке (когда сжимать roadmap, ротировать SESSION_START_PROMPT,
    архивировать progress, и т.д.)
11. **`docs/superpowers/specs/`** + **`audits/`** - дизайн-спеки и
    аудиты крупных работ

Бэкенд- и фронтенд-специфичные правила - в `backend/CLAUDE.md` и
`frontend/CLAUDE.md`. Эти файлы строже корневого - читать их в первую
очередь при работе в соответствующей части. **Они НЕ дублируют этот
файл** - содержат только бэк- или фронт-специфику.

## Соглашения (краткая выжимка)

### Naming
- Backend DTO: `*Request` / `*Response` (Java); `*Row` для staging-уровня
- Backend методы: `find*` → Optional, `get*` → бросает, `getOne` в REST
  controllers по convention всех 4-х
- Frontend компоненты: `*Page`, `*Panel`, `*Modal`
- Frontend hooks: `use*`, stores: `use*Store`
- UUID-поля: `id` для primary key в собственном DTO,
  `{entityName}Id` для foreign refs и route params

### Commits
- Conventional Commits с scope: `feat(backend)`, `refactor(frontend)`,
  `docs:`, `chore:`, `fix:`
- Атомарные: один commit = одно изменение + его документация
- Никогда `--no-verify` без явной просьбы

### Что НЕ делать

- **Backend:** JPA/Hibernate (только JDBC Template), Lombok (только records),
  H2 в тестах (только Testcontainers), `@Transactional` на @Scheduled
- **Frontend:** `any`, TypeScript `enum` (union literal types вместо),
  отдельные CSS/SCSS файлы (только Tailwind utilities), индекс массива
  как `key` в списках, превентивный `useMemo`/`useCallback`
- **Общее:** не трогать `frontend/design-reference/`, не менять схему БД
  без Liquibase миграции, не менять REST API contracts без обновления
  `docs/api-contract.md` + регенерации `frontend/src/shared/api/types.ts`,
  не запускать массовый shamela-парсинг без UX-валидации

### Workflow

- **Автономный режим:** тактические решения - сам без подтверждения
  (см. `docs/SESSION_START_PROMPT.md` «Режим работы - автономный
  заместитель»)
- **WSL2:** backend, frontend и тесты гонять в WSL2, не Windows-side
- **Backend / frontend rerun:** Claude сам запускает и перезапускает
  оба dev-сервера по необходимости (миграция, regenerate-api,
  smoke-test). Backend ВСЕГДА с JDWP debug args (Абдула подключается
  IntelliJ к `:5005`) - команда в разделе «Команды» выше. Логи в
  `/tmp/backend.log` и `/tmp/frontend.log`. При смене порта 9090
  проверить занят ли - если мой процесс, kill и перезапустить
- **Build cadence:** `./mvnw verify` / `npm run build` - в конце фазы,
  не на каждый чих (см. memory `feedback_no_frequent_builds.md`)
- **Документация по ходу:** ADR / gotcha / api-contract обновляются в
  том же коммите что и код. progress.md - в конце сессии. Правила
  гигиены документации (сжатие закрытых этапов, ротация
  SESSION_START_PROMPT, архивация progress) - в `docs/doc-hygiene.md`
