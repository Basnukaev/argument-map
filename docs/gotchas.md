# Ловушки проекта (Gotchas)

Специфичные для проекта подводные камни, которые легко забыть и наступить
на грабли второй раз. Добавлять сюда всё, что однажды потратило время
на дебаг и может повториться.

> **Статус gotcha:** если внутри блока есть запись `Update YYYY-MM-DD
> (сессия N): ...` подтверждающая что фикс применён - это **resolved**.
> Решённые gotcha **не удаляются** - служат retrospective контекстом
> при разборе похожих проблем. Сейчас resolved: `@CurrentUser` (Сессия 16).
> При накоплении 3+ resolved - вынести в отдельный архивный файл
> (D-06 audit, defer).

Формат:
```
## Короткое название
**Симптом:** что видно
**Причина:** что на самом деле происходит
**Решение:** как исправить или обойти
```

---

## Tiptap CSS темизация - `prefers-color-scheme` ≠ manual override

**Симптом:** user выбрал в Header «Светлая» тема на dark системе ОС.
Все семантические компоненты (Modal, Card, Header) показывают light
правильно, но HadithBox / AyahBox / Marginalia / Footnote и др.
Tiptap extensions рендерятся в **dark variant** (peach текст на тёмном
фоне на белой странице - выглядит сломанно)

**Причина:** `src/styles/tiptap.css` использовал
`@media (prefers-color-scheme: dark) { .hadith-box {...} }`. Это media
query из браузера, читает системное предпочтение ОС - **не знает про
наш themeStore manual override** (`data-theme="dark"` на html).

ThemeStore с режимом `system` - читает то же `prefers-color-scheme` и
синхронизируется. Но при `mode: 'light' | 'dark'` (explicit override)
у нас рассинхронизация: themeStore показывает что user хочет light,
а CSS из браузерного media query всё равно применяет dark variant
extensions.

**Решение:** заменить `@media (prefers-color-scheme: dark)` на
`[data-theme='dark'] { ... }` (single source of truth с themeStore +
ThemeEffect). Использовать CSS native nesting (Chrome 112+, Firefox
117+, Safari 16.5+):

```css
/* было */
@media (prefers-color-scheme: dark) {
  .hadith-box { background: rgb(127 29 29 / 0.2); }
}

/* стало */
[data-theme='dark'] {
  .hadith-box { background: rgb(127 29 29 / 0.2); }
}
```

**Update 2026-05-17 (Сессия 41):** все 7 dark-блоков в tiptap.css
переведены на `[data-theme='dark']`. ThemeEffect ставит data-theme
на `<html>` по effectiveTheme - extensions теперь синхронизированы
с user preference.

**Правило для будущего:** в CSS приложения - **никогда**
`prefers-color-scheme`. Только в bootstrap inline script
(`index.html` FOUC prevention). Все component-level dark styles -
через `[data-theme='dark']` ИЛИ через семантические CSS variables
(`var(--c-bg)` / `var(--c-text)` etc - они уже переключаются).

---

## Tashkeel removal через ProseMirror JSON transform (закрыто)

**Симптом исторически:** Reader имел кнопку «С огласовками / Без
огласовок» которая ставила класс `.hide-tashkeel` на article-wrapper,
но визуально текст **не менялся** - арабские диакритические знаки
(`َ`, `ِ`, `ُ`, `ْ`, `ّ`, `ٰ`) оставались на экране даже при
`hideTashkeel=true`. Это было MVP placeholder в Этапе 17.0.c.

**Причина:** диакритические знаки (Unicode range `U+064B`-`U+065F` +
superscript alef `U+0670`) - это **combining characters**, не отдельные
glyphs. Чистый CSS не может их «скрыть» через `display: none` или
`visibility: hidden` - они часть text node того же символа. Реальные
способы убрать огласовки:

1. **JSON transform перед render** - модифицировать ProseMirror
   document tree (заменить `text` поля у text-nodes через regex)
   **до** того как Tiptap отрендерит. Functional, React-friendly,
   без DOM-walk
2. **runtime regex по DOM** через `TreeWalker(NodeFilter.SHOW_TEXT)`
   после mount + замена `textContent`. Конфликтует с React reconciler
3. **font-feature-settings** через специальный шрифт где tashkeel -
   separate ligature glyphs (требует custom font asset)
4. **double render** - хранить два text representation в данных и
   переключаться между ними (raise data volume + breaks editing UX)

**Решение (закрыто):** выбрана опция 1 - чистая functional
трансформация ProseMirror JSON. Утилиты в
`frontend/src/shared/components/editor/utils/stripTashkeel.ts`:

- `stripTashkeelText(s)` - regex `/[ً-ٰٟ]/g` по строке
- `stripTashkeelFromDoc(doc, strip)` - рекурсивный walk JSON-tree,
  трансформ text-nodes, сохранение marks и attrs (включая сам
  `tashkeel` mark - он остаётся как семантический маркер)

`RichTextRenderer` принимает `hideTashkeel: boolean` prop, через
useMemo вычисляет processed content и передаёт в Tiptap. Toggle
обратно - тот же useMemo с другим input, возвращает оригинал
(идемпотентно, без mutation). Tatweel `U+0640` НЕ удаляется -
это горизонтальное растяжение буквы (каллиграфия), не диакритик;
отдельный feature в backlog при необходимости.

В legacy fallback path (когда `page.formattedContent` = null и
рендерится sanitized HTML) `hideTashkeel` применяется через
`stripTashkeelText` к raw text до `sanitizePageHtml`.

Покрытие: 17 тестов (15 для утилит + 2 интеграционных в
RichTextRenderer) - идемпотентность, рекурсия nested структур,
сохранение marks и attrs, no-mutation, latin/tatweel
негативные случаи.

---

## Каждый PdfSourceProvider должен явно поддержать новый source type
**Симптом:** Загруженный через `POST /api/v1/library/imports/file` PDF
успешно появляется в MinIO + `library_files` (status 201), но при
открытии reader'а на frontend `/books/{id}` бесконечный спиннер.
Backend log: `GET /api/v1/library/books/{id}/pdf/info` → 404
`pdf-not-available`. Это происходит **только** для user-uploaded книг -
shamela-импорт работает нормально.

**Причина:** `PdfService.findProvider` итерирует все
`PdfSourceProvider` bean'ы и берёт первого где `supports(book) == true`.
До Этапа 16.h единственный provider был `PdfLinksSourceProvider`,
который смотрит только `metadata.pdf_links` - shamela ETL пишет это
поле, `FileImportService` нет (он пишет `user_uploaded:true` +
`original_filename` + `pdf_page_count` без pdf_links). Соответственно
для USER_UPLOAD книги ни один provider не возвращал supports=true.

Тот же класс проблем повторится при добавлении нового способа создания
Book - direct archive.org by ID, IIIF манифест, OCR-only ввод
(Этап 17) и т.д.

**Решение (post-fix):** добавлен `UserUploadProvider` (Этап 16.h,
`@Order(50)`) который опрашивает `library_files.findActiveByBookId-
AndSourceType(bookId, USER_UPLOAD)`. PdfService теперь имеет два
зарегистрированных provider'а, supports разделяется по source-type.

**Превентивный паттерн:** при введении нового способа создания Book
обязательно прогнать **smoke** через full read-flow:
1. создать книгу через новый путь
2. `curl GET /api/v1/library/books/{id}/pdf/info` → должен быть 200
3. `curl GET /api/v1/library/books/{id}/pdf?fileIndex=0` → должен
   вернуть application/pdf

Если шаг 2 или 3 даёт 404 `pdf-not-available` - нужен новый
`PdfSourceProvider` (или расширение supports() в существующем).
E2E test `FileImportControllerIT.POST_upload_thenGET_pdfInfo_...`
служит регрессионным якорем для этого паттерна - дублировать для
новых способов.

**Связано с:** ADR-021 source-first нумерация, ADR-024 object storage,
ADR-035 PDFBox.

---

## lib_pages.id стабильность через mapper skip-if-existing
**Симптом:** Можно ожидать что при re-import shamela master metadata
`lib_pages` пересоздаются с новыми UUID, что сломает citation.page_id refs
(FK ON DELETE RESTRICT блокирует delete существующих pages пока на них
ссылается citation - выглядит как «не могу обновить книгу»).

**Причина:** `ShamelaToLibraryMapper.mapBook` (строки 96-102) делает
`findByShamelaBookId` check **до** перемаппинга и returns `alreadyMapped`
если book уже импортирована. `lib_pages` **не** пересоздаются для
existing books. `PageRepository.save` - чистый INSERT (без UPSERT), но
он не вызывается для re-import scenarios потому что mapper выходит раньше.

