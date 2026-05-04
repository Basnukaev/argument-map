# Журнал работы

Хронологический лог сессий. Новые записи — **сверху**.

Формат записи:
```
## YYYY-MM-DD — Сессия N
### Сделано
### Решения
### Проблемы
### Следующий шаг
```

---

## 2026-05-03 — Сессия 9 (frontend) — API-клиент + список тем + создание темы

### Сделано
- **Юзер для dev-окружения**: пользователь создал запись в `users`
  (UUID `14561248-0bfd-4a62-8395-d40a6972182a`, username Claude),
  записан в `frontend/.env.local` (в gitignore) как `VITE_DEV_USER_ID`
  + `VITE_API_URL=http://localhost:9090`
- **Бэк перезапущен в WSL2** (был в Windows - WSL2 не достукивался по
  localhost:9090, через Windows-host-IP timeout от firewall). В WSL2
  `cd ../backend && ./mvnw spring-boot:run` поднимается за ~7 сек,
  актуальная база рабочая
- **`npm run generate-api`** - сгенерировал `src/api/types.ts`
  (1004 строки) - все эндпоинты v1, схемы Topic/Node/Edge/Source/
  Authority и т.д.
- **`src/api/client.ts`**: типизированный fetch-клиент
  - `apiGet<P extends keyof paths>`, `apiPost`, `apiPatch`, `apiDelete`
  - автоинжекция `X-User-Id` из `import.meta.env.VITE_DEV_USER_ID` в
    мутирующие запросы (POST/PATCH/PUT/DELETE)
  - класс `ApiError extends Error` с распарсенным `ProblemDetails`
    (RFC 7807) + helper `is(suffix)` для match по type-коду
    (`error.is('topic-not-found')`)
  - 204 → undefined, 4xx/5xx с JSON-телом → `ApiError`, 4xx/5xx без
    тела → `ApiError` со статус-текстом
  - helper-типы под springdoc-quirk: контент-тип `*/*` (springdoc) и
    `application/json` оба обрабатываются
- **`src/pages/TopicListPage.tsx`** - список тем
  - 4 ViewState: `loading` / `success-empty` / `success-list` / `error`
  - GET `/api/v1/topics` через `apiGet`, AbortController на cleanup
  - карточки тем (title, description, дата создания) со ссылкой на
    граф `/topics/{id}`
  - filter с type-narrowing для надёжных id (springdoc делает все поля
    optional - см gotchas)
  - визуально: bg-gray-50, white card с hover, blue accent
- **`src/pages/CreateTopicPage.tsx`** - форма создания
  - три поля: `title` (required, max 500), `description` (optional,
    max 2000), `rootQuestion` (required, max 1000) - превратится в
    корневой QUESTION-узел
  - кнопка "Создать" disabled пока обязательные поля пусты
  - submit → POST `/api/v1/topics` → redirect на `/topics/{newId}`
  - field-errors из `errors[]` отображаются под соответствующим полем
  - общая ошибка из `detail` отображается над кнопками
  - кнопка "Отмена" возвращает на `/topics`
- **MSW + RTL setup для тестов**:
  - `src/test/server.ts` - `setupServer()` без дефолтных handlers
  - `src/test-setup.ts` - listen/reset/close через
    `onUnhandledRequest: 'error'` + `vi.stubEnv` для VITE_*
  - 6 тестов на api/client (X-User-Id only-on-mutation, ApiError
    парсинг, type.is(suffix), errors[] валидация, 204 → undefined)
  - 4 теста на TopicListPage (loading, empty, list, 5xx ошибка)
  - 4 теста на CreateTopicPage (disabled-button, success-redirect,
    field-errors, общая ошибка)
- **Прогоны**: lint OK, build OK (239kB / gzip 76kB), тесты 18/18 OK,
  E2E через curl (preflight + GET с Origin) OK - реальный POST в
  бэк создал тему `1d2124ba-...` с auto-generated rootNodeId

### Решения
- **Доменные types создавать пока не буду** (YAGNI). Springdoc делает
  все поля Response optional. Использую `TopicResponse` напрямую +
  `??` для дефолтов + filter с type-narrowing где нужны required поля.
  Когда количество страниц вырастет и появится дублирование - сделаю
  слой мапперов
- **Без middleware для fetch** (axios, ky, react-query) - нативный
  `fetch` + типизированный wrapper. На MVP достаточно. React Query
  заведу когда появится кэширование между страницами или optimistic
  updates
- **`erasableSyntaxOnly: true`** в `tsconfig.app.json` запрещает
  parameter properties в конструкторе. Переписал `ApiError` на явные
  поля. Это TS-флаг для верификации что код полностью erasable
  (валидный JS без TS-only синтаксиса)
- **Springdoc показывает кастомный `@CurrentUser` параметр как
  `query.userId`**, хотя реально читается из заголовка `X-User-Id`.
  Не блокер для фронта - я в `client.ts` вообще не использую
  parameters, только requestBody. Записал в gotchas как backend-task
  для будущего фикса (через `@Parameter(in = HEADER)` или
  `OperationCustomizer`)
- **Тесты - явные handlers per-test** (`server.use(...)`) вместо
  глобального handlers.ts. Тест видит свои моки рядом с assertions,
  любой неожиданный запрос падает (`onUnhandledRequest: 'error'`)

### Проблемы
- **Кросс-сетевая проблема WSL2 ↔ Windows**: бэк запущенный на
  Windows не достукивался из WSL по localhost:9090 (firewall режет
  входящие 9090 от WSL). Решение: перезапустить бэк в WSL2 - там
  Java/Maven уже работают, всё в одной плоскости
- Springdoc + кастомный resolver - см. выше
- `erasableSyntaxOnly` - см. выше

### Следующий шаг
**Страница графа `/topics/{id}` на React Flow.**

Это самый большой кусок MVP - заслуживает отдельной сессии.
Приблизительный план:

1. **Загрузка графа**: `apiGet('/api/v1/topics/{topicId}/graph')`
   возвращает `GraphResponse{topic, nodes, edges}`. Использовать
   useEffect + ViewState (loading/success/error) как в TopicListPage
2. **CSS React Flow**: `import '@xyflow/react/dist/style.css'`
   в `src/index.css` или в самой странице
3. **Кастомный узел** (`src/components/graph/NodeCard.tsx`) - см
   `frontend/docs/ui-guidelines.md` секция "Кастомный узел":
   - цвет фона/border по статусу: STANDING (зелёный), DISPUTED
     (жёлтый), REFUTED (красный), UNVERIFIED (серый)
   - иконка по nodeType (lucide-react): QUESTION → HelpCircle,
     CLAIM → Megaphone, ARGUMENT → MessageSquareQuote, EVIDENCE →
     FileText
   - контент с truncate (3 строки), weight в углу
4. **Кастомное ребро** (`src/components/graph/CustomEdge.tsx`) - см
   `frontend/docs/ui-guidelines.md` секция "Стили рёбер":
   - SUPPORTS / REFUTES - стандартный bezier
   - INVALIDATES - жирный пунктир (kill-семантика, ADR-007)
   - QUALIFIES / RESPONDS_TO - тонкий + полупрозрачный
     (не алгоритмические, ADR-007)
   - подпись с типом
5. **Автолейаут через dagre**: `npm install dagre @types/dagre`,
   горизонтальный layout (rankdir LR), корневой QUESTION слева
6. **Toolbar** в верхнем углу графа:
   - "Добавить узел" → модалка с CreateNodeRequest
   - "Добавить связь" → модалка (выбор from/to из существующих
     узлов + edgeType)
   - "Удалить" - активна когда выделено узел/ребро
7. **Side-панель деталей узла** при выборе:
   - контент, вес, источники, авторитеты (через
     `GET /api/v1/nodes/{id}/sources`, `/authorities`),
     ревизии (`GET /api/v1/nodes/{id}/revisions`)
   - редактирование контента (PATCH `/api/v1/nodes/{id}`)
8. **Hot-update** после мутаций - re-fetch графа после каждого
   POST/PATCH/DELETE (можно потом оптимизировать на local state
   update)

