---
name: shamela-parser-debug
description: >
  Use when debugging Shamela ETL issues, diagnosing import failures, investigating
  ShamelaApiClient errors, staging DAO anomalies, mapper edge cases, or re-running
  failed imports safely. Triggers on: shamela, ETL, import book, ShamelaApiClient,
  ShamelaMasterReader, ShamelaBookReader, ShamelaBookImportService, lib_books error,
  book import, archive extract, sqlite reader, seed-mawlid, AdminShamelaPage,
  staging tables, shamela sync, ShamelaMasterSyncService, ShamelaToLibraryMapper,
  ShamelaTextCleaner, ShamelaBibliographyParser, ShamelaAuthorityResolver.
  Path scope: backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/,
  frontend/src/apps/admin/pages/AdminShamelaPage.tsx.
  Always use this skill before touching ETL code or triggering any import — the
  pipeline has non-obvious failure modes and a strict bulk-import policy that must
  not be bypassed.
---

# Shamela ETL — Diagnostic Playbook

Shamela.ws — публичный архив исламской литературы (~6500 книг). Этот skill
описывает 6-шаговый ETL pipeline, диагностическое дерево симптомов, команды
для диагностики каждого слоя, и процедуру безопасного повторного запуска.

---

## 1. Overview — 6-шаговый ETL pipeline

```
Fetch → Extract → Parse → Map → Persist → Cleanup
```

| Шаг | Что происходит | Ключевые классы |
|---|---|---|
| **1. Fetch** | Скачать архив (tar.gz) с зеркал shamela.ws | `ShamelaApiClient`, `ShamelaWorkDirManager` |
| **2. Extract** | Распаковать архив во временную директорию | `ShamelaArchiveExtractor` |
| **3. Parse** | Читать SQLite → staging DTO (master metadata + per-book content) | `ShamelaMasterReader`, `ShamelaBookReader`, `SqliteValueParser` |
| **4. Map** | Конвертировать staging DTO в domain objects | `ShamelaToLibraryMapper`, `ShamelaTextCleaner`, `ShamelaBibliographyParser`, `ShamelaAuthorityResolver` |
| **5. Persist** | INSERT в `lib_books` / `lib_chapters` / `lib_pages` (transactional) | `ShamelaBookImportService`, `ShamelaMasterSyncService`, DAOs в `repository/` |
| **6. Cleanup** | Удалить temp файлы | `ShamelaWorkDirManager` |

Каждый шаг может падать независимо. Важно знать на каком шаге сломалось.

---

## 2. Diagnostic decision tree

```
Что не работает?
├─ Book доступен в shamela.ws через browser, но fetch fails?
│  → Network / rate limit / DNS. См. «Fetch troubleshooting» (раздел 3)
├─ Archive downloaded, но extract fails?
│  → Corrupted archive / disk space / tar mismatch. См. «Extract» (раздел 4)
├─ Extract OK, но parse fails (NoSuchElementException / SQLite errors)?
│  → Schema drift в shamela / unsupported book format. См. «Parse» (раздел 5)
├─ Parse OK, но mapper produces invalid / empty domain objects?
│  → ShamelaTextCleaner / BibliographyParser / AuthorityResolver edge case.
│     См. «Map» (раздел 6)
├─ Mapping OK, но persist fails (DB constraint violation / DuplicateKeyException)?
│  → Duplicate import / CHECK constraint / FK violation. См. «Persist» (раздел 7)
└─ Persist OK, но result в UI отображается криво?
   → Frontend rendering issue, не ETL (см. library-page-rendering skill)
```

**Первый шаг всегда:** проверить `/tmp/backend.log`

```bash
tail -100 /tmp/backend.log | grep -i "error\|exception\|shamela\|ETL"
```

---

## 3. Fetch troubleshooting

**Источник:** `backend/.../shamela/api/ShamelaApiClient.java` + `ShamelaApiProperties.java`

### Connection timeout

```bash
# Проверить доступность shamela.ws
curl -sv --max-time 10 https://shamela.ws/ 2>&1 | head -20

# Проверить разрешение DNS в WSL2
nslookup shamela.ws
```

Если timeout — shamela.ws недоступен. Возможные причины: WSL2 DNS проблемы.