**Решение:** Этот invariant **полагается на skip-if-existing**. Текущее
поведение удовлетворяет требование стабильности `page_id` для citation
FK с `ON DELETE RESTRICT` (ADR-026, ADR-027). Если в будущем потребуется
обновлять контент страниц при re-import (например после shamela major
release update), нужно сменить mapper на UPSERT по композитному ключу
`(book_id, page_number)` через `INSERT ... ON CONFLICT DO UPDATE
RETURNING id` - чтобы UUID оставался стабильным. Сейчас этого не
требуется (Этап 18.f citation), зафиксировано как design decision.

**Связано с:** ADR-026, ADR-027 (citation stability requires stable
page_id refs).

---

## Циркулярный внешний ключ topics ↔ nodes
**Симптом:** Liquibase падает при создании таблиц `topics` и `nodes` из-за
взаимных FK (`topics.root_node_id → nodes.id`, `nodes.topic_id → topics.id`)

**Причина:** две таблицы ссылаются друг на друга, нельзя создать FK до
создания обеих таблиц

**Решение:** создавать таблицы в таком порядке:
1. `topics` — без `root_node_id` (или nullable без FK)
2. `nodes` — с FK на `topics.id`
3. Отдельная миграция: `ALTER TABLE topics ADD CONSTRAINT ...`
добавляющая FK `topics.root_node_id → nodes.id`

---

## Liquibase lock после упавшей миграции
**Симптом:** Liquibase зависает с сообщением "waiting for changelog lock"

**Причина:** предыдущая миграция упала и не отпустила lock в таблице
`databasechangeloglock`

**Решение:**
```sql
UPDATE databasechangeloglock SET locked = false, lockgranteddate = null, lockedby = null WHERE id = 1;
```
Или полная очистка: `DELETE FROM databasechangeloglock;`

---

## Символ `&` в Liquibase XML-changeset'ах
**Симптом:** Liquibase падает при парсинге миграции с ошибкой
`The entity name must immediately follow the '&' in the entity reference`
(SAXParseException)

**Причина:** в XML символ `&` зарезервирован под entity-ссылки
(`&amp;`, `&lt;`, и т.п.). Ломается даже в комментариях `<comment>` и
обычных `<sql>`-блоках

**Решение:**
- в тексте комментариев избегать `&` (переформулировать) или экранировать `&amp;`
- в SQL-блоках, где `&` нужен (например, jsonb-оператор `?&`) — оборачивать
  содержимое в `<![CDATA[ ... ]]>`

---

## PG JDBC не выводит SQL-тип для Instant
**Симптом:** при вставке через `jdbcTemplate.update(..., instant)` драйвер
падает с `PSQLException: Can't infer the SQL type to use for an instance
of java.time.Instant. Use setObject() with an explicit Types value`

**Причина:** `Instant` не несёт zone-информации, а PG-драйвер отказывается
делать имплицитный выбор между `timestamp` и `timestamptz`. Для других
типов (`UUID`, `String`, `Integer`) вывод работает

**Решение:** конвертировать `Instant` → `OffsetDateTime.ofInstant(instant,
ZoneOffset.UTC)` перед передачей в `jdbcTemplate`. На чтение —
`rs.getObject("col", OffsetDateTime.class).toInstant()`. В проекте есть
утилита `repository.JdbcTimes` с методами `odt(Instant)` и `instant(ResultSet, String)`

---

## Failsafe plugin в Spring Boot parent — только pluginManagement
**Симптом:** `./mvnw verify` запускает только `*Test`-классы, игнорирует
`*IT`-классы. Smoke-тест проходит, интеграционные не запускаются

**Причина:** Spring Boot parent объявляет `maven-failsafe-plugin` в
`pluginManagement` (фиксирует версию), но не привязывает goal'ы.
Surefire (default) сканирует только `*Test`, Failsafe — только `*IT`,
но без явного `<execution>` Failsafe не запустится

**Решение:** добавить в `pom.xml` в блок `<plugins>`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## npm 9 (Debian/WSL apt-пакет) криво работает с proxy-auth
**Симптом:** `npm install <package>` через корпоративный прокси
возвращает `npm ERR! code E407 - 407 Proxy Authentication Required`,
при том что `curl -x "$HTTPS_PROXY" https://registry.npmjs.org/...`
успешно качает с теми же кредами

**Причина:** npm 9.x (в частности 9.2.0 из репов Debian/Ubuntu) не
передаёт заголовок `Proxy-Authorization` корректно. Ни флаги
`--proxy`/`--https-proxy`, ни env-переменные `npm_config_proxy` не
помогают. Только запись кредов в `.npmrc` файл срабатывает - но
ставит креды на диск в открытом виде

**Решение:** обновить npm до 10.x: `npm install -g npm@latest`. После
этого env-переменные `HTTPS_PROXY`/`HTTP_PROXY` подцепляются
автоматически, ничего настраивать не нужно

---

## Vite HMR не работает на проекте под `/mnt/c/*` в WSL2
**Симптом:** правки в `.tsx`/`.ts`/`.css` не применяются в браузере. Только
полный перезапуск `npm run dev` показывает изменения. В консоли Vite не
видно сообщений типа `[vite] hmr update /src/...`

**Причина:** проект лежит на NTFS-разделе Windows, который доступен в
WSL2 через DrvFs/9P. Эта файловая система **не транслирует inotify-события**
в WSL. Chokidar (которым пользуется Vite) ждёт событий и ничего не
получает - HMR молчит

**Решение:** включить polling в `vite.config.ts`:
```ts
server: {
  watch: {
    usePolling: true,
    interval: 300,
  },
},
```

Опрос дороже по CPU (~1-3% постоянной нагрузки), но HMR начинает
срабатывать на каждое сохранение. Альтернатива - перенести проект
внутрь WSL-файловой системы (`~/projects/...`) - тогда inotify работает
нативно и polling не нужен. Но для монорепы с Windows-IDE это
неудобно

---

## Springdoc-openapi не знает про `@CurrentUser` - показывает `userId` как query
**Симптом:** в `/v3/api-docs` для мутирующих эндпоинтов (POST/PATCH) видно
параметр `userId` типа `query` (или просто `string`), хотя реально бэк
его игнорирует и читает заголовок `X-User-Id`. После
`openapi-typescript` фронт получает в `parameters.query.userId` тип
`string` - вводит в заблуждение

**Причина:** контроллеры используют кастомный `@CurrentUser UUID userId`
параметр (резолвится `CurrentUserArgumentResolver` из заголовка
`X-User-Id`, ADR-006). Springdoc не имеет хука распознать кастомный
`HandlerMethodArgumentResolver` и интерпретирует параметр как обычный
query-string

**Решение (на фронте):** игнорировать `parameters.query.userId` -
отправлять `X-User-Id` заголовок как раньше. Бэк его всё равно читает,
а query-параметр не используется

**Решение (на беке, future task):** добавить аннотации springdoc на
параметры `@CurrentUser` или зарегистрировать `OperationCustomizer`,
который перепишет параметр на header. Альтернатива - заменить
кастомный resolver на стандартный `@RequestHeader("X-User-Id")` (но
это размыкнет ADR-006 abstraction)

**Update 2026-05-05 (сессия 16):** реализован `OperationCustomizer`
в `config/OpenApiConfig.java`. Для каждой операции с параметром,
помеченным `@CurrentUser`, customizer удаляет автогенерированный
`query.userId` и добавляет `header X-User-Id` (required, type=string,
format=uuid). После regen-api на фронте контроллеры теперь имеют
правильную типизацию `parameters.header['X-User-Id']: string` вместо
`parameters.query.userId: string`. Гочча больше не актуальна, но
оставлена для истории

---

## Tailwind v4 native binding `@tailwindcss/oxide-*` не подтягивается через прокси
**Симптом:** `npm run build` или `npm run dev` падает с
`Error: Cannot find native binding. npm has a bug related to
optional dependencies` от `@tailwindcss/oxide/index.js`

**Причина:** Tailwind v4 написан на Rust (через napi-rs),
платформо-специфичные нативные бинари упакованы как
`@tailwindcss/oxide-{linux-x64-gnu, darwin-arm64, win32-x64-msvc, ...}`
и подключаются как optionalDependencies. Через медленный/нестабильный
прокси npm иногда пропускает optional-deps без ошибки, оставляя
основной пакет установленным, но без бинаря для текущей платформы

**Решение:** поставить нужный native-биндинг явно для своей платформы:
```bash
# Linux x86_64 (WSL, Ubuntu)
npm install -D @tailwindcss/oxide-linux-x64-gnu
# macOS Apple Silicon
npm install -D @tailwindcss/oxide-darwin-arm64
# Windows x64
npm install -D @tailwindcss/oxide-win32-x64-msvc
```

Альтернатива (требует надёжного интернета): удалить `node_modules` и
`package-lock.json`, прогнать `npm install` заново

---

## React Flow `onSelectionChange` infinite loop при inline-handler
**Симптом:** "Maximum update depth exceeded" в консоли через несколько
действий с графом (drag, click). Стек: `onSelectionChange` → `setState`
→ снова `onSelectionChange` → ... до краша. Воспроизводится сложно -
зависит от количества drag'ов и порядка действий.