### Важные нюансы
- Бэк должен быть запущен в WSL2 (`./mvnw spring-boot:run`).
  Postgres-контейнер `argumentmap-postgres` healthy
- В `users` есть юзер UUID `14561248-...`, прописан в `.env.local`
- React Flow требует deterministic key/id для узлов и рёбер -
  использовать `id` из бэка
- `nodeTypes` и `edgeTypes` объявлять **вне** компонента (или через
  `useMemo`) - иначе ReactFlow ругается на каждый рендер (см
  `coding-standards.md`)
- Для тестов React Flow требуется `ResizeObserver` mock в jsdom -
  при первом тесте граф-компонента возможно понадобится
  `vi.stubGlobal('ResizeObserver', class { ... })` в test-setup
- Backend-задача (отдельно): починить springdoc + `@CurrentUser` -
  параметр `userId` должен исчезнуть из OpenAPI, вместо него -
  header `X-User-Id`

---

## 2026-05-03 — Сессия 8 (frontend) — Vite-инициализация + CORS на беке

### Сделано
- **Backend (отдельный коммит `ea54350`):** настройка CORS
  - `application.yml`: новое свойство `app.cors.allowed-origins`. В дефолте
    пусто (никакие cross-origin не разрешены), в `local`-профиле -
    `http://localhost:5173,http://localhost:4173` (Vite dev и preview),
    в `test` - `http://localhost:5173`
  - `WebMvcConfig.addCorsMappings(CorsRegistry)` - mapping `/api/**` с
    методами `GET/POST/PATCH/PUT/DELETE/OPTIONS`, заголовками
    `Content-Type, Authorization, Idempotency-Key, X-User-Id`,
    exposed `Location`, `allowCredentials=false`, `maxAge=3600`. Если
    список origin'ов пуст - mapping не регистрируется (безопасный дефолт)
  - `CorsIT.java` - 4 теста (preflight allowed/forbidden, simple GET с
    Origin / без Origin). Всего 140/140 тестов зелёные (`./mvnw verify`)
- **Frontend - инициализирован вручную в существующей папке** (не через
  `npm create vite` чтобы не возиться с overwrite на непустой папке):
  - `package.json`: scripts `dev/build/preview/test/test:run/lint/format/format:check/generate-api`
  - Runtime deps: `react@19.2`, `react-dom@19.2`, `@xyflow/react@12.10`,
    `react-router@7.14`, `zustand@5.0`, `lucide-react@1.14`
  - Dev deps: `vite@6.4`, `@vitejs/plugin-react`, `typescript@5.9`,
    `@types/{react,react-dom,node}`, `tailwindcss@4` + `@tailwindcss/vite`
    + `@tailwindcss/oxide-linux-x64-gnu` (нативный биндинг), `eslint@9`
    + `@eslint/js` + `typescript-eslint` + `eslint-plugin-react-hooks` +
    `eslint-plugin-react-refresh` + `eslint-config-prettier` +
    `prettier`, `globals`, `openapi-typescript@7`, `vitest@3.2` +
    `@testing-library/{react,user-event,jest-dom}` + `jsdom` + `msw`
- TypeScript strict: `tsconfig.json` (project refs), `tsconfig.app.json`
  (`strict`, `noUncheckedIndexedAccess`, paths `@/*`),
  `tsconfig.node.json`
- `vite.config.ts`: alias `@` → `src/`, плагины `react()` + `tailwindcss()`,
  vitest-конфиг (`globals: true`, `environment: 'jsdom'`, setup-файл)
- `eslint.config.js` flat config: typescript-eslint recommended, react-hooks,
  react-refresh, prettier (отключает конфликтующие правила)
- `.prettierrc.json`: 100 char, single quote, trailing comma all
- `.env.example` с `VITE_API_URL` и `VITE_DEV_USER_ID` (UUID для `X-User-Id`
  по ADR-006); `.env.local` в `.gitignore`
- Базовая структура:
  - `index.html`, `src/main.tsx` с `BrowserRouter`, `src/App.tsx` с
    роутами `/topics`, `/topics/new`, `/topics/{id}`, `/` → редирект на `/topics`
  - `src/index.css` с `@import "tailwindcss"` (v4 синтаксис, без
    @tailwind base/components/utilities)
  - `src/components/ui/Button.tsx` - варианты `primary/secondary/danger`,
    проброс `disabled` и нативных props
  - `src/pages/{TopicListPage,CreateTopicPage,TopicGraphPage}.tsx` -
    заглушки с навигацией между страницами
  - `src/test-setup.ts` (jest-dom матчеры),
    `src/components/ui/Button.test.tsx` - 4 теста (рендер, клик,
    вариант, disabled)
- Прогоны: `npm run build` OK (234kB JS / 75kB gzip),
  `npm run lint` OK, `npm run test:run` 4/4 OK, `npm run dev` отвечает
  HTTP 200 на `:5173`

### Решения
- **CORS вместо Vite proxy.** Фронт ходит напрямую на `VITE_API_URL`,
  бэк отвечает `Access-Control-Allow-Origin`. Идентично продакшну, нет
  магии proxy-rewrite. Запись в roadmap об этом обновлена
- **React Router v7 (не `react-router-dom`).** `npm install react-router`
  без явной версии резолверр взял `6.30.3` - принудительно поставил
  `@latest` (`7.14.2`). В v7 `react-router-dom` deprecated, основной
  пакет - `react-router`
- **Lucide-react 1.x** - пакет действительно перешёл с `0.x` на `1.x`,
  не подозрительная версия
- **TypeScript 5.9** (не 6.x). Latest typescript@6 несовместим с
  `openapi-typescript@7.13` (peer `^5.x`). Откат на 5.x не требует
  изменений в коде
- **Tailwind CSS v4 без postcss/autoprefixer.** В v4 не нужны - плагин
  `@tailwindcss/vite` всё делает через Lightning CSS. CSS-импорт - через
  `@import "tailwindcss"`, не `@tailwind`-директивы
- **ESLint 9 flat config** (`eslint.config.js`), не legacy `.eslintrc.json`
- **Не создавать пустые папки `src/api/`, `/stores/`, `/hooks/`,
  `/types/`, `/utils/` заранее** - YAGNI. Появятся вместе с первым
  файлом в них
- **`X-User-Id` через `.env.local`** для dev: `VITE_DEV_USER_ID` будет
  вшиваться в fetch-обёртку. Когда появится Spring Security - заменим
  на токен (ADR-006)

### Проблемы
- **npm 9.2.0 (Debian apt-пакет) криво обрабатывал proxy-auth.** Все
  попытки (`--proxy` флаги, `npm_config_*` env-переменные) возвращали
  `407 Proxy Authentication Required`, при том что `curl` с теми же
  кредами успешно скачивал страницы registry. После обновления npm до
  10.9.3 заработало через стандартные env `HTTPS_PROXY`/`HTTP_PROXY`
  без явной настройки. Записано в gotchas
- **TypeScript 6.x latest несовместим с openapi-typescript@7** -
  откатил на `^5.7`, npm подтянул `5.9.3`. На будущее: при апгрейде
  TS до 6.x ждать поддержки от openapi-typescript
- **Tailwind v4 native binding `@tailwindcss/oxide-linux-x64-gnu`** не
  подтянулся как optionalDependency через прокси (известный bug npm
  с optional deps). Поставил явно как dev-dep. На других платформах
  (Mac, Windows) понадобится свой `@tailwindcss/oxide-*-*-*` -
  записано в gotchas

### Следующий шаг
**Подключение фронта к бэк-API.**

1. **Создать пользователя в БД для dev** (нужен для `X-User-Id`):
   ```sql
   INSERT INTO users (id, username, email, created_at) VALUES
   (gen_random_uuid(), 'abdullah', 'a@example.com', now())
   RETURNING id;
   ```
   Полученный UUID положить в `frontend/.env.local`:
   ```
   VITE_API_URL=http://localhost:9090
   VITE_DEV_USER_ID=<тот самый UUID>
   ```
