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

<!-- Добавлять новые ловушки сюда по мере их обнаружения -->