**Починить DNS в WSL2:**
```bash
cat /etc/resolv.conf
# Если видим только 127.0.0.x — сгенерировать заново:
# sudo rm /etc/resolv.conf
# sudo bash -c 'echo "nameserver 8.8.8.8" > /etc/resolv.conf'
# Перезапустить WSL через Windows Terminal: wsl --shutdown
```

### 403 / 429 — rate limited

- Shamela ограничивает частые автоматические запросы
- Wait 5-10 минут и retry
- **НЕ запускать массовые параллельные fetch** — см. раздел 9 (Bulk import policy)
- Опционально: использовать VPN если IP заблокирован временно

### 404 — книга не найдена

Проверить `source_id` в запросе — shamela периодически переиндексирует книги.
Искать книгу вручную на shamela.ws, найти актуальный ID.

### Конфигурация

```yaml
# backend/src/main/resources/application.yml
shamela:
  base-url: https://shamela.ws
  # Остальные properties → ShamelaApiProperties
```

---

## 4. Extract troubleshooting

**Источник:** `backend/.../shamela/etl/ShamelaArchiveExtractor.java`

### Out of disk space

```bash
df -h /tmp
# Если < 500MB — освободить место или сменить temp dir
```

### Corrupted archive

Симптом: `ShamelaArchiveException` при распаковке.

```bash
# Проверить размер скачанного файла
ls -lh /tmp/shamela-*

# Тест что файл открывается как tar.gz
file /tmp/shamela-*.tar.gz
tar -tzf /tmp/shamela-*.tar.gz 2>&1 | head -5
# Если ошибка → файл повреждён → удалить и скачать заново
```

### SQLite version mismatch

Shamela использует стандартный SQLite формат — mismatch крайне маловероятен.
Если вдруг: `sqlite3 /tmp/shamela-extract/<id>.sqlite ".tables"` вернёт error →
проверить что `sqlite-jdbc` в `backend/pom.xml` актуален.

---

## 5. Parse troubleshooting

**Источники:**
- `backend/.../shamela/etl/ShamelaMasterReader.java` — читает master.db (метаданные)
- `backend/.../shamela/etl/ShamelaBookReader.java` — читает per-book.db
- `backend/.../shamela/etl/SqliteValueParser.java` — null-safe парсинг TEXT колонок

### Проверить содержимое SQLite напрямую

```bash
# Список таблиц в master
sqlite3 /tmp/shamela-extract/master.db ".tables"

# Список таблиц в конкретной книге
sqlite3 /tmp/shamela-extract/<bookId>.db ".tables"

# Проверить кодировку + содержимое контента
sqlite3 /tmp/shamela-extract/<bookId>.db \
  "SELECT typeof(content), substr(content, 1, 50) FROM books LIMIT 1"

# Схема таблицы
sqlite3 /tmp/shamela-extract/master.db ".schema b"
```

### Shamela schema drift

Shamela периодически обновляет схему своих SQLite файлов.
Симптом: `NoSuchElementException` или `SQLException: no such column` в логах.

**Диагностика:**
1. Найти exception в `/tmp/backend.log`:
   ```
   grep "no such column\|ResultSet\|SQLite" /tmp/backend.log | tail -10
   ```
2. Сравнить `.schema` таблицы с ожидаемым в Reader классе
3. Если новая колонка появилась — Reader'у не нужно её читать, можно игнорировать
4. Если колонка переименована — обновить имя в `ShamelaMasterReader` / `ShamelaBookReader`

### Особенности `SqliteValueParser`

Shamela хранит большинство числовых и boolean полей как TEXT:

| Паттерн | Метод | Поведение |
|---|---|---|
| `"99999"` в поле year | `parseYearOrNull` | → `null` (sentinel для «год неизвестен») |
| `"0"` / `"1"` boolean флаги | `parseBoolOrNull` | `"1"` → true, `"0"` → false, иначе null |
| Tombstone `is_deleted` | `isDeletedFlag` | только `"1"` → deleted, всё остальное → актуальна |
| Пустая строка `""` | все методы | → null (отсутствие значения) |

**Год с арабским маркером хиджры** («1340 هـ»): `parseYearOrNull` вернёт null
(не числовой формат). Это ожидаемое поведение — такие значения не парсируются.
Если нужен парсинг — обновить `parseYearOrNull` с regex, зафиксировать в backlog.