2. **Сгенерировать TS-типы из OpenAPI** (бэк должен быть запущен):
   ```bash
   npm run generate-api
   ```
   → создаст `src/api/types.ts` с типами всех Request/Response DTO
3. **Создать fetch-обёртку** `src/api/client.ts`:
   - читает `VITE_API_URL` (по умолчанию `http://localhost:9090`)
   - на мутирующих запросах (POST/PATCH/DELETE) добавляет
     `X-User-Id: ${VITE_DEV_USER_ID}` (читает из env, в будущем - из
     стейта/токена)
   - парсит Problem Details (RFC 7807) ответы 4xx/5xx, выбрасывает
     типизированное исключение `ApiError` с полями `type`, `title`,
     `status`, `detail`, опционально `errors[]` для validation
   - возвращает уже типизированные данные через generic `ApiClient`,
     совместимый с типами из `src/api/types.ts`
4. **Реализовать `TopicListPage`:**
   - useEffect → `GET /api/v1/topics`
   - отрисовать карточки тем (id, title, createdAt) + кнопка "создать"
   - обработка loading / error / empty состояний
5. **Реализовать `CreateTopicPage`:**
   - форма (`title`, `initialQuestion` для корневого узла)
   - submit → `POST /api/v1/topics` → редирект на `/topics/{id}`
   - валидация на клиенте + отображение Problem Details ошибок с бэка
6. **Первый Zustand-стор** `src/stores/topicStore.ts` если списочное
   состояние понадобится shared между страницами; на этапе MVP можно и
   через React Query / локальный useState - решить по необходимости

### Важные нюансы для следующей сессии
- Бэк должен быть запущен (`docker compose up -d` для Postgres + бэк на
  :9090). CORS уже настроен в этой сессии - запросы пройдут
- В `users` таблице должен быть пользователь, чьим UUID мы будем
  заполнять `VITE_DEV_USER_ID`. Без него мутации (POST/PATCH/DELETE)
  упадут с 422 на FK-нарушение `created_by`
- При установке новых npm-зависимостей через прокси нужен **npm 10+**
  (на Debian/WSL стандартный 9.2.0 не работает) - выполнить
  `npm install -g npm@latest` если переустановка
- `node_modules/.cache/` если HMR начнёт глючить - удалить и
  перезапустить dev-сервер

---

## 2026-05-03 — Сессия 7 (frontend) — подготовка документации фронта

Это **разовая сессия по подготовке** — кода фронта не пишем.
Создаётся документация и структура `frontend/` для запуска
полноценной разработки в следующей сессии (запускается из
`cd ../frontend && claude`).

### Сделано
- 2 новых ADR в `docs/decisions.md`:
  - **ADR-008** — React 19 + TypeScript + Vite для фронтенда
  - **ADR-009** — React Flow (`@xyflow/react`) для визуализации графа.
    Рассмотрены и отклонены: Cytoscape.js, D3, vis.js
- Создана структура `frontend/`:
  - `frontend/CLAUDE.md` — конфиг для Claude Code, аналог
    `backend/CLAUDE.md`. Стек, документация, соглашения по коду,
    структура папок, тесты, локальная разработка, git-коммиты
  - `frontend/docs/coding-standards.md` — TS/React стандарты:
    SOLID/KISS/DRY/YAGNI в контексте React, TypeScript strict,
    union literal types вместо enum, правила хуков, React Flow
    специфика (`nodeTypes` вне компонента), именование, обработка
    ошибок Problem Details, тесты через Vitest + RTL + MSW
  - `frontend/docs/ui-guidelines.md` — дизайн-система: цвета
    статусов узлов (зелёный/жёлтый/красный/серый), стили рёбер по
    типу (включая пунктирный INVALIDATES), спецификация кастомного
    узла React Flow, layout страниц (`/topics`, `/topics/new`,
    `/topics/{id}`), компоненты, responsive (desktop-first 1024px+),
    a11y
- Обновлён Этап 7 в `docs/roadmap.md`:
  - Подзадача "выбор фреймворка" и "библиотеки графа" закрыты
    (ADR-008, ADR-009)
  - Подзадача "создать CLAUDE.md / coding-standards / ui-guidelines"
    закрыта
  - Добавлены конкретные подзадачи для инициализации проекта,
    генерации API-типов, MVP-страниц, после-MVP функций

### Решения
- **React + React Flow стек.** Главные мотиваторы:
  - React Flow — единственная библиотека, дающая Miro-подобный UX
    drag-and-drop за дни, не месяцы
  - React даёт максимум ресурсов для разработчика без JS-опыта
  - TypeScript обязателен для синхронизации с
    `api-contract.md` через `openapi-typescript`
- **Tailwind CSS** для стилизации. Никаких отдельных CSS-файлов.
  Если набор классов повторяется в 3+ местах — компонент или
  `cva` для вариантов
- **Zustand** для стейт-менеджмента вместо Redux — простота, малый
  объём boilerplate. Для MVP более чем достаточно
- **MSW для моков API в тестах** — перехватывает на уровне fetch,
  максимально близко к реальной работе
- **Без TypeScript `enum`** — union literal types
  (`type NodeStatus = 'STANDING' | ...`). Нет runtime-объекта,
  нативно сериализуется в JSON, лучше tree-shaking
- **Цветовая палитра статусов:** зелёный/жёлтый/красный/серый.
  Это центральная визуальная семантика проекта — пользователь
  видит результат алгоритма пересчёта одним взглядом
- **Стили рёбер:** `INVALIDATES` — жирная пунктирная (визуально
  отделена от обычных REFUTES, отражает kill-семантику ADR-007)
- **Desktop-first.** Граф плохо работает на мобилках; на узких
  экранах — сообщение "откройте на десктопе" с read-only-режимом
- **`generate-api` через `openapi-typescript`** — типы фронта
  всегда в синхроне с бэком. Если расходятся — это бажный
  бэк (см. правило `api-contract.md`)

### Проблемы
- Нет

### Следующий шаг
**Инициализация `frontend/` проекта.** Запускается из новой сессии:
```bash
cd ../frontend && claude
```

Конкретные шаги первой `(frontend)` сессии:
1. `npm create vite@latest .` — выбрать React + TypeScript
2. Установить зависимости:
   - `@xyflow/react` (React Flow)
   - `@tanstack/react-router` или `react-router` (v7)
   - `zustand`
   - `tailwindcss`, `@tailwindcss/vite`, `postcss`, `autoprefixer`
   - `lucide-react` (иконки)
   - dev: `openapi-typescript`, `msw`, `vitest`,
     `@testing-library/react`, `@testing-library/user-event`,
     `@testing-library/jest-dom`, `@types/node`
3. Настройка Tailwind: `tailwind.config.js`, импорт в
   `src/index.css`
4. Настройка `vite.config.ts`: alias `@` = `src/`, proxy `/api/*` →
   `http://localhost:9090`
5. Настройка `tsconfig.json`: `strict: true`,
   `noUncheckedIndexedAccess: true`, paths для `@/*`
6. ESLint + Prettier (через `eslint-config-prettier`)
7. Скрипт `generate-api`:
   `openapi-typescript http://localhost:9090/v3/api-docs -o
   src/api/types.ts`
8. Базовая структура:
   - `src/App.tsx` с роутером (placeholder страницы `/topics`,
     `/topics/new`, `/topics/{id}`)
   - `src/components/ui/Button.tsx` — первый базовый компонент
     для проверки Tailwind
9. Проверить: `npm run dev` поднимает приложение, `npm run build`
   собирает, `npm run test` прогоняет (пока пусто), `npm run
   generate-api` генерит типы (требует поднятого бэка)
10. Commit: `chore(frontend): initial vite + react + ts setup`

После этого — переход к MVP-страницам по чек-листу из roadmap
Этап 7.

### Важные нюансы для следующей сессии
- Бэк должен быть запущен (`docker compose up -d` для Postgres,
  `cd ../backend && ./mvnw spring-boot:run`) для генерации API-типов