**Причина:** inline arrow handler `({nodes, edges}) =>
setSelectedNodeIds(nodes.map(n => n.id))` создаёт новый `[]` массив
каждый вызов. `useState` сравнивает по `Object.is(prev, next)` -
для разных ссылок всегда `false` → re-render → React Flow снова
триггерит `onSelectionChange` (например после `setNodes`) → опять
новый `[]` → бесконечный цикл

**Решение:** для всех RF callbacks (`onSelectionChange`, `onConnect`,
`onNodeDragStop` и т.д.) ВСЕГДА:
1. `useCallback(handler, [])` - стабильная ссылка не пересоздаётся
2. Функциональный setter со сравнением содержимого:
   ```ts
   setSelectedNodeIds(prev => sameIds(prev, next) ? prev : next);
   ```
   `sameIds` - поверхностное сравнение `string[]` (длина + поэлементно).
   Возвращаем `prev` (ту же ссылку) если содержимое не изменилось -
   `useState` видит ту же ссылку, не вызывает re-render

См. `TopicGraphPage.tsx:handleSelectionChange` + helper `sameIds()`.

---

## React Flow `elevateNodesOnSelect=true` по дефолту перетирает явный zIndex
**Симптом:** "На задний план" из контекстного меню как будто не работает -
узел остаётся поверх остальных. Стоит снять выделение (клик на pane) -
сразу уезжает на задний план

**Причина:** RF default `elevateNodesOnSelect=true` (и для рёбер тоже).
RF автоматически кладёт selected узел поверх остальных через внутренний
zIndex-boost. Это перебивает наш явный `node.zIndex` из контекстного меню

**Решение:** на `<ReactFlow>` поставить
```tsx
elevateNodesOnSelect={false}
elevateEdgesOnSelect={false}
```
Тогда selected узел остаётся в своём слое (синяя обводка показывает
выделение, но не меняет visually order). Z-order контролируется только
явным `zIndex`

---

## Stale closure в `useCallback` для handler'ов с динамическими данными
**Симптом:** первый клик на пункт меню/кнопку работает корректно, но
последующие клики используют устаревшие данные. Например: после клика
"Добавить связанный узел" первый узел встаёт в свободное место, второй
накладывается ровно туда же - findFreePosition не "видит" только что
добавленный узел.

**Причина:** useCallback закешил handler с deps `[setNodes, ...]`,
не включая сам массив `nodes` в deps. Внутренний onClick читает `nodes`
через closure - получает snapshot из render'а где callback был
впервые создан. После refetch nodes обновился, но handler остался
старым - читает прежний (stale) snapshot.

Если включить `nodes` в deps - useCallback пересоздаётся каждый
drag/resize узла, что плохо для perf. eslint-disable + явное чтение
из ref - чище.

**Решение:** хранить актуальный snapshot в `useRef`, обновлять через
`useEffect`, читать `.current` внутри onClick:

```ts
const lastNodesRef = useRef<NodeCardNode[]>([]);
useEffect(() => { lastNodesRef.current = nodes; }, [nodes]);

const handleContextMenu = useCallback(/* deps без nodes */ ..., [setNodes]);
//   onClick: () => {
//     const currentNodes = lastNodesRef.current;  // всегда свежий
//     ...
//   }
```

Это правило для любых RF callbacks которые работают с динамическими
коллекциями (nodes/edges) и не должны пересоздаваться часто.

См. `TopicGraphPage.tsx:handleNodeContextMenu` + `lastNodesRef`.

---

## `layoutGraph` mixed-режим перебрасывает fresh узлы при появлении одного saved
**Симптом:** на свежей теме (узлы без `posX/posY` на бэке, dagre
расставил их на фронте) добавление нового узла через "Добавить
связанный X" (новый получает координаты через PATCH) приводит к тому,
что старые узлы перепрыгивают - все становятся в столбец справа от
нового.

**Причина:** `layoutGraph` имеет три режима:
1. `allSaved` - все узлы с posX/posY → as-is
2. `noneSaved` - все без → dagre
3. `mixed` - часть saved, часть нет → saved as-is, fresh **столбцом
   справа** от saved-кластера

Когда новый узел становится первым saved (через PATCH из
findFreePosition), все остальные узлы попадают в "столбец справа" -
теряют свои dagre-позиции с предыдущего render'а.

**Решение:** два слоя защиты:

1. **Backfill posX/posY на первой загрузке** - в Graph component
   useEffect: для каждого узла без posX/posY на бэке шлём PATCH с
   позицией которую только что вычислил dagre на фронте. Через ~1-2
   сек граф становится `allSaved=true`, дальше mixed не возникает.

2. **`previousNodes` hint в layoutGraph** - принимает прежний массив
   узлов (через `lastNodesRef.current` из RF state). В mixed-режиме
   для fresh узла, который УЖЕ был на канвасе - возвращаем его
   прежнюю позицию (а не "столбец справа"). Только совершенно новые
   узлы которых не было в previous идут в "столбец".

См. `graphLayout.ts:layoutGraph` + `TopicGraphPage.tsx:lastNodesRef`
useEffect для backfill.

---

## Tailwind v4 preflight ломает центрирование нативного `<dialog>` (top-layer)
**Симптом:** модалки на `<dialog>` с `showModal()` открываются прижатыми
к левому верхнему углу viewport, а не центрированно. Каждая модалка в
проекте (`AddNodeModal`, `AddEdgeModal`, `AddSourceModal`,
`AddAuthorityModal`) ведёт себя одинаково - заголовок касается top-bar
страницы

**Причина:** UA-stylesheet браузера для `<dialog>` устанавливает
`margin: auto; inset: 0` для центрирования через flexbox на top-layer.
Tailwind v4 preflight применяет `* { margin: 0 }` на широкий список
селекторов и затирает UA-margin на dialog. В результате `inset: 0`
расширяет dialog на весь viewport (через width:100% от родителя), но
margin: 0 фиксирует элемент в top-left угла без центрирования

**Решение:** добавить `m-auto` в className `<dialog>`. Это восстанавливает
поведение UA-stylesheet и работает для top-layer:
```tsx
<dialog
  className={`m-auto w-full ${maxWidth} rounded-lg ...`}
/>
```

Фикс делается один раз в общем компоненте `Modal` - все модалки проекта
наследуют. Альтернатива - явное центрирование через `fixed inset-0 flex
items-center justify-center` на wrapper - даёт больше контроля, но overkill
для нативного dialog где UA уже всё умеет

См. `frontend/src/components/ui/Modal.tsx`. Зафиксировано в сессии 18
после жалобы пользователя что модалки в углу

---

## `react-hooks/set-state-in-effect` блокирует useEffect-сброс state модалки
**Симптом:** ESLint ругается `Avoid calling setState() directly within an
effect` (правило `react-hooks/set-state-in-effect`) на типичный паттерн
"если модалка закрыта - очистить поля":

```ts
useEffect(() => {
  if (!open) {
    setQuery('');
    setSelectedId(null);
    // ... очистка остального state
  }
}, [open]);
```

**Причина:** правило справедливо: setState в useEffect-е каскадирует
re-render'ы и затрудняет отладку. Альтернативные стандартные паттерны:
1. `key`-trick на родителе - `<Modal key={String(open)} ... />`. Сложно
   объяснимое решение, выглядит как побочный эффект. Используется в
   проекте (см. memory `feedback_react_key_remount.md`)
2. Сбрасывать state в event handler'ах (handleClose, handleSubmit
   success-branch). Работает, но размазывает reset по нескольким
   местам. Легко забыть один путь
3. **Conditional render родителя** - `{open && <Modal/>}`. Модалка
   монтируется при открытии, размонтируется при закрытии. State
   всегда свежий - `useState` инициализируется при mount

**Решение для одноразовых модалок (`AddSourceModal`, `AddAuthorityModal`,
обычно любые модалки c POST/PATCH):** выбрать вариант 3.

```tsx
// Родитель:
{addSourceOpen && node.id && (
  <AddSourceModal
    nodeId={node.id}
    onClose={() => setAddSourceOpen(false)}
    onAttached={loadSources}
  />
)}

// Сам компонент: убрать prop `open`, useEffect для load
// без зависимости от open. Внутри Modal всегда `open` prop.
function AddSourceModal({ nodeId, onClose, onAttached }) {
  const [state, setState] = useState({ kind: 'loading' });
  useEffect(() => {
    apiGetRaw(...).then(...).catch(...);
  }, []);

  return <Modal open onClose={...}>...</Modal>;
}
```

**Когда conditional render не подходит:** если модалка имеет открытие/
закрытие анимацию (slide-in/slide-out) - unmount во время closing
animation её обрезает. Тогда варианты 1 или 2. У нас Modal на нативном
`<dialog>` без animation - conditional render идиоматично.