---

## 6. Map troubleshooting

**Источники в `service/mapper/`:**
- `ShamelaTextCleaner.java` — strip HTML tags, нормализация пробелов
- `ShamelaBibliographyParser.java` — извлечение author/publisher/year из freeform metadata
- `ShamelaAuthorityResolver.java` — матчинг shamela authority strings к `authorities` таблице
- `ShamelaBookMetadataBuilder.java` — сборка доменного объекта `Book`
- `ShamelaChapterMapper.java` / `ShamelaPageMapper.java` — маппинг chapters + pages

### ShamelaTextCleaner — пустой text_content

Симптом: `mapped_book.text_content = ""` или `null` при непустом raw content.

```java
// Проверить в тесте:
// ShamelaTextCleanerTest — unit тесты для edge cases
// mvn -pl backend test -Dtest=ShamelaTextCleanerTest
```

Типичные причины:
- HTML состоит только из тегов без текстового контента (div без текста)
- Контент — только пробелы после stripping → normalizer сворачивает в ""
- Encoding issue: символы потеряны до TextCleaner

### ShamelaBibliographyParser — missing author

Симптом: `Book.authorName = null` несмотря на наличие текста в metadata.

```java
// ShamelaBibliographyParserTest — юнит-тесты для Arabic author parsing
// mvn -pl backend test -Dtest=ShamelaBibliographyParserTest
```

Edge cases:
- Множественные авторы через «و» (واو العطف) — parser берёт первого
- Имя в нестандартном формате («مؤلفه:» вместо «المؤلف:»)
- Автор указан только в поле `extra_info` а не в основном поле

### ShamelaAuthorityResolver — дублирующийся authority

Симптом: `INSERT` в `authorities` бросает `DuplicateKeyException` на `(name, type)`.

`ShamelaAuthorityResolver` создаёт `AuthorityType.AUTHOR` (не SCHOLAR) для
shamela-авторов — это корректно (см. `backend/docs/hadith-grades.md`).

Причины дублирования:
1. Имя автора встречается в двух чуть разных написаниях (нормализация не помогла)
2. Authority уже существует от предыдущего импорта с другим написанием

**Диагностика:**
```sql
-- Найти похожие authority по имени
SELECT id, name, type FROM authorities
WHERE name ILIKE '%<часть имени>%' AND type = 'AUTHOR'
ORDER BY name;
```

Если дублирует existing — resolver должен матчить по нормализованному имени.
Проверить логику нормализации в `ShamelaAuthorityResolver.normalizeAuthorName`.

---

## 7. Persist troubleshooting

**Источники:** `ShamelaBookImportService`, `ShamelaMasterSyncService`, DAOs в `repository/`

### DuplicateKeyException — повторный импорт

`lib_books` имеет `UNIQUE (source, source_id)`. Если книга уже импортирована,
повторный `INSERT` вылетит с `DuplicateKeyException`.

**Решение:** сначала очистить (см. раздел 8 «Re-run safely»).

**Диагностика:**
```sql
-- Проверить существует ли книга
SELECT id, title, source, source_id, created_at
FROM lib_books
WHERE source = 'shamela' AND source_id = '<id>';
```

### CHECK constraint violation — `lib_pages`

`lib_pages` требует хотя бы одно из двух: `text_content IS NOT NULL OR file_path IS NOT NULL`.

Симптом: `ERROR: new row for relation "lib_pages" violates check constraint`.

Причина: mapper вернул `Page` с `text_content = null` И `file_path = null`.

**Диагностика:**
1. Найти книгу с problematic pages:
   ```sql
   SELECT COUNT(*) FROM lib_pages WHERE book_id = '<id>'
   AND text_content IS NULL AND file_path IS NULL;
   ```
2. Проверить `ShamelaPageMapper` — откуда берётся `text_content`. Если
   `ShamelaTextCleaner` всё вычистил и `file_path` не установлен → страница
   не прошла валидацию.

### FK violation — lib_chapters → lib_books

`lib_chapters.book_id` FK ссылается на `lib_books`. Если book INSERT провалился
(или не был committed) до chapters INSERT → FK violation.

