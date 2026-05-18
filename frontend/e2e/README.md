# E2E suite (Playwright)

Playwright-based end-to-end тесты против запущенных backend (:9090) и
frontend (:5173) dev-серверов. Покрывают critical user journeys:
auth, topics CRUD, граф, library, Q&A, settings, admin RBAC.

## Setup

Первый запуск - установи chromium:

```bash
cd frontend
npm install                 # @playwright/test и зависимости
npx playwright install chromium
```

Должны быть запущены оба dev-сервера:

```bash
# из корня проекта
docker compose up -d        # postgres

# backend, отдельный терминал
cd backend && ./mvnw spring-boot:run

# frontend, отдельный терминал
cd frontend && npm run dev
```

## Запуск

```bash
# все тесты (sequential, ~1 минута)
npm run e2e

# headed mode (виден браузер - требует X-сервер, в WSL2 нужен WSLg)
npm run e2e:headed

# debug mode (пошаговый прогон через Playwright Inspector)
npm run e2e:debug

# открыть HTML отчёт после прогона
npm run e2e:report

# конкретный suite
npx playwright test auth.spec.ts

# конкретный тест по описанию
npx playwright test -g "login - admin"
```

## Структура

```
frontend/e2e/
├── README.md             - этот файл
├── playwright.config.ts  - в корне frontend/ (не здесь)
├── helpers/
│   ├── auth.ts           - login/logout/register/clearAuth
│   ├── topics.ts         - createTestTopic/deleteTopic
│   └── fixtures.ts       - buildMinimalPdfBuffer для upload-тестов
├── auth.spec.ts          - 7 tests: login/register/protected/logout/refresh
├── topics.spec.ts        - 7 tests: CRUD + visibility + import/export
├── graph.spec.ts         - 7 tests: nodes/multi-select/z-order/palette
├── library.spec.ts       - 6 tests: browse/search/filter/upload modal
├── qa.spec.ts            - 5 tests: question/answer/list/search
├── settings.spec.ts      - 7 tests: language/theme/font/persist
└── admin.spec.ts         - 5 tests: ADMIN access / non-admin redirect
```

**Итого: 44 теста.**

## Помощники (helpers/)

### auth.ts

```typescript
// Логин админом через UI
await loginAsAdmin(page);

// Login любым user через форму
await login(page, 'user@example.com', 'password');

// Регистрация нового user'а - возвращает credentials
const creds = await registerNewUser(page, 'prefix');
// creds.email, creds.username, creds.password

// Logout через AvatarMenu
await logout(page);

// Чистка auth state (cookies + localStorage)
await clearAuth(page);
```

### topics.ts

```typescript
// Создать тему. Возвращает topicId (UUID из URL)
const topicId = await createTestTopic(page, {
  title: 'My topic',
  description: 'опционально',
  rootQuestion: 'Корневой вопрос?',
  visibility: 'PRIVATE' | 'SHARED' | 'PUBLIC',  // default PRIVATE
});

// Удалить тему (через Settings drawer + typing-name confirmation)
await deleteTopic(page, topicId, 'My topic');
```

### fixtures.ts

```typescript
// Минимальный valid PDF blob - для upload-тестов AdminShamelaPage
const pdfBuffer = buildMinimalPdfBuffer();
await fileInput.setInputFiles({
  name: 'test.pdf',
  mimeType: 'application/pdf',
  buffer: pdfBuffer,
});
```

## Конвенции

- **Workers=1, fullyParallel=false** - тесты делят БД, гонять в
  параллели нельзя без per-worker schema (backlog)
- **Каждый тест начинается с `clearAuth`** - изоляция auth state
- **Используй `loginAsAdmin`** где не критичен USER-уровень. Для RBAC
  тестов - регистрация нового USER через `registerNewUser`
- **Selectors** - сначала `getByRole`, потом `getByLabelText`, потом
  `getByText`, в крайнем случае CSS-классы. Tailwind classes часто
  меняются - не основывай selectors на них
- **`dialog[open]`** вместо `getByRole('dialog')` - в DOM рендерятся
  несколько `<dialog>` элементов параллельно (AddNode/AddEdge/Settings),
  open=true атрибут даёт активный
- **sr-only элементы** (visibility radios) - используй
  `dispatchEvent('click')` или `check({force:true})` чтобы обойти
  overlay-икoнки которые перехватывают pointer
- **timeouts** - `5_000` для DOM updates, `10_000` для navigation,
  не используй `waitForTimeout` если можно дождаться элемента
- **Названия тестов** - русский (как unit-тесты фронта, см.
  `frontend/CLAUDE.md` раздел "Тесты")

## Артефакты

После прогона:

- `e2e-report/` - HTML отчёт (открой через `npm run e2e:report`)
- `test-results/` - per-failure артефакты: screenshots, videos, traces

Все три каталога в `.gitignore`. Не коммитим.

## Known issues

- **WSL2 без X-сервера** - только `headless: true` (default). Для
  `--headed` нужен WSLg либо X-forwarding
- **chrome-headless-shell** - playwright 1.60+ использует отдельный
  binary. Если падает с "Executable doesn't exist" -
  `npx playwright install chromium` (требуется ~150 MB download)
- **AnswersSection.isAsker** - использует `VITE_DEV_USER_ID` (а не
  authStore) - accept-кнопка не показывается через UI. Известный
  legacy issue, accept-test через UI не делаем
- **EN locale** - в `DICTIONARY` нет EN-словаря (только ru/ar),
  EN-кнопка делает fallback на ru-tokens. Не баг в e2e, but: рендер
  на EN визуально идентичен ru
- **react-flow drag-drop edge creation** - в headless нестабильно
  (нужны pixel-точные координаты handles). Backend coverage на
  API-уровне покрывает этот flow

## CI

В `playwright.config.ts`:
- `retries: process.env.CI ? 1 : 0` - 1 retry в CI, 0 локально
- Скриншоты/видео `only-on-failure` - в артефактах CI остаются только
  при провалах

Локально перед коммитом - запускай `npm run e2e` минимум один раз.
В CI - часть pipeline (TODO когда настроим).
