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

<!-- Добавлять новые ловушки сюда по мере их обнаружения -->