Обычно означает что транзакция частично выполнилась. Проверить:
```sql
SELECT id FROM lib_books WHERE source = 'shamela' AND source_id = '<id>';
-- Если пусто — book не персистирован, chapters не смогут
```

### Transactional rollback

`ShamelaBookImportService.importBook` аннотирован `@Transactional`. При любой ошибке
в фазе persist — **все изменения откатываются**. Это безопасно — частичного импорта
не будет. Можно re-trigger без предварительной очистки **только если** предыдущая
попытка полностью откатилась (проверить через SELECT выше).

---

## 8. Re-run safely (idempotent)

### Шаг 1: очистить предыдущую попытку

```sql
-- CASCADE удалит lib_chapters и lib_pages автоматически
DELETE FROM lib_books
WHERE source = 'shamela' AND source_id = '<shamela_book_id>';

-- Верифицировать очистку
SELECT count(*) FROM lib_pages
WHERE book_id IN (
  SELECT id FROM lib_books WHERE source = 'shamela' AND source_id = '<shamela_book_id>'
);
-- Должно быть 0 (если DELETE прошёл — будет 0)
```

### Шаг 2: верифицировать что book_id удалён

```sql
SELECT COUNT(*) FROM lib_books
WHERE source = 'shamela' AND source_id = '<shamela_book_id>';
-- Ожидаем: 0
```

### Шаг 3: re-trigger

**Вариант A — через UI:**
Открыть `AdminShamelaPage` (`http://localhost:5173/admin/shamela`),
найти книгу, нажать «Import».

**Вариант B — seed script (для тестовой темы Мавлид):**
```bash
# Только для argument-map seed — НЕ для shamela book import
/home/basnukaev/projects/argument-map/scripts/seed-mawlid.sh
```

**Вариант C — curl (если есть прямой import endpoint):**
```bash
curl -X POST "http://localhost:9090/api/v1/shamela/books/<id>/import" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
# Проверить актуальный path в ShamelaAdminController
```

### Шаг 4: мониторинг

```bash
# Следить за progress в логах
tail -f /tmp/backend.log | grep -i "shamela\|import\|book"
```

```sql
-- Проверить результат через несколько секунд
SELECT id, title, source_id, created_at
FROM lib_books
WHERE source = 'shamela' AND source_id = '<id>';

SELECT COUNT(*) FROM lib_chapters WHERE book_id = '<book_uuid>';
SELECT COUNT(*) FROM lib_pages WHERE book_id = '<book_uuid>';
```

---

## 9. Bulk import policy — ОБЯЗАТЕЛЬНО ПРОЧИТАТЬ

**НИКОГДА не запускай массовый bulk import** без явного согласия Абдулы.

Причины:
1. **Нагружает shamela.ws** — частые запросы → rate limits, временный IP-бан
2. **Засевает мусор** — если parser имеет edge case bug, тысячи записей с
   неверными данными требуют cleanup всей БД
3. **Неизвестные edge cases** — shamela имеет ~6500 книг разных форматов,
   один парсер не может быть проверен без тестовой выборки

**Правильный workflow:**
```
Одна тестовая книга → UX validation → если OK → bulk
```

**Эскалация:** если в задаче явно или косвенно предполагается массовый импорт
(«импортировать все книги», «синхронизировать каталог», «добавить все книги
по теме») — **остановиться и уточнить у Абдулы** прежде чем запускать.

Это правило зафиксировано в `CLAUDE.md` корневом разделе
«Что НЕ делать → Общее»: «не запускать массовый shamela-парсинг без UX-валидации».

---

## 10. Live test mode

`@Tag("live")` тесты выполняют реальные вызовы shamela.ws. Они **исключены**
из стандартного `./mvnw verify`.

**Запуск:**
```bash
# Запускать только при изменении ShamelaApiClient / readers / parsers
cd /home/basnukaev/projects/argument-map/backend
./mvnw -Dgroups=live test -Dtest=ShamelaApiClientLiveIT
```

**Когда запускать:**
- После изменений в `shamela/api/ShamelaApiClient`
- После изменений в `shamela/etl/Shamela*Reader`
- После изменений в `SqliteValueParser`
- **НЕ запускать** при изменении только mapper / service слоя (unit тесты достаточны)