- Перед запросами с фронта — проверить что бэк отвечает на
  `localhost:9090/v3/api-docs`
- CORS не настроен на беке — для dev используем Vite proxy.
  Когда понадобится прямой запрос (production) — настроим CORS
  через `WebMvcConfigurer` (см. `api-design.md`)
- `X-User-Id` заголовок — пока временный (ADR-006). Фронт-клиент
  должен прокидывать его на каждый мутирующий запрос. Можно через
  fetch-обёртку, читающую UUID из localStorage / стейта

---

## 2026-05-03 — Сессия 6 (backend) — справочники и поиск (Этап 5)

### Сделано
- 3 новых исключения (`exception/`):
  `SourceNotFoundException`, `AuthorityNotFoundException`,
  `InvalidSourceException`
- 4 сервиса (`service/`):
  - `SourceService` — CRUD + searchByTitle. Бизнес-правило:
    `reliability != null` запрещён для `SourceType != HADITH`
    (бросает `InvalidSourceException`)
  - `AuthorityService` — CRUD + searchByName
  - `NodeSourceService` — `attachSource` / `getNodeSources` /
    `detachSource`. Валидирует существование узла и источника
  - `NodeAuthorityService` — то же со `stance`
- 8 DTO (`web/dto/`):
  - `CreateSourceRequest`, `SourceResponse` (metadata как `JsonNode`)
  - `CreateAuthorityRequest`, `AuthorityResponse`
  - `AttachSourceRequest`, `NodeSourceResponse`
  - `AttachAuthorityRequest`, `NodeAuthorityResponse`
- `DtoMappers` дополнен:
  - Методы `toResponse(Source/Authority/NodeSource/NodeAuthority)`
  - Утилиты `jsonToString(JsonNode)` / `jsonFromString(String)` через
    статический `ObjectMapper` для конверсии jsonb-колонок
- 4 контроллера (`web/controller/`):
  - `SourceController` — POST/GET-list (с `?q`)/GET-one/DELETE
  - `AuthorityController` — то же
  - `NodeSourceController` — POST/GET/DELETE на
    `/api/v1/nodes/{nodeId}/sources`
  - `NodeAuthorityController` — то же на `/authorities`
- `GlobalExceptionHandler` дополнен — 3 новых обработчика
  (`source-not-found`, `authority-not-found`, `invalid-source`)
- 32 новых интеграционных теста (всего 136):
  - `SourceControllerIT` — 10 тестов (создание HADITH/BOOK,
    бизнес-валидация reliability, поиск, удаление)
  - `AuthorityControllerIT` — 8 тестов
  - `NodeSourceControllerIT` — 7 тестов
  - `NodeAuthorityControllerIT` — 7 тестов
- `api-contract.md` дополнен v1: секции Sources/Authorities/привязок
  + 4 новых типа Response + новые `type`-коды

### Решения
- **`metadata` (jsonb) как `JsonNode` в DTO:** Jackson обрабатывает
  туда-обратно прозрачно. В domain — `String` (raw JSON), маппер
  делает `JSON.readTree(string)` на чтение и `node.toString()` на
  запись. Спрятано в `DtoMappers.jsonFromString` /
  `DtoMappers.jsonToString`. Альтернатива (`Map<String,Object>`)
  потребовала бы `@Component`-маппер с инжектом Spring `ObjectMapper` -
  не оправдано
- **`NodeSourceResponse`/`NodeAuthorityResponse` без вложенного
  `Source`/`Authority`:** возвращаются только метаданные привязки
  (`{nodeId, sourceId, quote, context, createdAt}`). Если фронту
  нужны полные данные источника - отдельный запрос на
  `/sources/{id}`. Минимальный payload, нет N+1 на бэке. При
  необходимости встроим nested позже без breaking change (новое поле
  не ломает клиентов)
- **`reliability` валидируется в сервисе, не на БД-CHECK:** в БД
  `reliability` принимает любое из `SAHIH/HASAN/DAIF` (или null) для
  любого `source_type`. Семантическое правило "только для HADITH" —
  на сервисном слое. Гибче добавлять новые типы источников, у которых
  тоже может быть reliability
- **Поиск через `?q=...`:** соответствует резервации в `api-design.md`
  ("`q` - зарезервированный параметр для текстового поиска").
  Реализация: `ILIKE '%query%'` на `title` / `name` через
  `searchByTitle` / `searchByName` репозиториев Этапа 2
- **Пагинация откладывается:** справочники маленькие, KISS. TODO
  отмечен в roadmap. Для полноценной пагинации потребуется
  `PageResponse<T>`/`PageInfo` records, `findAllPaged(offset, limit)`
  в репо, валидация `?page`/`?size`. Не блокирует MVP
- **DELETE на `/nodes/{nodeId}/sources/{sourceId}` возвращает 404
  если привязка не существует (`source-not-found`):** строго говоря,
  привязки не было; но различать "источника нет в справочнике" vs
  "привязки нет" не требуется для UX. Достаточно одного 404
- **Метаданные в JSON request — нативный объект, не строка:**
  фронт передаёт `{"metadata": {"book": 1}}`, а не
  `{"metadata": "{\"book\":1}"}`. Jackson десериализует в `JsonNode`,
  валидируется как обычный JSON. Это корректнее по api-design.md
  ("JSON в запросах")

### Проблемы
- Нет

### Следующий шаг
**Этап 6: улучшения после MVP.**