См. `AddSourceModal.tsx`, `AddAuthorityModal.tsx`, `NodeDetailsPanel.tsx`
(conditional render внутри `<aside>`).

---

## Java HttpClient блокирует Basic auth для HTTPS-прокси по умолчанию

**Симптом:** запрос через `java.net.http.HttpClient` к HTTPS-сайту
через прокси с авторизацией возвращает `HTTP 407 Proxy Authentication
Required` несмотря на правильно установленный
{@link java.net.Authenticator}. Authenticator не вызывается при 407
challenge. Тот же прокси с теми же кредами успешно работает через
curl/python.

**Причина:** с Java 8u11+ существует
`jdk.http.auth.tunneling.disabledSchemes=Basic` (системное свойство).
Это блокирует Basic-auth для HTTPS-туннеля (метод CONNECT) -
исторически из соображений безопасности (без TLS до прокси креды
видны как plaintext в первой фазе CONNECT). Но во многих корпоративных
environments прокси требует именно Basic auth.

**Решение:** перед созданием `HttpClient` снять блок:

```java
System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
```

После этого Authenticator будет вызываться на 407 challenge.
Альтернатива - передать через JVM args `-Djdk.http.auth.tunneling.disabledSchemes=`.

**Безопасность:** Basic через CONNECT-туннель защищён шифрованием
TLS-канала, так что в корпоративных env допустимо. В open-internet
сценариях лучше использовать прокси с digest или другим методом
auth.

В проекте: `library.shamela.api.ShamelaHttpClientConfig.applyProxy()`
вызывает `setProperty` если в `HTTPS_PROXY`/`SHAMELA_PROXY` есть
`user:pass@`. Зафиксировано в Сессии 21 при прогоне `ShamelaApiClientLiveIT`
через corporate-прокси `proxys.io`.

---

## OpenApiIT.readOnlyEndpoint_doesNotGetUserIdHeader флакает в общем прогоне

**Симптом:** при `./mvnw verify` иногда падает один тест
`OpenApiIT.readOnlyEndpoint_doesNotGetUserIdHeader` с
`PathNotFoundException: Missing property in path
$['paths']['/api/v1/topics']['get']['parameters']`. При запуске
этого же теста в одиночку (`./mvnw failsafe:integration-test
-Dit.test=OpenApiIT`) - все 5 тестов проходят.

**Причина:** `springdoc-openapi 2.8.0` инициализирует
schema лениво и зависит от порядка регистрации
`HandlerMethodArgumentResolver`'ов. Когда `OpenApiIT` идёт
после других IT, кеш context'а может быть переиспользован в
состоянии где `@CurrentUser`-customizer (`OpenApiConfig`) уже
применён и удалил `parameters` массив у read-only эндпоинта
(там нет ни одного `@CurrentUser`-параметра, в результате
оригинального `userId` query нет, и springdoc просто опускает
ключ `parameters` целиком вместо пустого массива). JsonPath с
filter не отличает "нет ключа" от "ключ есть, элементов нет".

**Решение:** на MVP - принимаем как известный flake, не
рефакторим. При повторе - перезапустить только этот тест в
одиночку (он стабильно зелёный без cache poisoning).

Если станет ежепрогонным:
- либо изменить ассерт на `jsonPath("$.paths./api/v1/topics.get.parameters").doesNotExist()`
  (терпимый к отсутствию ключа)
- либо `@DirtiesContext` на `OpenApiIT` чтобы поднять свежий
  context (дороже по времени)

Зафиксировано в Сессии 21 при прогоне после миграции 17.

---

## `git mv` директорий через WSL2 на DrvFs/NTFS даёт битый inode

**Симптом:** `git mv frontend apps/argument-map` (когда `apps/`
свежесозданный mkdir-ом) выдаёт warning `could not open directory
'apps/argument-map/'`, но при этом git индекс показывает rename
успешным (RD - renamed deleted). Физически целевой каталог имеет
битый inode - `ls apps/` показывает запись `d?????????`,
`ls apps/argument-map/` падает с "No such file or directory",
исходный `frontend/` тоже исчезает целиком.

**Причина:** WSL2 на NTFS-разделе через DrvFs/9P не атомарен на
rename операциях затрагивающих несколько файлов. Когда `git mv`
обходит дерево и переименовывает каждый файл, файловая система не
успевает закрыть старую директорию перед открытием новой - получается
неконсистентное состояние с битым inode целевого каталога.

**Решение:**
- `git reset --hard HEAD` восстанавливает файлы (всё в HEAD на месте,
  только индекс битый - reset чистит индекс). Все коммиты сохраняются
- Для будущих рефакторингов структуры через WSL2: использовать
  файл-менеджер Windows (Total Commander Move F6 / Проводник Cut+Paste)
  для физического перемещения, потом `git add -A` чтобы git распознал
  rename. Это атомарно работает через NTFS APIs, в обход WSL DrvFs

**Альтернатива** (если проект перенести в WSL-нативную ФС):
проблема уходит сама - inotify, rename, и прочее работают нативно
без 9P-обёртки. Но для монорепы с Windows-IDE неудобно.

Зафиксировано в Сессии 23 при попытке реструктуризации
`frontend/` → `apps/argument-map/` (потом откатили целиком, перешли
к single-page подходу).

---

## Shamela book-archive содержит `{bookId}-{major}.sqlite`, не `{bookId}.sqlite`

**Симптом:** `ShamelaImportService.importBook(1681)` падает с
`ShamelaImportException: ожидаемый SQLite-файл отсутствует в архиве:
1681.sqlite`. Архив `1681-6.zip` скачан и распакован, но внутри -
файл `1681-6.sqlite`, а не `1681.sqlite`.

**Причина:** реальный naming convention shamela:
- `{bookId}-{majorRelease}.sqlite` - наблюдалось для major_release=6+
  (книга 1681 Сахих аль-Бухари с major_release=6)
- `{bookId}.sqlite` - предполагалось ранее на основе общей логики
  `{bookId}-{major}.zip` → `{bookId}.sqlite`. Возможно встречается в
  старых major versions, но live-проверкой не подтверждено

Изначальный код жёстко требовал `{bookId}.sqlite` (литералом
`bookId + ".sqlite"`), что разваливается на real-data.

**Решение:** `ShamelaImportService.findBookSqlite(extractedDir, bookId, majorRelease)`
с tolerant lookup в порядке:
1. `{bookId}-{major}.sqlite` (актуальный формат)
2. `{bookId}.sqlite` (legacy fallback)
3. `Files.walk` по `extractedDir` - если найден ровно один `.sqlite`
   файл, берём его (защита на случай если shamela изменит формат
   снова)

Если ни один не найден или найдено несколько `.sqlite` файлов на
fallback-walk - бросаем `ShamelaImportException` с диагностикой
(сколько найдено + что искали).

**Live-проверка:** book 1681 (Сахих аль-Бухари, major_release=6)
импортируется успешно после фикса. Тестовые fixture в
`ShamelaImportServiceIT` обновлены на правильный naming
`{bookId}-{major}.sqlite`.

Зафиксировано в Сессии 23 при первом импорте через AdminShamelaPage UI.

---

## Springdoc-openapi 2.x теряет self-referential property в schema

**Симптом:** `ChapterResponse` имеет поле `List<ChapterResponse> children`
для nested tree. На бэке JSON ответа в `GET /api/v1/library/books/{id}`
действительно содержит `children: [{...nested}]`. Но в
`/v3/api-docs` schema выглядит так:

```json
{
  "ChapterResponse": {
    "type": "object",
    "properties": {
      "id": {...},
      "title": {...},
      "orderIndex": {...},
      "parentChapterId": {...},
      "startPageNumber": {...}
    }
  }
}
```

Поля `children` нет вообще. После `npm run generate-api` фронт
получает тип без `children` и теряет типизированный доступ к
рекурсивной структуре.

**Причина:** springdoc-openapi 2.x определяет рекурсивные типы как
циклическую ссылку и **выкидывает** self-referential property целиком
из schema чтобы не зациклиться. Использует обнаружение через
`io.swagger.v3.core.util.ModelResolver` которое не различает
self-reference (`List<Self>`) от настоящего цикла без терминатора.
Это known limitation, обсуждается в issues, без официального fix
на 2.8.0.

Возможные решения на стороне бэка:
1. `@Schema(implementation = ChapterResponse.class)` на поле -
   не работает, springdoc всё равно проверяет цикл
2. Кастомный `ModelConverter` - тяжёлая инфраструктура для одного
   поля
3. Раздельный DTO `ChapterTreeNode` (тот же набор полей + children)
   и `ChapterResponse` (без children) - дублирование, но springdoc
   их разведёт

**Решение (на MVP):** на фронте self-referential intersection:

