# CLAUDE.md - argument-map quick start

Быстрый старт для новых сессий Claude Code в этом проекте. Проект
ведётся с плагином **OMC (oh-my-claudecode)** как слоем оркестрации —
см. секции «Оркестрация (OMC)» и «Старт новой сессии» ниже.

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
  specs/                     - дизайн-спеки крупных работ
  plans/                     - implementation-планы крупных работ
  audits/                    - аудиты кодовой базы
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

1. **`docs/progress.md`** - журнал сессий; **последняя запись +
   «Следующий шаг» = текущий приоритет** (точка входа новой сессии)
2. **`docs/roadmap.md`** - активные этапы (закрытые свёрнуты в строку)
3. **`docs/backlog.md`** - идеи без привязки к этапу
4. **`docs/decisions.md`** - ADR'ы (фундаментальные решения, последние
   важные: ADR-018 platform pivot, ADR-022 frontend reorg, ADR-024
   object storage, ADR-028 academic citation)
5. **`docs/gotchas.md`** - известные ловушки и обходы
6. **`docs/architecture.md`** + **`docs/architecture-platform.md`**
7. **`docs/api-contract.md`** - источник истины REST контракта
8. **`docs/glossary.md`** - термины проекта
9. **`docs/archive/progress-sessions-*.md`** - архив журнала, читать
   только при поиске исторического контекста
10. **`docs/doc-hygiene.md`** - правила поддержания документации
    в порядке (когда сжимать roadmap, архивировать progress, и т.д.)
11. **`docs/specs/`** + **`docs/plans/`** + **`docs/audits/`** -
    дизайн-спеки, планы и аудиты крупных работ

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

- **Автономный режим:** тактические решения - сам без подтверждения;
  правила и toggleable MAX-режим - в memory (`feedback_full_autonomy`).
  Эскалация - только принципиальные решения (ADR между альтернативами,
  удаление непонятного файла, схема БД с existing data)
- **WSL2:** backend, frontend и тесты гонять в WSL2, не Windows-side
- **Backend / frontend rerun:** Claude сам запускает и перезапускает
  оба dev-сервера по необходимости (миграция, regenerate-api,
  smoke-test). Backend ВСЕГДА с JDWP debug args (Абдула подключается
  IntelliJ к `:5005`) - команда в разделе «Команды» выше. Логи в
  `/tmp/backend.log` и `/tmp/frontend.log`. При смене порта 9090
  проверить занят ли - если мой процесс, kill и перезапустить
- **Документация по ходу:** ADR / gotcha / api-contract обновляются в
  том же коммите что и код. progress.md - в конце сессии («Следующий
  шаг» переписывается под следующую сессию). Правила гигиены (сжатие
  закрытых этапов, архивация progress) - в `docs/doc-hygiene.md`

## Оркестрация (OMC)

Проект ведётся с плагином **OMC (oh-my-claudecode)**: `/autopilot`,
`/team`, `/ralplan` и пр. (конфиг - `.claude/settings.json`, гайд -
`.claude/CLAUDE.md` + скилл `omc-reference`). superpowers здесь
**отключён** - его скиллы/конвенции не использовать. Процесс крупных
работ прежний: спека (`docs/specs/`) → план (`docs/plans/`) →
исполнение → независимый review; инструменты теперь - OMC.

**Независимый code review после крупных этапов - обязателен** (как и
раньше): через OMC reviewer/verifier-агентов либо скилл `/code-review`.
Триггеры и порядок - в `backend/CLAUDE.md` / `frontend/CLAUDE.md`.

## Старт новой сессии

1. `CLAUDE.md` (этот файл) + memory (`MEMORY.md` подгружается сам)
2. `docs/progress.md` - последние 1-2 записи + «Следующий шаг»
3. `docs/roadmap.md` - активный этап
4. Инфра: `git log --oneline -10`, `docker ps | grep argumentmap`,
   порты 9090/5173
5. Краткое summary («вижу X, продолжаю Y») - и за работу

**Handoff в конце сессии:** запись в progress.md (формат -
doc-hygiene Принцип 5) с подробным «Следующим шагом»; roadmap
обновлён (`[x]`, закрытые этапы сжаты); финальный коммит
`docs: handoff Сессии N`. Лучше 70% задачи с чистым handoff, чем
100% с оборванным контекстом.

## Триаж находок (баги / недочёты / идеи)

- **Тривиальное** (<30 мин) - чинить сразу атомарным коммитом, если
  не в середине этапа; иначе записать и закрыть на границе этапа
- **Среднее** (новый endpoint, переделка экрана, рефактор) - в
  `docs/backlog.md`, осознанно планируется в этап
- **Архитектурное/контрактное** (API, схема БД) - только через ADR/спеку
- Эвристика: если записать дольше, чем починить - чини сразу

## Когда что запускать (cadence)

### Билды и тесты - НЕ на каждый чих

Полные прогоны (`./mvnw verify`, `npm run lint && npm run build &&
npm run test:run`) **тяжёлые**: в WSL2 каждый прогон - десятки секунд
и лог-шум, причём большая часть промежуточных запусков
несодержательна (компилятор / линтер ругнётся на следующем когда
дойдёт)

**Запускай полный прогон только когда:**

- завершилась логическая фаза (новый сервис с тестом, миграция
  применена, подэтап X.a/X.b/X.c закрыт)
- перед коммитом если в фазе были средние или крупные изменения
  (несколько файлов, новый слой, рефакторинг)
- есть конкретный сигнал что что-то могло сломаться:
  - миграция Liquibase / изменение схемы БД
  - изменение DTO / контроллера / API-контракта
  - рефакторинг затрагивающий >1 слой
  - изменение типов которые регенерируются (`npm run generate-api`)

**НЕ запускай**:

- между мелкими правками одного файла
- после косметической корректировки (rename переменной, форматирование)
- после правки одного теста
- «на всякий случай» перед чтением другого файла

Точечная валидация допустима (например проверить что миграция
синтаксически корректна) - это исключение, не правило

При многослойных правках (рефакторинг доменной модели, миграция
API): держать всё в голове, пройтись по всем затронутым файлам,
**один прогон в самом конце** ловит все накопившиеся проблемы за
один проход

История этого правила (incidents и обоснование) - в memory
`feedback_no_frequent_builds.md`

### Playwright для UI верификации (фронт)

Для любого frontend изменения которое влияет на UX (новый
компонент, fix bug'а, redesign элемента) - после коммита **сам**
запусти `/playwright-skill:playwright-skill` в headless режиме, не
жди пока Абдула пройдёт в браузер. Это сокращает feedback loop

**Setup:** первый запуск делает `npm run setup` в
`~/.claude/plugins/cache/playwright-skill/playwright-skill/.../skills/playwright-skill/` -
playwright + chromium ставятся локально в skill dir, не в проект

**Workflow:**

- WSL2: `headless: true` (нет X-сервера). Скриншоты сохранять в
  `/tmp/`, потом читать через Read tool
- параллельно playwright + `curl http://localhost:9090/api/...`
  для верификации backend данных
- если 2+ итерации не сходятся (react-pdf не загружается, selectors
  не находят элементы) - **звать Абдулу** с конкретным screenshot'ом
  и описанием что попробовано
- **всегда** в конце сообщения, даже после успешной playwright
  верификации, напомнить «проверь, пожалуйста, ещё раз вручную в
  браузере, на всякий случай» - playwright headless не на 100%
  повторяет реальный browser environment (нет DevTools, race
  conditions, отдельные браузерные API)

История этого правила - в memory `feedback_playwright_for_ui.md`