По roadmap:
- Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`)
- Реализация Dung's argumentation framework (продвинутый алгоритм
  пересчёта статусов)
- Импорт/экспорт темы в JSON
- Аутентификация и авторизация (Spring Security, JWT) - в т.ч.
  миграция с `X-User-Id` (ADR-006) на `Authentication`
- Голосование за вес аргументов

Каждая задача — отдельный мини-проект, можно делать независимо.

Альтернативно: **Этап 7 — фронтенд.** Бэкенд API стабилен и
задокументирован (`api-contract.md` + Swagger UI). Можно начинать
фронт. Подготовительные шаги:
1. Выбрать фреймворк (React / Vue / Svelte) → ADR
2. Выбрать библиотеку графов (React Flow / Cytoscape / D3) → ADR
3. Создать `frontend/` папку, `frontend/CLAUDE.md`,
   `frontend/docs/coding-standards.md`,
   `frontend/docs/ui-guidelines.md`
4. Сборка (Vite/Next), TypeScript, линтер
5. Сгенерировать TS-клиент из `/v3/api-docs` через
   `openapi-typescript`

### Важные нюансы
- Бэкенд готов к новым клиентам: API стабилен, OpenAPI генерится,
  `api-contract.md` синхронизирован
- Перед запуском фронта - убедиться что CORS настроен (см.
  `api-design.md`); сейчас не настроен, потребуется
  `WebMvcConfigurer` или Spring Security
- Когда появится Spring Security — заменить `CurrentUserArgumentResolver`
  на стандартный `@AuthenticationPrincipal`. Контракты сервисов не
  меняются (ADR-006)

---

## 2026-05-03 — Сессия 5 (backend) — REST API (Этап 4)

### Сделано
- Добавлена зависимость `springdoc-openapi-starter-webmvc-ui:2.8.0`
  в `pom.xml`. Spring Boot 3.5 совместим
- `GlobalExceptionHandler` (`@RestControllerAdvice`) с Problem Details
  (RFC 7807) для всех доменных исключений + Bean Validation +
  `DataIntegrityViolation`. Spring сам выставляет
  `Content-Type: application/problem+json`
- Новое исключение `MissingUserHeaderException` для невалидного
  / отсутствующего `X-User-Id`
- 9 DTO в `web/dto/`:
  - `CreateTopicRequest`, `TopicResponse`
  - `CreateNodeRequest`, `UpdateNodeRequest`, `NodeResponse`
  - `CreateEdgeRequest`, `EdgeResponse`
  - `RevisionResponse`, `GraphResponse`
- `DtoMappers` (`web/mapper/`) — статические методы маппинга
  `domain → DTO`, без MapStruct (объёма мало)
- `@CurrentUser` аннотация + `CurrentUserArgumentResolver` —
  читает `X-User-Id`, парсит UUID, инжектит в контроллерные методы.
  Существование пользователя не валидирует здесь — пускаем БД-FK
  поймать на write (→ 422)
- `WebMvcConfig` — регистрация резолвера в Spring MVC
- 3 контроллера в `web/controller/`:
  - `TopicController` — POST/GET/GET-one/DELETE/GET-graph
  - `NodeController` — POST/PATCH/DELETE/GET-revisions
  - `EdgeController` — POST/DELETE
- 4 интеграционных теста (29 тестов всего):
  - `TopicControllerIT` — 10 тестов
  - `NodeControllerIT` — 9 тестов
  - `EdgeControllerIT` — 7 тестов
  - `OpenApiIT` — 3 теста (доступность `/v3/api-docs` со всеми
    эндпоинтами; редирект `/swagger-ui.html`; загрузка
    `/swagger-ui/index.html`)
- `api-contract.md` обновлён v1 — описаны все эндпоинты + примеры
  запросов/ответов + список Problem Details type-кодов
- Все 104 теста проходят (`./mvnw verify`)

### Решения
- Маппинг через статический utility-класс `DtoMappers` — KISS, нет
  MapStruct (соглашение из roadmap "ручные, без MapStruct — слишком
  мало маппинга")
- `createdBy` в Response DTO — UUID, не вложенный объект `UserSummary`.
  Если фронту понадобится `username` — добавим `UserSummary` позже.
  KISS до явного use-case
- `@CurrentUser` + argument resolver вместо `@RequestHeader` на каждом
  методе — DRY. Параметр контроллера выглядит как `UUID userId`,
  без шумной аннотации заголовка
- В резолвере UUID не валидируется на существование в БД —
  FK-нарушение поймёт `INSERT` в репозитории, переведётся в 422 через
  `GlobalExceptionHandler`. Меньше круглых походов к БД, документировано
  в `api-contract.md`
- `ProblemDetail` из Spring Framework 6 — нативно поддержан Spring
  Boot 3, не нужны сторонние библиотеки. `setProperty("errors", ...)`
  для расширения тела `validation` ошибки
- Для `MethodArgumentNotValidException` написан кастомный обработчик
  — Spring Boot по умолчанию сам отвечает Problem Details, но без поля
  `errors[]`, которое требует api-design.md
- `DELETE` эндпоинты не требуют `X-User-Id` — не нужно знать "кто",
  достаточно "что". Авторизация (Этап 6) добавит контроль "может ли
  этот юзер удалять"

### Проблемы
- Нет

### Следующий шаг
**Этап 5 из roadmap: справочники и поиск.**

Задачи по roadmap:
- `SourceService` + REST: CRUD, поиск по названию/типу
- `AuthorityService` + REST: CRUD, поиск по имени/эпохе/мазхабу
- Привязка источников и авторитетов к узлам через
  `NodeSourceService` / `NodeAuthorityService`

Эндпоинты по `architecture.md`:
- `POST /api/v1/sources` — добавить источник
- `GET /api/v1/sources?q=...` — поиск
- `POST /api/v1/nodes/{id}/sources` — привязать
- `POST /api/v1/authorities` / `GET /api/v1/authorities?q=...`
- `POST /api/v1/nodes/{id}/authorities` — привязать со `stance`

Уже готово на Этапе 2: `SourceRepository`, `AuthorityRepository`,
`NodeSourceRepository`, `NodeAuthorityRepository` — с `searchByTitle` /
`searchByName`. Нужны сервисы (тонкие, без сложной логики), DTO,
контроллеры.

### Важные нюансы для Этапа 5
- Поиск через `?q=...` (как зарезервировано в `api-design.md`)
- Пагинация по правилам `api-design.md` — offset-based с `page`/`size`
  / `sort`. Для MVP списки могут быть без пагинации (KISS), но
  посмотреть на объём — если очерёдно 1000+ источников, добавить
- `NodeSource`/`NodeAuthority` — composite-key, отдельные эндпоинты
  с двумя параметрами в URL (`/nodes/{nodeId}/sources/{sourceId}`)
- Метаданные `sources.metadata` (jsonb) — в DTO как `Map<String,
  Object>` или сырая строка JSON. Решить: `Object` (Jackson сам
  парсит/сериализует) проще для фронта
- Привязка источника к узлу — POST с телом `{quote, context}`
- `Reliability` enum (`SAHIH`/`HASAN`/`DAIF`) только для
  `SourceType.HADITH`. Валидация бизнес-правила в сервисе:
  `reliability != null` запрещён для не-`HADITH`

---

## 2026-05-03 — Сессия 4 (backend) — сервисный слой (Этап 3)

### Сделано
- Брейнсторм Этапа 3 → дизайн в
  `docs/superpowers/specs/2026-05-03-stage-3-services-design.md`
- 2 новых ADR:
  - **ADR-006** — `createdBy` через HTTP-заголовок `X-User-Id` до
    появления Spring Security (Этап 6)
  - **ADR-007** — вклад типов рёбер в алгоритм пересчёта статусов:
    `SUPPORTS`/`REFUTES` — обычные, `INVALIDATES` — kill-switch,
    `QUALIFIES`/`RESPONDS_TO` — не входят
- Уточнение правила 1 в `architecture.md`: узел без влияющих входящих
  рёбер сохраняет текущий статус (вместо принудительного `UNVERIFIED`).
  Это поддерживает будущую ручную пометку статуса и делает алгоритм
  устойчивым к сценарию "удалили последнее ребро" — статус не
  обнуляется, а отражает фактическое состояние графа
- 4 доменных исключения в `exception/`:
  `TopicNotFoundException`, `NodeNotFoundException`,
  `EdgeNotFoundException`, `InvalidEdgeException`
- 5 сервисов в `service/`:
  - `TopicService` — `createTopic` (создаёт root QUESTION
    транзакционно, обходя циркулярный FK), `getTopic`, `listTopics`,
    `deleteTopic`
  - `NodeService` — `createNode`, `updateContent` (пишет revision),
    `deleteNode` (триггерит recalc), `getRevisions`
  - `EdgeService` — `createEdge` (валидация self-loop / cross-topic,
    триггерит recalc), `deleteEdge` (триггерит recalc)
  - `GraphService` — `getGraph(topicId) → GraphView{topic, nodes,
    edges}` (плоская форма, как у graph-библиотек React Flow / Cytoscape)
  - `StatusCalculationService` — фикспоинт-итерация в памяти, в БД
    пишутся только дельты, `MAX_ITERATIONS = max(20, nodes*2)`
- `GraphView` record (`service/GraphView.java`)
- 75 тестов всего (было 46 после Этапа 2):
  - `StatusCalculationServiceTest` — 14 unit-тестов (моки), все
    сценарии из testing-strategy.md
  - `StatusCalculationServiceIT` — 3 интеграционных
  - `TopicServiceIT` — 6
  - `NodeServiceIT` — 9 (включая recalc через `deleteNode`)
  - `EdgeServiceIT` — 9 (включая recalc через `createEdge`/`deleteEdge`,
    cross-topic, self-loop)
  - `GraphServiceIT` — 3
- Все коммиты по смыслу (5 коммитов на этап)
- Сохранена feedback-память в
  `~/.claude/projects/.../memory/feedback_decision_authority.md` —
  правило "решаю сам, спрашиваю только при дилеммах; ADR только когда
  через месяц возникнет вопрос почему"

### Решения
- Дизайн зафиксирован в spec-документе со ссылками на ADR-006/007
- Транзакционность: `@Transactional` строго на сервисах, не на
  репозиториях/контроллерах. `StatusCalculationService` без аннотации
  (присоединяется к транзакции вызывающего)
- `TopicService.createTopic` пишет root-узел через `NodeRepository`
  напрямую (а не через `NodeService.createNode`), потому что
  `NodeService` валидирует "тема существует", а тема ещё в незакоммиченной
  транзакции
- `EdgeService.deleteEdge` извлекает `topicId` через
  `nodeRepository.findById(existing.fromNodeId())` до удаления —
  иначе после удаления неоткуда взять topicId для пересчёта
- `NodeService.deleteNode` аналогично — `findById` до `deleteById`
- Алгоритм статусов: фикспоинт по графу в памяти, batch-update в БД
  только дельт. `INVALIDATES` от STANDING-источника = kill (REFUTED
  безусловно, бьёт STANDING supports). `QUALIFIES`/`RESPONDS_TO` —
  не влияют

### Проблемы
- В первой версии алгоритма "узел без влияющих рёбер → UNVERIFIED"
  ломал тесты с STANDING-источниками: алгоритм сбрасывал источник в
  UNVERIFIED, и цепочка не работала. Решено: уточнено правило 1 в
  `architecture.md` — узел без влияющих рёбер сохраняет статус. Это
  совместимо с буквой "пока не оценён" из оригинального правила и
  открывает дорогу к будущей ручной пометке (Этап 6+)
- Spring DI требовал явного добавления `StatusCalculationService` в
  конструкторы `EdgeService`/`NodeService` на шаге 6 — не упало, но
  потребовало внимательности с порядком реализации (сначала SCS,
  потом подключение)

### Следующий шаг
**Этап 4 из roadmap: REST API.**

Задачи по roadmap:
- DTO + ручные мапперы (без MapStruct — слишком мало маппинга по
  ADR-неявному соглашению Этапа 4)
- Контроллеры по эскизу из `architecture.md`:
  - `POST /api/v1/topics` → `TopicService.createTopic`
  - `GET /api/v1/topics`, `GET /api/v1/topics/{id}`,
    `DELETE /api/v1/topics/{id}`
  - `GET /api/v1/topics/{id}/graph` → `GraphService.getGraph`
  - `POST /api/v1/nodes`, `PATCH /api/v1/nodes/{id}`,
    `DELETE /api/v1/nodes/{id}`, `GET /api/v1/nodes/{id}/revisions`
  - `POST /api/v1/edges`, `DELETE /api/v1/edges/{id}`
- Глобальный `@ControllerAdvice` с маппингом доменных исключений на
  HTTP-коды:
  - `*NotFoundException` → 404
  - `InvalidEdgeException` → 422
  - `DataIntegrityViolationException` → 422 (FK нарушения)
  - `MethodArgumentNotValidException` → 400 (Bean Validation)
- Bean Validation на DTO через `@Valid`/`@NotNull`/`@NotBlank`/`@Size`
- OpenAPI-спецификация через `springdoc-openapi` (надо добавить
  зависимость в pom.xml — обсудить перед добавлением, см. CLAUDE.md
  "Не добавлять зависимости без обсуждения")
- `X-User-Id` заголовок (ADR-006): извлечение в контроллере, валидация
  существования юзера в `users`, проброс UUID в сервис. Возможно через
  `HandlerMethodArgumentResolver` или `@RequestHeader` на каждом методе
  (обсудить)
- Интеграционные тесты контроллеров через `MockMvc` + Testcontainers

### Важные нюансы для Этапа 4
- `api-contract.md` обновлять синхронно с каждым новым эндпоинтом
- Имена JSON-полей — `camelCase` (Jackson default OK, но проверить)
- Не возвращать доменные `Node`/`Edge`/`Topic` напрямую — DTO
  (`NodeResponse`, `TopicResponse`, и т.д.)
- DTO-структура для `GraphView` — `GraphResponse{topic, nodes[], edges[]}`,
  плоская
- `Idempotency-Key` для POST не делаем (запланировано на потом)
- Пагинация для `GET /api/v1/topics` пока не нужна, но при появлении
  скриниться по правилам `api-design.md`

---

## 2026-04-20 — Сессия 3 (backend) — доменная модель и репозитории

### Сделано
- Enum'ы в `backend/src/main/java/ru/basnukaev/argumentmap/domain/`:
  `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`, `Reliability`
- Java records (все иммутабельные, без Lombok):
  `Topic`, `Node`, `Edge`, `Source`, `Authority`, `NodeSource`, `NodeAuthority`,
  `Revision`. Timestamps — `Instant`, id — `UUID`
- JDBC-репозитории в `repository/`:
  - `TopicRepository` — save/findById/findAll/updateRootNodeId/deleteById
  - `NodeRepository` — save/findById/findByTopicId/update/updateStatus/deleteById
  - `EdgeRepository` — save/findById/findBy{From,To}NodeId/findByTopicId(JOIN)/deleteById
  - `SourceRepository` — CRUD + searchByTitle (ILIKE), metadata через `?::jsonb`
  - `AuthorityRepository` — CRUD + searchByName
  - `NodeSourceRepository` — save/findByIds/findByNodeId/findBySourceId/delete
  - `NodeAuthorityRepository` — аналогично со `stance`
  - `RevisionRepository` — save/findById/findByNodeId (без delete — журнал)
- Утилита `repository.JdbcTimes` — конвертация `Instant ↔ OffsetDateTime`
  для колонок `TIMESTAMPTZ` (см. gotcha)
- Интеграционные тесты на каждый репозиторий (`*IT.java`), Testcontainers
  Postgres 16, `@Transactional` + rollback. Фикстуры через
  `jdbcTemplate.update(...)`, не через тестируемый репозиторий
  (testing-strategy.md). Всего 45 тестов, `./mvnw verify` — зелёные
- Привязка `maven-failsafe-plugin` в `pom.xml` — без неё `verify` не
  запускал `*IT`-тесты (объявление есть в Spring Boot parent, но только
  в `pluginManagement`)
- `TestcontainersConfiguration` сделан `public`, чтобы импортировать
  из под-пакета `repository`
- Добавлены 2 gotcha в `docs/gotchas.md`:
  1. PG JDBC не выводит SQL-тип для `Instant` (нужен `OffsetDateTime`)
  2. Failsafe plugin в Spring Boot parent требует явного `<execution>`

### Решения
- **Контракт `save(T)`:** репозиторий принимает полностью заполненный
  record (id + timestamps). Генерация id и вычисление `Instant.now()` —
  ответственность сервисного слоя. Репозиторий остаётся тупым CRUD,
  тесты детерминированы (точные assertions по timestamp), политика
  генерации изолирована
- **Instant в доменных моделях, OffsetDateTime на границе с JDBC:**
  доменная модель не знает о JDBC-ограничениях. Конвертация вынесена
  в утилиту `JdbcTimes` рядом с репозиториями
- **jsonb через `?::jsonb` cast в SQL:** проще `PGobject`, работает
  для nullable значений, читабельно. Проверено тестом
  `metadataJsonb_isQueryableWithJsonbOperators` с оператором `@>`
- **Композитный PK у M:N таблиц:** `NodeSource` и `NodeAuthority` не
  имеют surrogate id. Методы `findByIds(a, b)` и `delete(a, b)` работают
  по паре ключей напрямую
- **`findByTopicId` у `EdgeRepository` — через JOIN `nodes`:** рёбра
  не содержат прямого `topic_id`, выбираются через `e.from_node_id =
  n.id`. Инвариант "ребро не пересекает границу темы" будет проверяться
  в `EdgeService` при создании (Этап 3)
- **`RevisionRepository` без `deleteById`:** revisions — исторический
  журнал, удалять только каскадно через удаление узла (что уже настроено
  в миграции 11). Принцип YAGNI
- **Reliability как enum (новый):** в roadmap не был в списке — добавил
  в том же духе, что остальные enum'ы, чтобы покрыть CHECK-ограничение
  `reliability IN ('SAHIH','HASAN','DAIF')`. Уже упоминался в прошлом
  progress (сессия 2)

### Проблемы
- `PSQLException: Can't infer the SQL type to use for an instance of
  java.time.Instant` — pgjdbc не маппит `Instant` через `setObject`
  без явного Types. Решено утилитой `JdbcTimes.odt(Instant)`
  (`OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`). Записано в
  `gotchas.md`
- `./mvnw verify` не запускал `*IT`-тесты — Spring Boot parent объявляет
  Failsafe в `pluginManagement`, но не привязывает goal'ы. Решено
  явным `<execution>` в `pom.xml`. Записано в `gotchas.md`

### Следующий шаг
**Этап 3 из roadmap: бизнес-логика (сервисный слой).**

Задачи по roadmap:
- `TopicService` — создание темы с корневым вопросом транзакционно.
  Паттерн: создать `Topic` без `root_node_id`, создать `Node`
  (QUESTION), `topicRepository.updateRootNodeId(...)` — всё в одной
  транзакции (`@Transactional` на методе)
- `NodeService` — создание/редактирование/удаление, запись в `revisions`
  при каждом редактировании (`content_before` = старое, `content_after`
  = новое). Использовать `Instant.now()` для timestamps здесь
- `EdgeService` — создание/удаление рёбер. Валидация: оба узла в одной
  теме (инвариант, используемый в `EdgeRepository.findByTopicId`)
- `GraphService` — загрузка всего графа темы одним-двумя запросами
  (узлы темы + рёбра темы). Возвращает агрегат `{nodes, edges}`
- `StatusCalculationService` — MVP-алгоритм пересчёта из `architecture.md`:
  1. Без входящих рёбер → `UNVERIFIED`
  2. Supports все от `REFUTED` + есть `STANDING` refute → `REFUTED`
  3. Есть `STANDING` supports И `STANDING` refutes → `DISPUTED`
  4. Есть `STANDING` supports, нет `STANDING` refutes → `STANDING`
  5. `INVALIDATES` — жёстче `REFUTES`
- Тесты сервисов: unit с Mockito для мапперов/логики, integration через
  Testcontainers для транзакционности
- Особое внимание — fixture-графам для `StatusCalculationService` (см.
  testing-strategy.md): минимум 4 сценария + 4 граничных

### Важные нюансы для Этапа 3
- На сервисах — `@Transactional`, не на репозиториях и не на контроллерах
  (см. coding-standards.md)
- Не использовать `@Transactional(readOnly = true)` вперемешку с `true` —
  разделять явно
- Доменные исключения (`TopicNotFoundException`, `NodeNotFoundException`,
  `InvalidEdgeException`) — в пакете `ru.basnukaev.argumentmap.exception`
- Начать рекомендую с `TopicService` — самая простая операция-с-транзакцией,
  задаёт шаблон. Потом `NodeService`, потом `EdgeService`, потом
  `GraphService`, потом `StatusCalculationService` (самый сложный)

---

## 2026-04-20 — Сессия 2 (backend) — Liquibase-миграции схемы БД

### Сделано
- Создано 11 changeset-файлов в `backend/src/main/resources/db/changelog/changes/`:
  - `20260413-01-create-extensions.xml` — `uuid-ossp`
  - `20260413-02-create-users-table.xml` — минимальные `users` (id, username,
    email, created_at)
  - `20260413-03-create-topics-table.xml` — `topics` с `root_node_id` без FK
    (циркулярная зависимость topics↔nodes)
  - `20260413-04-create-nodes-table.xml` — `nodes` + CHECK на
    `node_type`/`status`/`weight`, индексы на `topic_id`, `status`, `created_by`
  - `20260413-05-add-root-node-fk-to-topics.xml` — замыкающий FK
    `topics.root_node_id → nodes.id ON DELETE SET NULL` + индекс
  - `20260413-06-create-edges-table.xml` — `edges` + CHECK на `edge_type`,
    индексы на `from_node_id`, `to_node_id`, `edge_type`, `created_by`
  - `20260413-07-create-sources-table.xml` — `sources` + `reliability` CHECK,
    GIN-индекс на `metadata`
  - `20260413-08-create-authorities-table.xml` — `authorities` + GIN на `metadata`,
    индексы на `name`, `era`, `madhab`
  - `20260413-09-create-node-sources-table.xml` — M:N с композитным PK + индекс
    на `source_id`
  - `20260413-10-create-node-authorities-table.xml` — M:N со `stance`
    CHECK + индекс на `authority_id`
  - `20260413-11-create-revisions-table.xml` — история изменений узлов
- Обновлён `db.changelog-master.xml` — `<include>` всех 11 файлов в порядке
  применения
- Smoke-тест `ArgumentMapApplicationTests.contextLoads()` проходит:
  Testcontainers поднимает Postgres 16-alpine, Liquibase прогоняет 11 changeset'ов
  (`Run: 11, Previously run: 0`), BUILD SUCCESS
- У каждого changeset'а прописан `<rollback>` (обратимость миграции)

### Решения
- Формат миграций: XML с raw `<sql>` внутри `<changeSet>`. Нативные теги
  Liquibase (`<createTable>` и т.п.) не используем — `<sql>` проще и лучше
  переносит CHECK constraints, GIN-индексы и композитные PK
- Циркулярный FK `topics.root_node_id → nodes.id` вынесен в отдельную
  миграцию 05 (см. gotchas.md)
- Enum'ы хранятся как `TEXT + CHECK` (см. antipatterns.md), значения uppercase
  для консистенции с Java enum (`.name()`)
- `reliability` в `sources` — uppercase `SAHIH/HASAN/DAIF` (в `er-diagram.md`
  было lowercase, но uppercase лучше ложится на Java-enum — уточнение
  документации будет в отдельном коммите при необходимости)
- Индексы на FK создаются в той же миграции, что и таблица (antipatterns.md)
- `ON DELETE CASCADE` — для дочерних сущностей (`nodes.topic_id`, `edges.*`,
  `node_sources.*`, `node_authorities.*`, `revisions.node_id`)
- `ON DELETE SET NULL` — для `topics.root_node_id` (удаление корневого узла
  не должно удалять тему)
- Все `timestamp` поля — `TIMESTAMPTZ` с `DEFAULT now()`

### Проблемы
- XML parse error в миграции 07: символ `&` в комментарии должен
  экранироваться (`&amp;`). Решено: переформулировал комментарий без
  спецсимволов. На будущее — или CDATA, или `&amp;` в XML-комментариях

### Следующий шаг
**Этап 2 из roadmap: доменная модель и репозитории.**

Ждём подтверждения пользователя перед стартом Этапа 2. Задачи этапа:
- Java records для всех сущностей (`Topic`, `Node`, `Edge`, `Source`,
  `Authority`, `NodeSource`, `NodeAuthority`, `Revision`)
- Enum'ы: `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`,
  `Reliability` (SAHIH/HASAN/DAIF)
- JDBC Template репозитории с RowMapper'ами
- Интеграционные тесты на каждый репозиторий (CRUD), фикстуры через
  `jdbcTemplate.update(...)` (см. testing-strategy.md)

---

## 2026-04-20 — Сессия 1.5 (backend) — укрепление фундамента

### Сделано
- Создан `.editorconfig` в корне репы (единообразие отступов,
  окончания строк)
- Создан `.gitattributes` в корне репы + нормализация line endings
  (защита от CRLF/LF проблем на Windows+WSL)
- Установлен `spring.profiles.default: local` в application.yml
  (приложение стартует корректно из IDE и jar, не только из Maven)
- Добавлен `spring-boot-starter-actuator` в pom.xml
  (для /actuator/health и будущих метрик)
- Синхронизирован API-префикс `/api/v1/` в architecture.md
  (был `/api/`, расходился с api-design.md и api-contract.md)
- Добавлено примечание о порядке ADR в decisions.md
- Создан `docs/session-workflow.md` — компактный чек-лист сессии
- Создан `backend/docs/testing-strategy.md` — стратегия тестирования,
  включая подход к тестированию графовых обходов
- Создан `docs/git-workflow.md` — Conventional Commits, scope
  для монорепы, правила ветвления
- Создан `.github/workflows/README.md` — заготовка для будущего CI

### Решения
- Дефолтный профиль = local (чтобы не ломалось при запуске из IDE)
- Actuator добавлен сейчас, а не позже — документация уже ссылается на него
- Testing strategy зафиксирована до начала написания тестов

### Проблемы
- Нет

### Следующий шаг
**Этап 1 из roadmap: Liquibase-миграции схемы БД.**

Создать миграции по списку из roadmap:
1. `20260413-01-create-extensions` (uuid-ossp)
2. `20260413-02-create-users-table`
3. `20260413-03-create-topics-table`
4. `20260413-04-create-nodes-table` + индексы
5. `20260413-05-add-root-node-fk-to-topics` (циркулярный FK, см. gotchas.md)
6. `20260413-06-create-edges-table` + индексы
7. `20260413-07-create-sources-table` + GIN-индекс на metadata
8. `20260413-08-create-authorities-table`
9. `20260413-09-create-node-sources-table`
10. `20260413-10-create-node-authorities-table`
11. `20260413-11-create-revisions-table`
12. Smoke-тест: Testcontainers + Liquibase прогоняет все миграции

Автор всех changeset'ов: `Abdula Basnukaev`.
Формат: TEXT + CHECK constraints для enum'ов (см. antipatterns.md).
Индексы на FK — в той же миграции (см. antipatterns.md).
TIMESTAMPTZ, не TIMESTAMP (см. antipatterns.md).

---

## 2026-04-13 — Сессия 1 (backend)

### Сделано
- Установлены инструменты в WSL: OpenJDK 21.0.10, Maven 3.8.7
- Сгенерирован Spring Boot проект через Spring Initializr (версия 3.5.0):
  - `pom.xml` с зависимостями: web, jdbc, validation, liquibase, postgresql,
    testcontainers (включая `spring-boot-testcontainers` для `@ServiceConnection`)
  - Maven Wrapper (`mvnw`)
  - Главный класс `ArgumentMapApplication`
  - Тестовая конфигурация Testcontainers (`TestcontainersConfiguration`,
    `TestArgumentMapApplication`, `ArgumentMapApplicationTests`)
- Настроен `application.yml`:
  - Профиль `local` — подключение к Postgres из `docker-compose.yml`
  - Профиль `test` — заглушка, datasource через Testcontainers `@ServiceConnection`
  - Сервер на порту 9090 (8080 занят)
- Создан пустой `db.changelog-master.xml` с валидной структурой
- Создана папка `db/changelog/changes/` для будущих миграций
- Проверен успешный запуск: Tomcat на :9090, HikariPool подключился
  к Postgres, Liquibase прочитал changelog — "Database is up to date"
- Добавлен ADR-004 (Maven vs Gradle) в `decisions.md`
- Проставлены `[x]` на пунктах Этапа 0 в `roadmap.md`
- В `CLAUDE.md` добавлен раздел "Git-коммиты" с Conventional Commits

### Решения
- ADR-004: Maven вместо Gradle — привычный стек, совместимость с экосистемой
- Spring Boot 3.5.0 вместо 3.3.x — Initializr требует >=3.5.0 (3.3/3.4
  больше не поддерживаются на start.spring.io)
- Порт 9090 вместо дефолтного 8080 — порт 8080 занят на машине разработчика
- Добавлена зависимость `spring-boot-testcontainers` — идёт автоматически
  из Initializr при выборе Testcontainers, предоставляет `@ServiceConnection`

### Проблемы
- Java и Maven не были установлены в WSL — установлены через apt
- Spring Initializr больше не поддерживает Spring Boot 3.3/3.4 — использовали 3.5.0

### Следующий шаг
**Этап 1 из `roadmap.md`: Liquibase-миграции схемы БД.**

Создать миграции по списку из roadmap (extensions, users, topics, nodes,
edges, sources, authorities, node_sources, node_authorities, revisions).
Каждая миграция — отдельный файл в `src/main/resources/db/changelog/changes/`.
Smoke-тест через Testcontainers.

---

## 2026-04-13 — Сессия 0.5 (reorg → монорепа)

### Сделано
- Реорганизована структура проекта в монорепу с независимыми подпапками:
  - Корень: `README.md`, `docker-compose.yml`, `.gitignore`, `docs/` (общее)
  - `backend/` — Java/Spring Boot часть со своим `CLAUDE.md` и `docs/`
  - `frontend/` — появится на Этапе 7
- Документация разделена на общую (продуктовую) и специфичную для технологии:
  - Общее (`docs/`): architecture, er-diagram, glossary, roadmap, progress,
    decisions, gotchas, api-contract
  - Бэкенд (`backend/docs/`): coding-standards, antipatterns, api-design
- Создан `docs/api-contract.md` — пустой шаблон источника истины для
  контракта между беком и фронтом
- Добавлен **ADR-005** в `decisions.md` — решение о монорепе
- Расширен Этап 7 в `roadmap.md` — вместо заглушки полноценный план
  фронтенда (выбор фреймворка, библиотеки графов, подготовка, MVP)
- Обновлён `backend/CLAUDE.md`:
  - Добавлен раздел "Контекст: это монорепа" с правилами границ
  - Пути к общей документации через `../docs/`
  - Сессии помечаются префиксом `(backend)` в общем journal
- Создан корневой `README.md` с описанием структуры и принципов

### Решения
- ADR-005: монорепа с двумя независимыми папками, без специализированных
  инструментов (Nx/Turborepo). Простая модель, каждая часть независима.
- Claude Code запускается внутри подпапки (`cd backend && claude`),
  не в корне репы — читает свой локальный `CLAUDE.md`
- Сессии в общем `progress.md` помечаются префиксом `(backend)` / `(frontend)`
  для визуального разделения

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Важно: работать **внутри `backend/`**. Код Spring Boot проекта создаётся
в `backend/`, не в корне репы.

1. `cd backend`
2. Сгенерировать Maven-проект: Java 21, Spring Boot 3.3+, зависимости:
   `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `liquibase-core`,
   `postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
3. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из корневого `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: заглушка, настоящая конфигурация Testcontainers появится на Этапе 1
4. Создать пустой `db.changelog-master.xml` с валидной структурой
5. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается
6. Первый коммит: `chore(backend): initial spring boot project setup`