```ts
// types.ts (auto-gen) не имеет children для ChapterResponse, расширяем
type Chapter = components['schemas']['ChapterResponse'] & {
  children?: Chapter[];
};
```

В runtime поле приходит, типизация добавлена руками. После каждой
regen-api intersection остаётся (не схлопывается, потому что бэк
не отдаёт children в schema).

**Альтернатива при следующем витке library-фичей** - DTO-split на
бэке. Когда добавим `imageRegion` или похожее nested - перейти
сразу на 3й вариант.

Зафиксировано в Сессии 24 при fix sub-chapters рендера. Bug проявился
после того как мы сначала имели **двойную сборку tree** (бэк + фронт)
и фронт делал sortRecursive на пустых children. Когда убрали
front-side build - проявилось что types.ts вообще не знал про
children. Косвенно это связано с другим известным springdoc-gotcha
про `@CurrentUser` (см. выше) - в обоих случаях springdoc не понимает
кастомные расширения и нужны обходы.

---

## Shamela `pdf_links.cover: 1` означает что `files[0]` это обложка

**Симптом:** PdfViewer открывает книгу, react-pdf показывает numPages=3
вместо ожидаемых тысяч. Юзер видит обложку (cover) с counter
`X / 3` вместо реального содержания тома.

**Причина:** shamela / archive.org metadata формат:

```json
{
  "root": "https://archive.org/download/.../",
  "cover": 1,
  "files": ["00_113015.pdf", "01_113015p.pdf|المقدمة", "01_113015.pdf", ...]
}
```

`cover: 1` это **boolean флаг** что обложка есть. Convention где она
лежит - `files[0]` (typically named `00_*.pdf`). Если просто отдать
`fileIndex=0` юзеру в reader - попадёт на cover (3 страницы), а
реальные тома в `files[1..N]`.

**Решение:** на бэке `PdfLinksSourceProvider.getMetadata()` маркирует
первый файл `isCover=true` когда `hasCover=true`. Фронт пропускает
cover из основного potoka чтения - выбирает первый файл с
`isCover=false` как default `fileIndex`. Обложка может быть показана
отдельно (например пункт "Обложка" в dropdown), но из main paginator
она исключается.

Convention shamela устойчива на ~8500 проверенных книгах. Если когда-нибудь
сломается - backend упадёт с `fileIndex out of range` для книги где
все файлы помечены cover, что заметно.

Зафиксировано в Сессии 26 при first UX-проверке PDF Viewer на Тафсире
Ибн Касира (book 1503).

---

## AWS SDK v2 `RetryPolicy.defaultRetryPolicy().toBuilder()` - legacy API

**Симптом:** в `S3ClientConfig` retry-конфиг через
`RetryPolicy.defaultRetryPolicy().toBuilder().numRetries(N).build()` -
работает на AWS SDK 2.44.x, но в release notes 2.26+ помечен deprecated
в пользу `RetryMode` / `RetryStrategy`. Compile-warning не выпадает,
но при следующем major bump SDK поведение может измениться без
notification.

**Причина:** AWS SDK v2 в 2.26.x ввёл новую модель retry через
`RetryStrategy` interface. Старый `RetryPolicy` всё ещё работает, но
`toBuilder()` копирует default policy и переопределяет только
`numRetries` - сохраняет дефолтный `RetryCondition`, `BackoffStrategy`
и т.д. Внутренние изменения SDK могут изменить эти defaults.

**Решение (текущее, для MVP):** Оставить как есть, smoke-test
`S3ClientConfigIT` фиксирует что bean поднимается. Перед prod-deploy
переписать на `RetryStrategy`:
```java
ClientOverrideConfiguration.builder()
    .retryStrategy(AwsRetryStrategy.standardRetryStrategy()
            .toBuilder()
            .maxAttempts(maxRetries + 1)
            .build())
    .build();
```

Зафиксировано в Сессии 28 после code review этапа 25.b (ADR-024
object storage). Перенесено в Этап 25.c operational hardening
backlog в roadmap.

---

## `StreamingResponseBody` использует `SimpleAsyncTaskExecutor` по default

**Симптом:** `PdfController.streamPdf` возвращает `StreamingResponseBody`,
который Spring исполняет на отдельном thread (не на NIO). По дефолту
Spring Boot использует `SimpleAsyncTaskExecutor` - **создаёт новый
thread на каждый async request** без bounded pool. При N concurrent
PDF reads = N threads + N MinIO connections, без upper limit -
потенциальный thread exhaustion на нагрузке.

**Причина:** `SimpleAsyncTaskExecutor` - convenience для dev, не для
prod. Spring docs прямо говорят что для production нужен bounded
pool через `TaskExecutor` bean override.

**Решение (текущее, MVP):** живём с default - для one-instance dev
с админом-singleton нагрузка smэnая.

**Решение для prod:** добавить bean override:
```java
@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        configurer.setTaskExecutor(executor);
    }
}
```

Зафиксировано в Сессии 28 после code review этапа 25.b. Перенесено
в Этап 25.c operational hardening backlog.

---

<!-- Добавлять новые ловушки сюда по мере их обнаружения -->

## Chrome accelerators - Ctrl+K не отдать page-listener'у на Win/Linux

**Симптом:** Command palette с биндом `Ctrl+K` открывает omnibox-search
браузера вместо своего dialog. `e.preventDefault()` на keydown
не помогает. На Mac (Cmd+K) - всё работает.

**Причина:** Chrome на Win/Linux обрабатывает `Ctrl+K` как **native
browser accelerator** (search via default search engine, аналогично
`Ctrl+L` для address bar). Native accelerators обрабатываются раньше
любого page JS - не помогает ни bubble phase, ни capture phase,
ни `stopPropagation`. На Mac `Cmd+K` не имеет browser accelerator -
поэтому там тот же код работает.

**Решение:** Не использовать комбинации которые Chrome зарезервировал:
- `Ctrl+K` (search), `Ctrl+E` (search), `Ctrl+L`/`Alt+D` (address bar)
- `Ctrl+T` (new tab), `Ctrl+W` (close tab), `Ctrl+N` (new window)
- `Ctrl+Shift+T` (reopen tab), `Ctrl+Tab` (next tab)
- `Ctrl+R` / `F5` (reload) - можно intercept'ить но user-hostile
- `Ctrl+P` (print) - можно intercept'ить, конфликтует

**Безопасные:**
- `Alt+<letter>` - НЕ конфликтует кроме menubar (Alt+F/Alt+E/Alt+V/
  Alt+H/Alt+B/Alt+T - File/Edit/View/History/Bookmarks/Tools). Свободные:
  `Alt+K`, `Alt+P`, `Alt+G`, `Alt+J` и т.д.
- `Ctrl+/` - free
- `Ctrl+;` - free (на us-кладе)
- Single key вроде `/` (GitHub использует) - но конфликтует с inputs

**Linear/Vercel/Notion** используют `Cmd+K` на Mac + другой shortcut
на Win/Linux (часто без визуального hint).

Мы выбрали `Alt+K` универсально - на Mac `Option+K`, `e.altKey` ловит обе.

**Связано с:** Сессия 35 - CommandPalette изначально на `Cmd+K` с
capture phase + stopPropagation, не помогло. Финальное решение -
переезд на `Alt+K` (коммит `17353fa`).

---

## React StrictMode duplicate API requests в dev

**Симптом:** В DevTools Network tab каждый `useEffect`-fetch вызывается
дважды при `npm run dev`. Например `GET /api/v1/topics/{id}/graph`
прилетает 2 раза подряд. В production build (`npm run build` →
`npm run preview`) - всё нормально, один запрос.

**Причина:** `<React.StrictMode>` в `main.tsx` намеренно double-invoke'ит
`useEffect` в development чтобы ловить bugs от non-idempotent side
effects. Это not a bug - это feature React 18+. Production build
StrictMode noop'ит.

**Решение:** Не лечить. Реальные endpoint'ы идемпотентны (GET-only,
второй POST detect'ится duplicate-key). Если конкретный effect
дорогой и шумит logging - локально завернуть в `useRef` flag:

```ts
const calledOnce = useRef(false);
useEffect(() => {
  if (calledOnce.current) return;
  calledOnce.current = true;
  fetchExpensiveResource();
}, []);
```

Но это **не general fix** - только для truly-once effects вроде analytics
init. Большинство fetch'ей оставлять как есть.

**Связано с:** Сессия 32 - debug первичный thought был "race condition в
react-query", оказалось просто StrictMode. Не путать с реальными double-fire
багами.

---

## Closure-функции из хуков должны быть `useCallback`-стабильны

**Симптом:** Бесконечный fetch графа после добавления `t` (из useT)
в `useEffect` deps. DevTools показывает loading spinner который
никогда не останавливается, network tab спамит запросами.

```ts
const t = useT();
useEffect(() => {
  fetchGraph().catch(() => toast(t('error.fetch_failed')));
}, [topicId, t]);  // ← t меняется каждый рендер → infinite loop
```

