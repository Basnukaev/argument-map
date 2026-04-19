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

<!-- Добавлять новые ловушки сюда по мере их обнаружения -->