**Cost:** бесплатно (shamela.ws открытый), но занимает время + создаёт сетевые запросы.

---

## 11. Files cheat sheet

| Что | Где |
|---|---|
| API client | `backend/.../shamela/api/ShamelaApiClient.java` |
| API properties | `backend/.../shamela/api/ShamelaApiProperties.java` |
| API exception | `backend/.../shamela/api/ShamelaApiException.java` |
| Archive extractor | `backend/.../shamela/etl/ShamelaArchiveExtractor.java` |
| Master reader | `backend/.../shamela/etl/ShamelaMasterReader.java` |
| Book reader | `backend/.../shamela/etl/ShamelaBookReader.java` |
| SQLite parser | `backend/.../shamela/etl/SqliteValueParser.java` |
| Staging DTOs | `backend/.../shamela/etl/dto/` (ShamelaBookRow, ShamelaPageRow, и пр.) |
| Repository DAOs | `backend/.../shamela/repository/` (6 DAOs + ShamelaDaoSupport) |
| Text cleaner | `backend/.../shamela/service/mapper/ShamelaTextCleaner.java` |
| Bibliography parser | `backend/.../shamela/service/mapper/ShamelaBibliographyParser.java` |
| Authority resolver | `backend/.../shamela/service/mapper/ShamelaAuthorityResolver.java` |
| Book metadata builder | `backend/.../shamela/service/mapper/ShamelaBookMetadataBuilder.java` |
| Chapter mapper | `backend/.../shamela/service/mapper/ShamelaChapterMapper.java` |
| Page mapper | `backend/.../shamela/service/mapper/ShamelaPageMapper.java` |
| Book import service | `backend/.../shamela/service/ShamelaBookImportService.java` |
| Master sync service | `backend/.../shamela/service/ShamelaMasterSyncService.java` |
| Work dir manager | `backend/.../shamela/service/ShamelaWorkDirManager.java` |
| Admin controller | `backend/.../shamela/web/controller/ShamelaAdminController.java` |
| Admin UI | `frontend/src/apps/admin/pages/AdminShamelaPage.tsx` |
| Seed script (argument-map) | `scripts/seed-mawlid.sh` |
| Authority types + hadith | `backend/docs/hadith-grades.md` (AuthorityType.AUTHOR для shamela) |

---

## 12. Common errors table

| Ошибка | Симптом | Решение |
|---|---|---|
| Book imported but page rendering blank | UI показывает пустую страницу для shamela-книги | Проверить `text_content` и `ocr_status` в `lib_pages`. Это rendering issue → см. `library-page-rendering` skill |
| `DuplicateKeyException` при retry import | Лог: `duplicate key value violates unique constraint "lib_books_source_source_id_key"` | Очистить orphan rows (раздел 8). `DELETE FROM lib_books WHERE source='shamela' AND source_id='<id>'` |
| Mapper produces empty `text_content` | `lib_pages.text_content = ""` для всех страниц книги | `ShamelaTextCleaner` вычистил всё. Проверить raw content в SQLite: `sqlite3 file.db "SELECT content FROM books LIMIT 1"` |
| Year text contains «هـ» (hijri marker) | `SqliteValueParser.parseYearOrNull` вернул null для `"1340 هـ"` | Ожидаемое поведение — не числовой формат. Если нужно парсить — добавить regex в `parseYearOrNull`, зафиксировать в backlog |
| `ShamelaAuthorityResolver` creates duplicate | `DuplicateKeyException` на `authorities (name, type)` | Resolver создаёт new AUTHOR без проверки existing. Исправить: добавить `findByNameAndType` перед INSERT. Или: временно исправить имя в raw данных |
| ETL зависает (долго нет progress) | Backend запущен, import не завершается >10 минут | Проверить нет ли `@Tag("live")` тестов в обычном verify. Grep лог: `grep -i "stuck\|timeout\|waiting" /tmp/backend.log`. Возможно shamela.ws не отвечает на fetch |
| Staging DAO неверные данные | `lib_pages` содержит truncated или неверный арабский текст | Проверить SQLite напрямую (sqlite3 команды в разделе 5). Если SQLite корректен — проблема в `ShamelaTextCleaner` или encoding в JDBC driver |

---

## 13. Примеры

### Пример 1: Одна книга падает на Map шаге — diagnostic walk-through