**Причина:** `useT` возвращал новую функцию на каждом рендере (closure
над текущим `locale`). Любая stable function из ESLint exhaustive-deps
заставляет добавить её в deps, а нестабильная reference триггерит
повторный effect.

**Решение:** **Все** хуки возвращающие функции обязаны мемоизировать
их через `useCallback([dep])`:

```ts
export function useT() {
  const locale = useLocaleStore(s => s.locale);
  return useCallback(
    (key: DictKey, params?: Record<string,string>) => translate(locale, key, params),
    [locale]
  );
}
```

То же применимо к `useFormatDate` / `useNumberFormat` / любому custom
хуку. Правило: если функция попадает в external deps - она `useCallback`.

**Связано с:** Сессия 33 - infinite loop сжёг полчаса. Memory
`feedback_stable_hooks_for_deps` зафиксировала правило.

---

## Batch-Edit по cyrillic строкам - silent skip без verify-grep

**Симптом:** После 5+ замен через Edit tool по cyrillic-литералам
(например `'Сохранить'` → `t('common.save')`) часть строк остаётся в
коде хардкодом. Линтер их не ловит, тесты passing. Обнаруживается
только когда пользователь видит хардкод в UI.

**Причина:** Edit tool матчит `old_string` буквально, включая whitespace.
Если в файле reformat (Prettier) поменял indentation на табы или сжал
multi-line - match не срабатывает, Edit возвращает error. Но при
**batch-run** через несколько Edit tools в одном response можно
пропустить error notification и думать что всё прошло.

Особо вреднo с cyrillic потому что:
- VS Code grep по умолчанию case-insensitive, легко перепутать варианты
- Похожие фразы (`'Сохранить'` / `'Сохраняем'`) дают partial false-positive

**Решение:** После любой batch-замены по cyrillic в JSX **обязательно**
verify-grep:

```bash
grep -nE "label:.*'[А-ЯЁ]" frontend/src/apps/argument-map/
grep -nE ">[А-ЯЁ][^<]*<" frontend/src/apps/argument-map/
grep -rn "placeholder=\"[А-ЯЁ]" frontend/src/
```

Если результаты non-empty - не закрывать commit пока не разобрано.

**Связано с:** Сессия 33 - повторные находки хардкода в Сессии 34 code
review (5+ leftover). Memory `feedback_grep_after_batch_edits` зафиксировала
правило.

---

## Tailwind v4 `@theme inline` обязателен для runtime темизации

**Симптом:** Создаёшь dark theme через `[data-theme="dark"]` override
CSS variables. `themeStore` правильно ставит атрибут на `<html>`. Но
визуально страница не меняется. DevTools Computed показывает что
переменные обновились, но `bg-ink-900` рендерится **со старым цветом**.

**Причина:** Tailwind v4 по умолчанию **inline-разворачивает** `var()`
из `@theme` блока **на этапе сборки**. То есть `bg-ink-900` компилируется
в `background-color: oklch(0.15 ...)` (resolved value), а не
`background-color: var(--color-ink-900)`. Runtime override CSS variables
не работает потому что в финальном CSS их и нет - они уже подставлены.

```css
/* ❌ не работает - var() inline-резолвится */
@theme {
  --color-ink-900: var(--c-ink-900);
}

/* ✅ работает - var() остаётся в финальном CSS */
@theme inline {
  --color-ink-900: var(--c-ink-900);
}
```

**Решение:** Всегда использовать `@theme inline` если значения - это
`var()` ссылки на runtime-managed CSS variables. Обычные literal values
(`#fff`, `1rem`) можно в обычный `@theme` - они и так static.

**Связано с:** Сессия 34 v2 migration. Без `inline` тёмная тема не
работала весь первый день debugging. ADR-031 v2 design system.

---

## Mass-replace через sed после большой миграции требует grep audit

**Симптом:** После 4 волн sed-replace (slate→ink, indigo→accent,
border-slate-200→border-border, etc) build зелёный, tests passing, UI
выглядит правильно. Через 2 дня замечаешь хардкод `text-[14px]` /
`bg-emerald-100` / `rounded-xl` в коде - sed не подхватил.

**Причина:** sed regex'ы ловят только conformant patterns. Real-world
варианты которые sed пропускает:

- `text-[14px]` (arbitrary value) - не покрыт scale class regex'ом
- `bg-emerald-100` - emerald в проекте reserved для статуса ok, но
  попал в legacy palette файл
- `rounded-xl` (Tailwind 12px corner) - семантически не соответствует
  rounded-md токену
- `#c4b5fd` hex literal внутри inline `style={...}` - вне Tailwind scope

**Решение:** После любой большой sed-миграции - audit grep на остатки:

```bash
# Палитра-leftover (slate / indigo / emerald / rose / amber)
grep -rnE "(text|bg|border|ring|divide|from|to|via)-(slate|indigo|emerald|rose|amber|gray)-" frontend/src/ \
  | grep -v "design-reference"

# Arbitrary text sizes (нарушение typography scale)
grep -rnE "text-\[[0-9]+(px|rem)" frontend/src/ | grep -v "design-reference"

# Hex literals в JSX
grep -rnE "(?:background|color|stroke|fill)[:=][^'\"]*['\"]#[0-9a-fA-F]" frontend/src/

# Rounded scale violations
grep -rnE "rounded-(xl|2xl|3xl|full)" frontend/src/ | grep -v "design-reference"
```

Любой match - не закрывать миграцию пока не triage'ит.

**Связано с:** Сессия 34 code review нашёл 191 occurrence `text-[Xpx]`,
7+ файлов с sed-leftover палитры. Memory `feedback_grep_after_batch_edits`.

---

## Node 24 + undici 7 - AbortSignal instanceof check ломает fetch в тестах

**Симптом:** 12 frontend тестов в TopicListPage / TopicGraphPage /
NodeDetailsPanel начали фейлить между Сессией 35 (143/143 pass) и
Сессией 36 (131/143). Все async UI тесты с `apiGet(path, { signal:
controller.signal })` фейлят на ожидании success-контента - вместо него
рендерится error state с текстом `«Ошибка: RequestInit: Expected signal
('AbortSignal {}') to be an instance of AbortSignal»`.

**Причина:** Node 24 включает bundled undici 7 как
`node:internal/deps/undici/undici`. Этот undici валидирует `signal
instanceof AbortSignal` через **свой** internal prototype, недоступный
из user-space. Любой `AbortSignal` созданный через user code (jsdom-овский
ИЛИ native `globalThis.AbortController`) проваливает instanceof check
внутри `Request` constructor. fetch падает с `TypeError` ещё до первого
network call.

Регрессия появилась от **обновления окружения** (Node 22/23 → 24), не от
кода. Между Сессией 35 и 36 единственный commit `9ab061c chore: connect
RuFlo` не трогал frontend.

References:
- github.com/nodejs/undici/issues/2596
- github.com/nodejs/node/issues/56644

**ОТВЕРГНУТАЯ гипотеза:** В Сессии 36 первая диагностика винила React 19
+ act() warning. Это было side-effect - act() warning появлялся как
follow-up рендера error state. Реальная причина - fetch падал ДО любого
state update.

**Решение:** monkey-patch `globalThis.fetch` в `frontend/src/test-setup.ts`
beforeAll() **после** `server.listen()` (msw v2 устанавливает свой
fetchProxy в server.listen, наша обёртка должна идти ПОСЛЕ):

```typescript
function wrapFetchStripSignal(): void {
  const current = globalThis.fetch;
  globalThis.fetch = function wrappedFetch(input, init) {
    if (init?.signal) {
      const { signal: _signal, ...rest } = init;
      return current.call(this, input, rest);
    }
    return current.call(this, input, init);
  } as typeof fetch;
}

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  wrapFetchStripSignal();
});
```

В тестах cancellation/abort логика не нужна (MSW handlers синхронные),
в prod fetch работает напрямую без обёртки.

**Альтернативы (отвергнуто):** установить undici dep (меняет dep tree),
заменить jsdom на happy-dom (rebreaks RTL utilities), pin Node 22 (downgrade
не вариант), удалить `{ signal }` из apiGet (теряем prod cleanup-логику).

**Применено:** Сессия 36 через test-regression-diagnoser ruflo subagent.
142/143 проходит (12 регрессий восстановлены, 1 unrelated pre-existing
fail в AddSourceModal.test.tsx).

**Риски:** если когда-то понадобится тестировать cancellation/abort
логику - наша обёртка её сломает. Либо переключиться на conditional
strip (только в test env через VITE_TEST flag), либо обновить Node
когда undici issue зафиксят.

---

## shamela API из WSL2 требует VPN/прокси - 502 на sync-master

