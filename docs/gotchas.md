# Ловушки проекта (Gotchas)

Специфичные для проекта подводные камни, которые легко забыть и наступить
на грабли второй раз. Добавлять сюда всё, что однажды потратило время
на дебаг и может повториться.

Формат:
```
## Короткое название
**Симптом:** что видно
**Причина:** что на самом деле происходит
**Решение:** как исправить или обойти
```

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

<!-- Добавлять новые ловушки сюда по мере их обнаружения -->