**Ситуация:** импорт книги с `source_id = 1234` завершился ошибкой.
В логах: `ShamelaImportException: text mapping failed for book 1234`.

**Шаг 1: Проверить лог**
```bash
grep "1234\|mapping\|TextCleaner\|Bibliography" /tmp/backend.log | tail -30
```

**Шаг 2: Воспроизвести в unit тесте**

Если `ShamelaTextCleaner` — добавить test case с реальным raw content:
```java
@Test
void cleanText_withSpecificContent_returnsExpected() {
    // Вставить raw content из SQLite в тест
    String raw = "<div>..actual content from sqlite3 query..</div>";
    assertThat(cleaner.clean(raw)).isNotBlank();
}
// mvn -pl backend test -Dtest=ShamelaTextCleanerTest
```

**Шаг 3: Починить edge case**

Обычно: нестандартный HTML тег, неизвестный CSS class, или encoding.
Починить в `ShamelaTextCleaner`, добавить unit test, зафиксировать в
`docs/gotchas.md` (формат / root cause / fix).

**Шаг 4: Re-run** (см. раздел 8)

---

### Пример 2: Попытка массового bulk import — правильная эскалация

**Ситуация:** задача сформулирована как «синхронизировать все книги по категории
"фикх"» — это ~300 книг.

**Неправильное действие:** запустить loop по API и import все 300 книг подряд.

**Правильное действие:**

1. **Остановиться** — задача потенциально нарушает bulk import policy (раздел 9)
2. **Уточнить у Абдулы:**
   - «Задача предполагает импорт ~300 книг. По политике проекта (CLAUDE.md) массовый
     bulk import требует явного согласия. Прежде чем продолжить:
     - Нужно ли импортировать все 300 сразу или subset?
     - UX validation пройдена? (Одна книга показывается корректно в library?)
     - Shamela rate limits учтены?»
3. **Получить согласие** → реализовать с rate limiting (pause между запросами)
4. **Мониторить** первые 5-10 книг, убедиться качество данных OK → продолжить

---

### Пример 3: Shamela schema drift — новая колонка в master SQLite

**Ситуация:** после обновления shamela.ws зеркал, `ShamelaMasterReader` падает с
`SQLException: no such column: book_info`.

**Диагностика:**
```bash
# Найти скачанный master.db (или скачать заново)
sqlite3 /tmp/shamela-extract/master.db ".schema b"
# Вывод покажет актуальную схему таблицы 'b' (books в master)
```

Допустим в `.schema` теперь есть колонка `book_info TEXT` которой не было раньше,
но зато отсутствует `notes` которую `ShamelaMasterReader` читал.

**Варианты исправления:**

A. **Колонка переименована:** обновить имя в `ShamelaMasterReader.mapRow`:
```java
// Было:
String notes = rs.getString("notes");
// Стало:
String notes = rs.getString("book_info");
```

B. **Колонка удалена:** убрать чтение из reader, проверить не используется ли
в downstream mapper.

C. **Новая колонка добавлена и нужна:** добавить в `ShamelaBookRow` DTO, читать
в reader, использовать в mapper.

**После правки:** запустить live IT:
```bash
./mvnw -pl backend -Dgroups=live test -Dtest=ShamelaApiClientLiveIT
```

Зафиксировать в `docs/gotchas.md`: «Shamela schema drift YYYY-MM-DD: `notes`
переименован в `book_info` в master.db».

---

## 14. Pre-diagnosis checklist

Перед любой работой с Shamela ETL:

- [ ] Прочитан лог `/tmp/backend.log` на наличие shamela-related errors?
- [ ] Определён конкретный шаг failure (Fetch / Extract / Parse / Map / Persist)?
- [ ] Если задача предполагает запуск import — это одна тестовая книга, не bulk?
- [ ] Если re-run — orphan rows из предыдущей попытки очищены через DELETE?
- [ ] Если меняется reader / parser — unit тесты запущены
  (`-Dtest=ShamelaTextCleanerTest` / `ShamelaBibliographyParserTest`)?
- [ ] Если меняется `ShamelaApiClient` / readers — live IT запланирован
  (`@Tag("live")`) после изменений?
- [ ] Bulk import policy (раздел 9) соблюдена?