**Симптом:** `POST /api/v1/admin/shamela/sync-master` возвращает
502 Bad Gateway с Problem Details `type=shamela-api-error`, `detail`
содержит замаскированный `api_key=***`. Реальный upstream запрос
к `https://dev.shamela.ws/api/v1/patches/master?api_key=***&version=N`
получает ConnectTimeout / 503 / 407 (зависит от ситуации).

**Причина:** `dev.shamela.ws` доступен только через определённые
network egress points. WSL2 с corp прокси (407) либо без VPN не
может достучаться. Это **не наш баг** - circuit breaker правильно
бросает `ShamelaApiException`, `GlobalExceptionHandler` мапит в 502.

**Решение:**
- (a) Включить VPN к shamela-разрешённому network egress
- (b) Если admin sync не нужен прямо сейчас - продолжать локальную
  работу, frontend теперь показывает локализованный toast
  «внешний сервис shamela.ws недоступен. возможно требуется VPN
  или сервис временно лежит. попробуйте позже» вместо сырого
  Problem Details (#5 user feedback Сессии 38, Сессия 39 fix)
- (c) Использовать локальный staging dump shamela (если есть) для
  dev-режима - не требует выходного интернета

Альтернатива была - DOWN flag в `/actuator/health` после N
неудачных попыток подряд. Отложено как over-engineering для
admin-only функциональности

Зафиксировано Сессией 39 после ручного тестирования пользователем.

**Update Сессия 43:** найдена ещё одна причина 407 - **JVM-property
`-Dhttps.proxyHost` из IntelliJ Run Config**. IntelliJ может
автоматически прокидывать Windows system proxy через JVM properties
когда backend стартует из IDE. Это перебивает env vars (HTTPS_PROXY)
потому что `ProxySelector.getDefault()` читает JVM properties first.

Fix в `ShamelaHttpClientConfig` Сессии 43 - **по умолчанию форсим
прямое соединение** через `.proxy(ProxySelector.of(null))` когда
HTTPS_PROXY env var не задан. Это игнорирует system proxy и JVM
properties полностью. Если же `HTTPS_PROXY` задан - используется
с credentials как раньше.

Также `ShamelaApiClient` теперь явно говорит про corp proxy при
407 - сообщение «407 Proxy Authentication Required - corporate
proxy блокирует. Очистите JVM-property -Dhttps.proxyHost из Run
Config либо задайте HTTPS_PROXY env var с credentials» вместо
generic «HTTP 407».

**Если 407 всё ещё приходит после fix:**
1. IntelliJ → Run Config → VM options - убрать любые `-Dhttp*proxy*`
2. Settings → Appearance & Behavior → System Settings → HTTP Proxy
   → No proxy либо disable «Auto-detect proxy settings»
3. В .env / shell - `unset HTTPS_PROXY HTTP_PROXY` перед `./mvnw spring-boot:run`
4. Verify в логах startup: «shamela HTTP-клиент: прямое соединение
   (HTTPS_PROXY не задан, system proxy bypassed)»

---

## Google Fonts в WSL2 за corp proxy не загружаются (407)

**Симптом:** все web-fonts из `https://fonts.googleapis.com/...`
не загружаются - DevTools показывает 407 Proxy Authentication Required.
В UI текст рендерится system-default serif/sans (Liberation Serif
на Linux, Times New Roman на Mac). Visual diff между dev и production:
типографика «другая», иногда сильно (italic / hinting / weight
fallback).

**Причина:** corp proxy требует Basic auth для HTTPS-CONNECT
запросов к Google Fonts CDN. Браузер не передаёт креды для font
загрузки.

**Решение:**
- (a) Для dev в WSL2 - принять fallback. Все компоненты используют
  font-stacks с system serif/sans в конце - читаемо
- (b) Для accurate visual review - проверить в реальном браузере
  на хост-машине Windows (где proxy либо не активен либо нативно
  проксирует), не через WSL2 dev preview
- (c) Если нужен dev preview с правильными шрифтами - подключить
  `@font-face` локально через файлы в `public/fonts/` минуя CDN

Frontend tests не зависят от этого - vitest+jsdom не рендерит
шрифты вообще, computed font-family возвращает CSS variable
строку как есть.

Зафиксировано Сессией 39 при playwright диагностике #6 шрифта
title книг (пользователь жаловался на «выврвиглазный шрифт»).

---

## `event.key` vs `event.code` в keyboard handlers - layout independence

**Симптом:** hotkey'и срабатывают на en-раскладке но не на ru/ar.
Например `Alt+K` открывает Command Palette в EN, но в RU тот же
физический keypress ничего не делает (хотя пользователь видит
«K» на клавиатуре).

**Причина:** `e.key` возвращает символ который **производит** клавиша
в текущей раскладке: `'k'` на en, `'л'` на ru (на стандартной ЙЦУКЕН),
`'ل'` на ar. Проверка `e.key.toLowerCase() === 'k'` пройдёт только
на en. `e.code` возвращает **физическую** клавишу (`'KeyK'` всегда)
и от раскладки не зависит.

**Reproducer:**
```ts
window.addEventListener('keydown', (e) => {
  console.log(`key=${e.key} code=${e.code}`);
});
// EN раскладка, нажать K: key=k code=KeyK
// RU раскладка, та же клавиша: key=л code=KeyK
// AR раскладка, та же клавиша: key=ل code=KeyK
```

**Решение:** для буквенных hotkey'ев использовать `event.code`. Во
фронте все hotkey'и зарегистрированы через `useHotkey`
(`@/shared/hooks/useHotkey`, см. ADR-036) который ставит `useKey: true`
в default options - react-hotkeys-hook матчит по `event.code` для
буквенных автоматически. Модификаторы (`alt`/`ctrl`/`shift`/`meta`)
от раскладки не зависят и без useKey.

**Когда писать `addEventListener('keydown')` вручную (legacy code,
третьесторонняя интеграция):**

```ts
window.addEventListener('keydown', (e) => {
  if (e.altKey && e.code === 'KeyK') { ... }  // ✓ layout-independent
  // НЕ: if (e.altKey && e.key.toLowerCase() === 'k')  // ✗ зависит от layout
});
```

Зафиксировано Сессией 39 при унификации hotkey'ев (#2 фидбэк -
Alt+K не работал на ru-раскладке у пользователя).


## react-hotkeys-hook + native HTML `<dialog>` - `preventDefault` блокирует cancel event

**Симптом:** `useHotkey('escape', onClose)` в компоненте который **внутри**
native `<dialog>` (или другого браузерного modal) не закрывает диалог
через ESC. Native `<dialog>` имеет встроенный cancel event который должен
срабатывать на ESC, но preventDefault от react-hotkeys-hook его блокирует.

**Причина:** `react-hotkeys-hook` по умолчанию вызывает
`event.preventDefault()` после handler'а. Если в DOM tree есть native
`<dialog>`, его cancel event не успевает fire - prevented. То же
применимо к другим браузерным top-layer modal'ам (попап позже).

**Решение:** для ESC в графе и модалках использовать `{ preventDefault:
false }` опцию, а preventDefault звать вручную только когда реально
обработали event:

```typescript
useHotkey(
  'escape',
  (e) => {
    if (document.querySelector('dialog[open]')) return; // dialog сам закроется
    if (hasSelection) {
      e.preventDefault();
      onClearSelection();
    }
  },
  { enableOnFormTags: true, preventDefault: false },
  [hasSelection, onClearSelection],
);
```

**Reproducer:** см. `frontend/src/apps/argument-map/hooks/useGraphEscape.ts:55-58`
и комментарий внутри. Inline комментарий там подробно фиксирует ту же
самую логику; gotcha здесь - чтобы её можно было найти при поиске по
docs/, а не только при чтении кода.

**Узнано:** Сессия 38, при миграции с inline `onKeyDown` на единую
`useHotkey` систему (ADR-036). Inline handlers до этого не вызывали
preventDefault явно, поэтому проблема была невидима - regression
проявилась только после унификации.

---

## Scrollbar shift при навигации между route'ами

**Симптом:** при переключении между `/topics` (много карточек) и `/qa`
(2 элемента) контент **визуально прыгает влево-вправо на ~7px**. Глаз
ловит это как «дёрганый» переход между страницами.

**Причина:** браузерное поведение по умолчанию. Когда страница длинная и
требует вертикальной прокрутки - появляется vertical scrollbar и width
body уменьшается на ширину scrollbar (~15px на Chrome/WSL). Короткая
страница без скролла - body на 15px шире. Контейнеры центрируются через
`mx-auto`, поэтому при разной body-width центр контента сдвигается на
половину дельты ≈ 7-8px.

**Решение:** `scrollbar-gutter: stable` на `html` в `index.css` (@layer
base). Резервирует место для scrollbar даже когда его нет - body всегда
одинаковой ширины. Modern CSS spec, поддерживается Chrome 94+, Firefox
97+, Safari 18+. Применено в Сессии 40.


---

## @fontsource Cyrillic subset trap