Также добавить ADR-004 (Maven vs Gradle) в `../docs/decisions.md`.

После Этапа 0 — переход к Этапу 1 (Liquibase-миграции схемы БД).

---

## 2026-04-13 — Сессия 0 (инициализация)

### Сделано
- Обсуждена идея проекта: API-first инструмент для argument mapping
- Выбран стек: Java 21, Spring Boot 3.3+, PostgreSQL 16, Liquibase, JDBC Template, Testcontainers
- Спроектирована архитектура и доменная модель (Topic, Node, Edge, Source, Authority, Revision)
- Создана полная документация проекта:
  - `CLAUDE.md` — конфиг для Claude Code
  - `docs/architecture.md`, `docs/er-diagram.md`, `docs/glossary.md` — архитектура и термины
  - `docs/roadmap.md` — план работ по этапам
  - `docs/decisions.md` — три первых ADR
  - `docs/gotchas.md` — шаблон + первые ловушки
  - `docs/progress.md` — журнал сессий (этот файл)
  - `docs/coding-standards.md` — принципы, SOLID, правила Java-кода, комментариев, тестов
  - `docs/antipatterns.md` — что не делаем в Java/SQL/REST
  - `docs/api-design.md` — правила дизайна REST API
- Настроен `docker-compose.yml` с Postgres 16

### Решения
- См. `docs/decisions.md`:
  - ADR-001: JDBC Template вместо JPA
  - ADR-002: Source и Authority как отдельные справочники, не узлы графа
  - ADR-003: Граф в двух таблицах (nodes + edges) с дискриминатором

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Конкретно:
1. Сгенерировать Maven-проект (Spring Initializr или вручную): Java 21,
   Spring Boot 3.3+, зависимости: `spring-boot-starter-web`,
   `spring-boot-starter-jdbc`, `liquibase-core`, `postgresql`,
   `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
2. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: Testcontainers поднимает свой Postgres
3. Создать пустой `db.changelog-master.xml`
4. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается (без миграций — это Этап 1)
5. Создать первый коммит: `chore: initial spring boot project setup`

После этого — переход к Этапу 1 (Liquibase-миграции схемы БД).