**Симптом:** меняешь `--font-book-title` или другую font CSS-variable,
запускаешь dev-сервер - в браузере **ничего не меняется** для русских
названий книг (для латинских `Smoke test 16.h` шрифт переключается
нормально). Несколько сессий пытались править weight 500→600→500 -
картинка не реагирует, пользователь раздражается

**Причина:** не все семейства в `@fontsource` / `@fontsource-variable`
поставляются с **Cyrillic subset**. Конкретно отсутствуют:
- EB Garamond
- Fraunces
- Space Grotesk
- DM Sans
- Crimson Pro
- Cormorant Garamond / Cormorant SC (есть только Cormorant general purpose)

Когда CSS-правило ставит `font-family: 'EB Garamond'`, браузер парсит
все `@font-face` декларации этого family, проверяет `unicode-range`
каждой. Для кириллических символов (U+0400-04FF) match не находится →
браузер падает на следующий шрифт в font-stack (`Source Serif 4`,
`Georgia`, ...). Меняешь `--font-book-title` - но fallback тот же,
визуально ничего не меняется

**Reproducer:**
```bash
grep -l "cyrillic" node_modules/@fontsource-variable/{eb-garamond,fraunces,space-grotesk}/*.css
# пусто - этих subsets нет

grep -l "cyrillic" node_modules/@fontsource-variable/{source-serif-4,manrope,inter,lora}/*.css
# найдёт - эти семейства покрывают cyrillic
```

**Решение:** **перед добавлением нового font** проверять subset coverage:
```bash
grep -l "cyrillic" node_modules/@fontsource*/{package}/*.css
```
Если cyrillic нет - либо использовать только для latin контента, либо
не использовать вообще. **Для книжных названий обязателен cyrillic
subset** - проект bilingual ru/ar.

Семейства с полным cyrillic (на Сессию 38): Manrope, Source Serif 4,
Inter, Lora, Bitter, Playfair Display, IBM Plex Sans, Literata,
JetBrains Mono

**Узнано:** Сессия 38 при работе над Font Tweaker. До этого несколько
сессий пытались поменять `--font-book-title: EB Garamond` и удивлялись
что ничего не меняется - был fallback на Source Serif 4 всё время.

---

## book.language ≠ направление шрифта контента

**Симптом:** в `Card.Title` для книги «Священный Коран» (поле
`book.language = 'ar'` в БД, но title по-русски) шрифт рендерится
**Amiri'ом** (font-arabic), хотя текст кириллический. Amiri не имеет
глифов для кириллицы → fallback на browser-default serif → визуально
выглядит как «уродский жирный»

**Причина:** в коде использовался naive check `isArabic = book.language
=== 'ar'` для выбора между `font-arabic` и `font-serif`. Но **язык
оригинала книги ≠ язык отображаемого названия**:
- Книга оригинально на арабском (`language='ar'`)
- Russian-локализованное название «Священный Коран» хранится в `title`
- Контент title - кириллица, не arabic script

Amiri покрывает только Arabic Unicode ranges (U+0600-06FF). Для
кириллицы у него нет глифов - браузер показывает «недоступные» через
font-stack fallback

**Реactor:** `Card.Title` auto-detect шрифт по **содержимому** через
`hasArabicScript(children)` из `@/shared/i18n`:
```tsx
const isArabic =
  arabic ?? (typeof children === 'string' && hasArabicScript(children));
```
Если контент содержит арабские глифы (regex `/[؀-ۿ...]/`) → `font-arabic`.
Иначе → `font-serif` (с латиницей+кириллицей)

**Reproducer:** см. `frontend/src/shared/components/ui/Card.tsx:94-110` и
`frontend/src/shared/i18n/script.ts`

**Правило проекта** (зафиксировано в CLAUDE.md):
> Для контента из API - `dir="auto"`, шрифт через `hasArabicScript` из
> `@/shared/i18n`. Inline regex `/[؀-ۿ]/` в компонентах запрещён -
> использовать единый модуль

**Узнано:** Сессия 38, при анализе FOUT через playwright computed styles
показали что **все** Card.Title рендерятся через Amiri (включая
«Священный Коран», «Smoke test 16.h»). Корень - `book.language === 'ar'`
у всех тестовых книг

## JWT HS256 - secret минимум 32 байта (256 бит)
**Симптом:** при старте Spring Boot падает с
`io.jsonwebtoken.security.WeakKeyException` либо наш
`IllegalStateException("auth.jwt.secret должен быть минимум 256 бит")`

**Причина:** jjwt 0.12.x жёстко валидирует длину ключа для HS256 -
строго 32 байта (256 бит) и больше. Если просто скопировать `secret: foo`
в `application.yml` - не запустится. RFC 8017 + JWA RFC 7518: HMAC-SHA256
ключ должен быть не меньше output длины (32 байта)

**Решение:** в `application.yml` дефолтный secret сделан заведомо
длиннее 32 ASCII-символов. В prod **обязательно** через env
`AUTH_JWT_SECRET` минимум 32 байта (генерируется через
`openssl rand -hex 32` - 64 hex символа = 32 байта). Документировано
в `backend/CLAUDE.md` и в ADR-040. `JwtService` конструктор бросает
explicit IllegalStateException с понятным сообщением

## Spring Security 6 - порядок requestMatchers важен
**Симптом:** добавил `.requestMatchers("/api/**").permitAll()` для
дев-режима, а `/api/v1/auth/me` тоже permitAll'нулся (хотя должен
быть authenticated) - тесты `GET /auth/me without auth → 401` падают
с `200 OK`

**Причина:** в Spring Security 6 `authorizeHttpRequests` matcher'ы
применяются в порядке объявления, **первый матч выигрывает**.
Если `/api/**` permitAll стоит перед `.anyRequest().authenticated()`,
он покрывает и /auth/me

**Решение:** более специфичные правила объявлять РАНЬШЕ общих.
Конкретно: `auth.requestMatchers("/api/v1/auth/me").authenticated()`
ДО `auth.requestMatchers("/api/**").permitAll()`. См. SecurityConfig
+ AuthControllerIT.GET_me_withoutAuth_returns401

## Параллельная сессия на Page record - не свой код, не трогать
**Симптом:** `./mvnw test-compile` падает на PageRepositoryIT /
QuestionCitationServiceIT с `constructor Page cannot be applied to
given types` - стало 12 параметров вместо 11

**Причина:** parallel subagent (Tiptap, Этап 17.0) добавил
`formattedContent` поле в `library.domain.Page` record. Существующие
тесты ещё не обновлены, потому что он в процессе работы. Не моя зона
ответственности - не трогать его код

**Решение:** игнорировать ошибки `library.repository.PageRepositoryIT`
и `qa.service.QuestionCitationServiceIT` в этой сессии. Подождать пока
parallel subagent закроет свою задачу - он сам обновит конструкторы

## SameSite=Strict refresh cookie + cross-origin fetch - не работает в dev

**Симптом:** Этап 21.b login flow в браузере падает: фронт делает POST
`/api/v1/auth/login` на :9090, получает 200 + Set-Cookie, но
последующий `/api/v1/auth/refresh` валится с CORS error
«'Access-Control-Allow-Credentials' header in the response is ''
which must be 'true' when the request's credentials mode is 'include'»

**Причина:** два момента:
1. backend Этап 21.a `WebMvcConfig.addCorsMappings` имеет
   `.allowCredentials(false)` - блокирует любой `credentials: 'include'`
   fetch cross-origin
2. SameSite=Strict refresh cookie вообще не отправляется браузером
   cross-origin (:5173 → :9090) даже если CORS бы пропустил

**Решение в рамках frontend (без правки backend):** Vite proxy +
relative API_BASE_URL:
- `vite.config.ts` - `server.proxy: { '/api': 'http://localhost:9090' }`
- `client.ts` - `API_BASE_URL = ''` в browser, абсолютный URL только
  в test mode (msw слушает `http://test.local`)

Через proxy фронт и API живут на одном origin (5173) - cookies
работают, CORS не нужен. В prod aналогичный setup через nginx/
Cloudflare reverse proxy

См. ADR-040 + `frontend/vite.config.ts` + `frontend/src/shared/api/
client.ts` + frontend/CLAUDE.md «Auth» секция

## React 19 StrictMode double-effect для loadCurrentUser

**Симптом:** на mount App.tsx делает 2 запроса `POST /auth/refresh`
вместо одного. В dev consolelog `[MSW] intercepted...` дублируется

**Причина:** React 19 StrictMode в dev умышленно дважды вызывает
useEffect ради ловли побочных эффектов

**Решение:** хранить `initialized` флаг в authStore. useEffect
вызывает `loadCurrentUser()` только если `!initialized`. После
первого call (success или fail) флаг ставится в true, второй render
видит initialized=true и пропускает. Двух запросов нет

См. `frontend/src/shared/stores/authStore.ts` + `App.tsx`
