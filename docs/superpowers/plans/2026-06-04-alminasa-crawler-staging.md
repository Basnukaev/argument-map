# alminasa Hadith Ingestion — Plan 2: ES-клиент + resumable краулер → am_staging_*

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HTTP-клиент к открытому ES-прокси alminasa.ai + фоновый возобновляемый краулер, который снимает полный снапшот корпуса (82,596 хадисов + рави + шархи + рулинги) в наши staging-таблицы `am_staging_*` с чекпоинтами.

**Architecture:** «hadith-first» краулинг — единственный resumable-цикл по индексу `hadith-12` (sort по `hadith_serial_id` + `search_after`); зависимые сущности (нарраторы, шархи, рулинги) добираются батчевыми `terms`-запросами по id из каждой страницы. Один чекпоинт (`am_crawl_checkpoint`), идемпотентные upsert'ы по природным ключам, сырой `_source` в JSONB (forward-compat — их индекс датирован `2024-08-24` и может молча смениться). Управление — admin-only REST (start/pause/status), сам краулинг — `@Async` на выделенном single-thread executor'е.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC Template, Liquibase (миграция 72), `java.net.http.HttpClient`, Jackson, Resilience4j `@Retry`, Testcontainers + JDK `com.sun.net.httpserver.HttpServer` (stub).

**Спека:** `docs/superpowers/specs/2026-06-03-alminasa-hadith-source-design.md` (раздел A + B-staging + G).
**Plan 1 (закрыт):** `docs/superpowers/plans/2026-06-03-alminasa-hadith-ingestion-schema.md` — миграции 70-71, домен, репозитории.

---

## Ключевые факты HTTP-контракта (из HAR, проверено 2026-06-04)

- Эндпоинт: `POST {base}/api/reactivesearchproxy/es-prod-euw1-{index}-read/_search`
  (префикс `es-prod-euw1-`, суффикс `-read`; `_msearch` существует, но нам НЕ нужен —
  батчи делаем одним `_search` c `terms`).
- Обязательные заголовки: `Origin: https://alminasa.ai`, `Referer: https://alminasa.ai/`,
  `Content-Type: application/json`. Авторизации/cookies нет.
- Индексы: `hadith-12` (82,596 доков), `narrators-12` (11,221), `hadith-explanation-12`,
  `rulings-12_v2`. Вкладки علل/غريب живут в индексах `hadith-commentary-12` /
  `chains-links-12` / `chains-graph-12` / `ambiguous-12` — их контракты НЕ сняты в HAR,
  отложены до Плана 6 (зафиксировать в gotchas).
- `hadith-12._source.hadith_serial_id` — глобально-уникальный int (наш sort/`search_after`
  ключ). `hadith_id` = `"{bookId}-{serial}"` (`"146-1"`). `narrators[].id` — **строка**
  (`"4698"`). У narrator-дока в `_source` НЕТ поля `id` — id берём из `_id` хита.
  ES `_id` хитов explanation/ruling — случайные строки (`"GqPGhpEBXUur4f6nXKde"`).
- Сайт пагинирует `from+size` (упрётся в ES-лимит 10k) — мы используем `search_after`,
  hits отдают `sort`-массив. Rate-limit заголовков нет, ответы CDN-кэшированы
  (s-maxage=86400) — но старт консервативный: delay 1000ms между страницами.
- Полные сэмплы: `/tmp/alminasa-fixtures/*.json` (Task 1 переносит выжимку в
  test/resources; если /tmp пуст — пере-извлечь из HAR-файлов в корне репо,
  они в .gitignore).

## File Structure

```
backend/src/main/resources/db/changelog/changes/
  20260604-72-alminasa-staging-tables.xml          [Task 2]
backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/
  api/AlminasaProperties.java                      [Task 5]  конфиг-record (prefix alminasa)
  api/AlminasaHttpClientConfig.java                [Task 5]  @Bean alminasaHttpClient (+прокси)
  api/AlminasaApiException.java                    [Task 6]  statusCode + transient-семантика
  api/AlminasaTransientFailurePredicate.java       [Task 6]  resilience4j predicate
  api/AlminasaEsClient.java                        [Task 6]  4 fetch-метода, @Retry
  api/dto/AlminasaHit.java                         [Task 6]  (id, source, sort)
  api/dto/AlminasaPage.java                        [Task 6]  (totalHits, hits)
  etl/AlminasaRows.java                            [Task 3]  JsonNode→Row статическая фабрика
  etl/dto/AmHadithRow.java                         [Task 3]
  etl/dto/AmNarratorRow.java                       [Task 3]
  etl/dto/AmExplanationRow.java                    [Task 3]
  etl/dto/AmRulingRow.java                         [Task 3]
  repository/AmDaoSupport.java                     [Task 4]  sumAffected (копия sunnah-версии: та умрёт в Плане 4)
  repository/AmHadithStagingDao.java               [Task 4]
  repository/AmNarratorStagingDao.java             [Task 4]
  repository/AmExplanationStagingDao.java          [Task 4]
  repository/AmRulingStagingDao.java               [Task 4]
  repository/AmCrawlCheckpointDao.java             [Task 4]
  repository/domain/AmCrawlCheckpoint.java         [Task 4]  record + enum AmCrawlStatus
  service/AlminasaCrawlConfig.java                 [Task 8]  executor 1 поток
  service/AlminasaCrawlService.java                [Task 8]  claimStart/crawlAsync/crawlLoop/pause/status
  web/AlminasaAdminController.java                 [Task 9]
  web/AlminasaCrawlConflictException.java          [Task 9]
  web/dto/AlminasaCrawlStatusResponse.java         [Task 9]
backend/src/main/java/ru/basnukaev/argumentmap/exception/GlobalExceptionHandler.java
                                                   [Task 9, modify] +handler 409
backend/src/main/resources/application.yml         [Task 5, modify] блок alminasa + retry instance
backend/src/test/resources/alminasa/               [Task 1]  hadith-page.json, hadith-page-empty.json,
                                                             narrators.json, explanations.json, rulings.json
backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/
  etl/AlminasaRowsTest.java                        [Task 3]  unit, фикстуры
  repository/AmStagingDaoIT.java                   [Task 4]  round-trip + идемпотентность
  api/AlminasaEsClientStubIT.java                  [Task 6]  standalone stub: shape запросов + парсинг
  api/AlminasaEsClientRetryIT.java                 [Task 7]  @SpringBootTest 503/503/200
  service/AlminasaCrawlServiceIT.java              [Task 8]  full/resume/pause/conflict/stale
  web/AlminasaAdminControllerIT.java               [Task 9]  MockMvc 403/202/409/200
docs/api-contract.md                               [Task 10, modify]
docs/gotchas.md                                    [Task 10, modify]
docs/architecture.md                               [Task 10, modify]
frontend/src/shared/api/types.ts                   [Task 10, regen]
```

Контракт фазы: краулер пишет ТОЛЬКО в `am_staging_*`. Маппинг в `hd_*` — План 3.

---

## Task 1: Тестовые фикстуры из HAR-выжимки

**Files:**
- Create: `backend/src/test/resources/alminasa/hadith-page.json`
- Create: `backend/src/test/resources/alminasa/hadith-page-empty.json`
- Create: `backend/src/test/resources/alminasa/narrators.json`
- Create: `backend/src/test/resources/alminasa/explanations.json`
- Create: `backend/src/test/resources/alminasa/rulings.json`

Каждый файл — тело ОДНОГО ES `_search`-ответа (`{took, hits:{total, hits:[...]}}`),
урезанное до 1-2 хитов, но с ПОЛНЫМ `_source` каждого хита (включая длинные
арабские тексты — они и есть предмет теста).

- [ ] **Step 1: Сгенерировать фикстуры из /tmp/alminasa-fixtures**

```bash
mkdir -p backend/src/test/resources/alminasa && python3 - <<'EOF'
import json, pathlib
src = pathlib.Path('/tmp/alminasa-fixtures')
dst = pathlib.Path('backend/src/test/resources/alminasa')

def first_response(fname):
    d = json.load(open(src / fname))
    rb = d['response_body']
    return rb['responses'][0] if 'responses' in rb else rb

def trim(resp, n):
    hits = resp['hits']['hits'][:n]
    return {'took': resp.get('took', 1),
            'hits': {'total': resp['hits']['total'], 'max_score': None, 'hits': hits}}

hadith = first_response('hadith-12_msearch_roads.json')
(dst / 'hadith-page.json').write_text(
    json.dumps(trim(hadith, 2), ensure_ascii=False, indent=1), encoding='utf-8')
empty = {'took': 1, 'hits': {'total': hadith['hits']['total'], 'max_score': None, 'hits': []}}
(dst / 'hadith-page-empty.json').write_text(
    json.dumps(empty, ensure_ascii=False, indent=1), encoding='utf-8')
(dst / 'narrators.json').write_text(
    json.dumps(trim(first_response('narrators-12_msearch.json'), 2), ensure_ascii=False, indent=1), encoding='utf-8')
(dst / 'explanations.json').write_text(
    json.dumps(trim(first_response('hadith-explanation-12_msearch.json'), 2), ensure_ascii=False, indent=1), encoding='utf-8')
(dst / 'rulings.json').write_text(
    json.dumps(trim(first_response('rulings-12_v2_msearch.json'), 2), ensure_ascii=False, indent=1), encoding='utf-8')
print('ok:', [p.name for p in sorted(dst.iterdir())])
EOF
```

Expected: `ok: ['explanations.json', 'hadith-page-empty.json', 'hadith-page.json', 'narrators.json', 'rulings.json']`

Fallback если `/tmp/alminasa-fixtures` пропал: открыть
`alminasa.ai_Archive [26-06-02 20-58-09].har` в корне репо, найти в
`log.entries[]` POST'ы на `reactivesearchproxy` (response.content.text — JSON,
иногда base64), извлечь те же ответы.

- [ ] **Step 2: Проверить ключевые инварианты фикстур**

```bash
python3 - <<'EOF'
import json
h = json.load(open('backend/src/test/resources/alminasa/hadith-page.json'))
hit = h['hits']['hits'][0]
assert hit['_source']['hadith_id'] == '146-1'
assert hit['_source']['hadith_serial_id'] == 1
assert hit['_source']['type'] == 'مرفوع'
assert isinstance(hit['_source']['narrators'][0]['id'], str)  # id рави — строка!
n = json.load(open('backend/src/test/resources/alminasa/narrators.json'))
assert n['hits']['hits'][0]['_id'] == '5719'
assert 'id' not in n['hits']['hits'][0]['_source']             # id только в _id
e = json.load(open('backend/src/test/resources/alminasa/explanations.json'))
assert e['hits']['hits'][0]['_source']['hadith']['hadith_id'] == '146-1'
r = json.load(open('backend/src/test/resources/alminasa/rulings.json'))
assert r['hits']['hits'][0]['_source']['ruler'] == 'البخاري'
assert r['hits']['hits'][0]['_source']['ruler_dod'] == 256
print('fixtures OK')
EOF
```

Expected: `fixtures OK`

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/resources/alminasa/
git commit -m "test(hadith): фикстуры alminasa ES-ответов из HAR для клиента/краулера (Сессия 56)"
```

---

## Task 2: Миграция 72 — staging-таблицы + чекпоинт

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/20260604-72-alminasa-staging-tables.xml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml` (последний `<include>`)

Skill-чеклист `liquibase-migration`: ID `20260604-72-alminasa-staging-tables`,
author `Abdula Basnukaev`, rollback, регистрация в master, CDATA.

- [ ] **Step 1: Создать файл миграции**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        План 2 alminasa-инжеста (ADR-060): staging-слой am_staging_* + чекпоинт
        краулинга. Спека: docs/superpowers/specs/2026-06-03-alminasa-hadith-source-design.md §A/§B.

        Паттерн зеркалит sn_staging_* (миграция 59, ADR-051), отличия:

          (1) Природные ключи alminasa — hadith_id "{bookId}-{serial}" и numeric
              narrator id — устойчивые внешние идентификаторы (HAR-анализ),
              PK напрямую. Для explanation/ruling доков своего природного ключа
              нет — PK = ES _id (es_id): индекс датирован 2024-08-24 и фактически
              иммутабелен, _id стабилен между запросами.

          (2) raw jsonb NOT NULL — полный _source (forward-compat: пере-маппинг
              без пере-краулинга; контракт alminasa может молча смениться).
              Горячие колонки — только то, что нужно прогрессу/каталогу/Плану 3
              для выборок.

          (3) am_staging_hadith.hadith_serial_id UNIQUE — это sort-ключ
              search_after-пагинации; уникальность = гарантия что resume не
              теряет и не дублирует доки. Упадёт на живом краулинге → сигнал
              что ключ ненадёжен и пагинацию надо пересматривать.

          (4) БЕЗ FK между staging-таблицами: staging — landing zone сырых
              данных, согласованность проверяет маппер (План 3). Зависимые доки
              (ruling/explanation) могут ссылаться на ещё-не-скраулённые хадисы.

          (5) am_crawl_checkpoint generic по index_name — Планы 6+ добавят
              индексы (ambiguous-12, hadith-commentary-12) без новой миграции.
    -->
    <changeSet id="20260604-72-alminasa-staging-tables" author="Abdula Basnukaev">
        <comment>План 2 alminasa: staging-таблицы am_staging_* + am_crawl_checkpoint</comment>
        <sql><![CDATA[
            CREATE TABLE am_staging_hadith (
                hadith_id        varchar(20) PRIMARY KEY,
                book_id          integer NOT NULL,
                hadith_serial_id bigint  NOT NULL,
                book_name        text,
                hadith_type      varchar(50),
                chapter          text,
                sub_chapter      text,
                raw              jsonb NOT NULL,
                imported_at      timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_am_staging_hadith_book ON am_staging_hadith(book_id);
            CREATE UNIQUE INDEX idx_am_staging_hadith_serial ON am_staging_hadith(hadith_serial_id);

            CREATE TABLE am_staging_narrator (
                narrator_id  bigint PRIMARY KEY,
                full_name    text,
                grade        text,
                level        text,
                raw          jsonb NOT NULL,
                imported_at  timestamptz NOT NULL DEFAULT now()
            );

            CREATE TABLE am_staging_explanation (
                es_id        varchar(100) PRIMARY KEY,
                hadith_id    varchar(20) NOT NULL,
                book_name    text,
                author       text,
                raw          jsonb NOT NULL,
                imported_at  timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_am_staging_explanation_hadith ON am_staging_explanation(hadith_id);

            CREATE TABLE am_staging_ruling (
                es_id           varchar(100) PRIMARY KEY,
                hadith_id       varchar(20) NOT NULL,
                ruler           text,
                ruler_dod       integer,
                narrations_type varchar(30),
                raw             jsonb NOT NULL,
                imported_at     timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX idx_am_staging_ruling_hadith ON am_staging_ruling(hadith_id);

            CREATE TABLE am_crawl_checkpoint (
                index_name      varchar(50) PRIMARY KEY,
                status          varchar(20) NOT NULL DEFAULT 'IDLE'
                                CHECK (status IN ('IDLE','RUNNING','PAUSED','FAILED','COMPLETED')),
                last_sort_value bigint,
                fetched_count   bigint NOT NULL DEFAULT 0,
                total_hits      bigint,
                error           text,
                started_at      timestamptz,
                updated_at      timestamptz NOT NULL DEFAULT now()
            );
        ]]></sql>
        <rollback>
            <sql><![CDATA[
                DROP TABLE IF EXISTS am_crawl_checkpoint;
                DROP TABLE IF EXISTS am_staging_ruling;
                DROP TABLE IF EXISTS am_staging_explanation;
                DROP TABLE IF EXISTS am_staging_narrator;
                DROP TABLE IF EXISTS am_staging_hadith;
            ]]></sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Зарегистрировать в master changelog**

В `backend/src/main/resources/db/changelog/db.changelog-master.xml` после строки
`<include file="db/changelog/changes/20260603-71-hd-alminasa-tables.xml"/>` добавить:

```xml
    <include file="db/changelog/changes/20260604-72-alminasa-staging-tables.xml"/>
```

- [ ] **Step 3: Проверить что миграция применяется**

Точечный прогон существующего IT (поднимает Testcontainers и прогоняет ВСЕ миграции):

```bash
cd backend && ./mvnw -Dit.test=AlminasaSchemaRepositoryIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `BUILD SUCCESS` (если Liquibase-ошибка — упадёт на старте контекста).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/changelog/
git commit -m "feat(hadith): миграция 72 — staging-таблицы alminasa + чекпоинт краулинга (Сессия 56)"
```

---

## Task 3: Row-records + фабрика AlminasaRows (JsonNode → Row)

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/etl/dto/AmHadithRow.java`
- Create: `.../etl/dto/AmNarratorRow.java`
- Create: `.../etl/dto/AmExplanationRow.java`
- Create: `.../etl/dto/AmRulingRow.java`
- Create: `.../api/dto/AlminasaHit.java` (нужен фабрике; клиентские методы — Task 6)
- Create: `.../etl/AlminasaRows.java`
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/etl/AlminasaRowsTest.java`

- [ ] **Step 1: Написать падающий unit-тест**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/**
 * Unit-тесты фабрики {@link AlminasaRows} на реальных _source из HAR-фикстур
 * (test/resources/alminasa). План 2 alminasa, спека §B staging.
 */
class AlminasaRowsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AlminasaHit firstHit(String fixture) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + fixture)) {
            JsonNode resp = MAPPER.readTree(in);
            JsonNode hit = resp.path("hits").path("hits").get(0);
            return new AlminasaHit(hit.path("_id").asText(), hit.path("_source"), hit.path("sort"));
        }
    }

    @Test
    void fromHadithHit_парсит_горячие_поля_и_raw() throws IOException {
        AmHadithRow row = AlminasaRows.fromHadithHit(firstHit("hadith-page.json"));

        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.bookId()).isEqualTo(146);
        assertThat(row.hadithSerialId()).isEqualTo(1L);
        assertThat(row.bookName()).isEqualTo("صحيح البخاري");
        assertThat(row.hadithType()).isEqualTo("مرفوع");
        assertThat(row.chapter()).isEqualTo("باب بدء الوحي");
        // raw — полный _source как JSON-строка (jsonb-колонка)
        assertThat(MAPPER.readTree(row.rawJson()).path("matn_with_tashkeel").asText()).isNotBlank();
    }

    @Test
    void fromHadithHit_кривой_hadith_id_бросает() {
        JsonNode source = MAPPER.createObjectNode()
                .put("hadith_id", "no-dash-but-not-number")
                .put("hadith_serial_id", 5);
        assertThatThrownBy(() -> AlminasaRows.fromHadithHit(new AlminasaHit("x", source, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-dash-but-not-number");
    }

    @Test
    void fromNarratorHit_id_из_ES_id_хита() throws IOException {
        AmNarratorRow row = AlminasaRows.fromNarratorHit(firstHit("narrators.json"));

        assertThat(row.narratorId()).isEqualTo(5719L);
        assertThat(row.fullName()).isEqualTo("علقمة بن وقاص العتواري");
        assertThat(row.grade()).isEqualTo("ثقة ثبت");
        assertThat(row.level()).isEqualTo("الثانية");
    }

    @Test
    void fromExplanationHit_hadith_id_из_вложенного_hadith() throws IOException {
        AmExplanationRow row = AlminasaRows.fromExplanationHit(firstHit("explanations.json"));

        assertThat(row.esId()).isEqualTo("GqPGhpEBXUur4f6nXKde");
        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.bookName()).isEqualTo("فتح الباري بشرح صحيح البخاري");
        assertThat(row.author()).isEqualTo("ابن حجر العسقلاني"); // trailing space из источника triммится
    }

    @Test
    void fromRulingHit_парсит_ruler_и_dod() throws IOException {
        AmRulingRow row = AlminasaRows.fromRulingHit(firstHit("rulings.json"));

        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.ruler()).isEqualTo("البخاري");
        assertThat(row.rulerDod()).isEqualTo(256);
        assertThat(row.narrationsType()).isEqualTo("raw");
    }
}
```

- [ ] **Step 2: Прогнать тест — убедиться что падает (классов нет)**

```bash
cd backend && ./mvnw -Dtest=AlminasaRowsTest test
```

Expected: COMPILATION ERROR (классы ещё не существуют).

- [ ] **Step 3: Создать records**

`api/dto/AlminasaHit.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Один хит ES-ответа alminasa: {@code _id}, {@code _source} и {@code sort}
 * (значения сортировки для search_after-пагинации). План 2 alminasa.
 */
public record AlminasaHit(String id, JsonNode source, JsonNode sort) {
}
```

`etl/dto/AmHadithRow.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_hadith}: горячие поля + полный _source в raw. */
public record AmHadithRow(
        String hadithId,
        int bookId,
        long hadithSerialId,
        String bookName,
        String hadithType,
        String chapter,
        String subChapter,
        String rawJson
) {
}
```

`etl/dto/AmNarratorRow.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_narrator}. id — numeric id alminasa (из ES _id). */
public record AmNarratorRow(
        long narratorId,
        String fullName,
        String grade,
        String level,
        String rawJson
) {
}
```

`etl/dto/AmExplanationRow.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_explanation}. PK — ES _id (индекс-снапшот иммутабелен). */
public record AmExplanationRow(
        String esId,
        String hadithId,
        String bookName,
        String author,
        String rawJson
) {
}
```

`etl/dto/AmRulingRow.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_ruling}. Один док = один ruler × hadith. */
public record AmRulingRow(
        String esId,
        String hadithId,
        String ruler,
        Integer rulerDod,
        String narrationsType,
        String rawJson
) {
}
```

- [ ] **Step 4: Реализовать фабрику**

`etl/AlminasaRows.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/**
 * Статическая фабрика: ES-хит alminasa → staging-row. Горячие поля
 * вынимаются из {@code _source}, остальное едет в raw jsonb как есть
 * (forward-compat, спека §A). Падает с {@link IllegalArgumentException}
 * на структурно-битом доке — краулер тогда уходит в FAILED с понятным
 * сообщением (fail-fast: индекс — статичный снапшот, битый док = баг
 * наших ожиданий, а не «грязные данные»).
 */
public final class AlminasaRows {

    private AlminasaRows() {
    }

    public static AmHadithRow fromHadithHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        String hadithId = requireText(src, "hadith_id");
        int dash = hadithId.indexOf('-');
        int bookId;
        try {
            bookId = Integer.parseInt(hadithId.substring(0, Math.max(dash, 0)));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(
                    "alminasa hadith_id не в формате {bookId}-{serial}: " + hadithId, e);
        }
        long serial = src.path("hadith_serial_id").asLong(-1);
        if (serial < 0) {
            throw new IllegalArgumentException(
                    "alminasa док без hadith_serial_id: " + hadithId);
        }
        return new AmHadithRow(
                hadithId,
                bookId,
                serial,
                textOrNull(src, "book_name"),
                textOrNull(src, "type"),
                textOrNull(src, "chapter"),
                textOrNull(src, "sub_chapter"),
                src.toString()
        );
    }

    public static AmNarratorRow fromNarratorHit(AlminasaHit hit) {
        long id;
        try {
            id = Long.parseLong(hit.id());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("alminasa narrator _id не numeric: " + hit.id(), e);
        }
        JsonNode src = hit.source();
        return new AmNarratorRow(
                id,
                textOrNull(src, "full_name"),
                textOrNull(src, "grade"),
                textOrNull(src, "level"),
                src.toString()
        );
    }

    public static AmExplanationRow fromExplanationHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode hadith = src.path("hadith");
        JsonNode explanation = src.path("explanation");
        return new AmExplanationRow(
                hit.id(),
                requireText(hadith, "hadith_id"),
                textOrNull(explanation, "explanation_book_name"),
                textOrNull(explanation, "explanation_book_author"),
                src.toString()
        );
    }

    public static AmRulingRow fromRulingHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode dod = src.path("ruler_dod");
        return new AmRulingRow(
                hit.id(),
                requireText(src, "hadith_id"),
                textOrNull(src, "ruler"),
                dod.isNumber() ? dod.asInt() : null,
                textOrNull(src, "narrations_type"),
                src.toString()
        );
    }

    private static String requireText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("alminasa док без обязательного поля " + field);
        }
        return value.trim();
    }

    /** Текст поля с trim'ом (источник содержит trailing spaces), null если нет. */
    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
```

- [ ] **Step 5: Прогнать тест — PASS**

```bash
cd backend && ./mvnw -Dtest=AlminasaRowsTest test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0` → `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/ backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/
git commit -m "feat(hadith): alminasa row-records + фабрика AlminasaRows из ES-хитов (Сессия 56)"
```

---

## Task 4: Staging-DAO + чекпоинт-DAO

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/repository/AmDaoSupport.java`
- Create: `.../repository/AmHadithStagingDao.java`
- Create: `.../repository/AmNarratorStagingDao.java`
- Create: `.../repository/AmExplanationStagingDao.java`
- Create: `.../repository/AmRulingStagingDao.java`
- Create: `.../repository/domain/AmCrawlCheckpoint.java`
- Create: `.../repository/AmCrawlCheckpointDao.java`
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/repository/AmStagingDaoIT.java`

- [ ] **Step 1: Написать падающий IT**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;

/**
 * Round-trip IT staging-DAO alminasa (миграция 72): upsert идемпотентен по
 * природному ключу, чекпоинт проходит полный жизненный цикл статусов.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AmStagingDaoIT {

    @Autowired private AmHadithStagingDao hadithDao;
    @Autowired private AmNarratorStagingDao narratorDao;
    @Autowired private AmExplanationStagingDao explanationDao;
    @Autowired private AmRulingStagingDao rulingDao;
    @Autowired private AmCrawlCheckpointDao checkpointDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static AmHadithRow hadith(String id, long serial, String type) {
        return new AmHadithRow(id, 146, serial, "صحيح البخاري", type,
                "باب بدء الوحي", null, "{\"hadith_id\":\"" + id + "\"}");
    }

    @Test
    void hadith_upsert_идемпотентен_и_обновляет_поля() {
        hadithDao.upsertAll(List.of(hadith("146-1", 1, "مرفوع"), hadith("146-2", 2, "مرفوع")));
        assertThat(hadithDao.count()).isEqualTo(2);

        // Повторный upsert того же ключа с другим type — строка одна, поле новое
        hadithDao.upsertAll(List.of(hadith("146-1", 1, "موقوف")));
        assertThat(hadithDao.count()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hadith_type FROM am_staging_hadith WHERE hadith_id = '146-1'", String.class))
                .isEqualTo("موقوف");
        // raw — валидный jsonb
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw->>'hadith_id' FROM am_staging_hadith WHERE hadith_id = '146-1'", String.class))
                .isEqualTo("146-1");
    }

    @Test
    void narrator_upsert_и_findAllIds() {
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", "{}"),
                new AmNarratorRow(4698, "الحميدي", "ثقة حافظ", "العاشرة", "{}")));
        narratorDao.upsertAll(List.of(
                new AmNarratorRow(5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", "{}")));

        assertThat(narratorDao.count()).isEqualTo(2);
        assertThat(narratorDao.findAllIds()).containsExactlyInAnyOrder(5719L, 4698L);
    }

    @Test
    void explanation_и_ruling_upsert_по_es_id() {
        explanationDao.upsertAll(List.of(new AmExplanationRow(
                "GqPGhpEBXUur4f6nXKde", "146-1", "فتح الباري", "ابن حجر العسقلاني", "{}")));
        explanationDao.upsertAll(List.of(new AmExplanationRow(
                "GqPGhpEBXUur4f6nXKde", "146-1", "فتح الباري بشرح صحيح البخاري", "ابن حجر العسقلاني", "{}")));
        assertThat(explanationDao.count()).isEqualTo(1);

        rulingDao.upsertAll(List.of(new AmRulingRow(
                "yanMhpEBXUur4f6nVw_U", "146-1", "البخاري", 256, "raw", "{}")));
        rulingDao.upsertAll(List.of(new AmRulingRow(
                "yanMhpEBXUur4f6nVw_U", "146-1", "البخاري", 256, "raw", "{}")));
        assertThat(rulingDao.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ruler_dod FROM am_staging_ruling WHERE es_id = 'yanMhpEBXUur4f6nVw_U'", Integer.class))
                .isEqualTo(256);
    }

    @Test
    void checkpoint_полный_жизненный_цикл() {
        assertThat(checkpointDao.find("hadith-12")).isEmpty();

        AmCrawlCheckpoint started = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(started.status()).isEqualTo(AmCrawlStatus.RUNNING);
        assertThat(started.lastSortValue()).isNull();
        assertThat(started.fetchedCount()).isZero();
        assertThat(started.startedAt()).isNotNull();

        checkpointDao.setTotalHits("hadith-12", 82596L);
        checkpointDao.advance("hadith-12", 100L, 100);
        checkpointDao.advance("hadith-12", 200L, 100);

        AmCrawlCheckpoint mid = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(mid.lastSortValue()).isEqualTo(200L);
        assertThat(mid.fetchedCount()).isEqualTo(200L);
        assertThat(mid.totalHits()).isEqualTo(82596L);

        checkpointDao.markPaused("hadith-12");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().status())
                .isEqualTo(AmCrawlStatus.PAUSED);

        // resume БЕЗ reset — прогресс сохраняется
        AmCrawlCheckpoint resumed = checkpointDao.upsertRunning("hadith-12", false);
        assertThat(resumed.lastSortValue()).isEqualTo(200L);
        assertThat(resumed.fetchedCount()).isEqualTo(200L);

        checkpointDao.markFailed("hadith-12", "boom");
        assertThat(checkpointDao.find("hadith-12").orElseThrow().error()).isEqualTo("boom");

        checkpointDao.markCompleted("hadith-12");
        AmCrawlCheckpoint done = checkpointDao.find("hadith-12").orElseThrow();
        assertThat(done.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(done.error()).isNull();

        // рестарт с reset — прогресс обнуляется
        AmCrawlCheckpoint fresh = checkpointDao.upsertRunning("hadith-12", true);
        assertThat(fresh.lastSortValue()).isNull();
        assertThat(fresh.fetchedCount()).isZero();
    }
}
```

- [ ] **Step 2: Прогнать — FAIL (классов нет)**

```bash
cd backend && ./mvnw -Dit.test=AmStagingDaoIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: AmDaoSupport**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

/**
 * Хелперы staging-DAO alminasa. Копия {@code SunnahDaoSupport} — sunnah-пакет
 * удаляется Планом 4 (ADR-060), зависеть от него нельзя.
 */
final class AmDaoSupport {

    private AmDaoSupport() {
    }

    /**
     * Суммирует затронутые строки batch'а; {@code SUCCESS_NO_INFO (-2)}
     * считаем как 1. Использовать только для логирования.
     */
    static int sumAffected(int[] affected) {
        int total = 0;
        for (int n : affected) {
            total += (n >= 0) ? n : 1;
        }
        return total;
    }
}
```

- [ ] **Step 4: Четыре staging-DAO** (паттерн `SunnahHadithDao`: batchUpdate + ON CONFLICT + `?::jsonb`)

`AmHadithStagingDao.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;

/** DAO {@code am_staging_hadith} (миграция 72). План 2 alminasa. */
@Repository
public class AmHadithStagingDao {

    private final JdbcTemplate jdbcTemplate;

    public AmHadithStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmHadithRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_hadith (
                    hadith_id, book_id, hadith_serial_id, book_name, hadith_type,
                    chapter, sub_chapter, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (hadith_id) DO UPDATE SET
                    book_id = EXCLUDED.book_id,
                    hadith_serial_id = EXCLUDED.hadith_serial_id,
                    book_name = EXCLUDED.book_name,
                    hadith_type = EXCLUDED.hadith_type,
                    chapter = EXCLUDED.chapter,
                    sub_chapter = EXCLUDED.sub_chapter,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.hadithId(), r.bookId(), r.hadithSerialId(), r.bookName(), r.hadithType(),
                r.chapter(), r.subChapter(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_hadith", Long.class);
        return count == null ? 0 : count;
    }
}
```

`AmNarratorStagingDao.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;

/** DAO {@code am_staging_narrator} (миграция 72). План 2 alminasa. */
@Repository
public class AmNarratorStagingDao {

    private final JdbcTemplate jdbcTemplate;

    public AmNarratorStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmNarratorRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_narrator (narrator_id, full_name, grade, level, raw)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (narrator_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    grade = EXCLUDED.grade,
                    level = EXCLUDED.level,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.narratorId(), r.fullName(), r.grade(), r.level(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /** Все уже-скраулённые id — seed дедупликации краулера при resume. */
    public List<Long> findAllIds() {
        return jdbcTemplate.queryForList(
                "SELECT narrator_id FROM am_staging_narrator", Long.class);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_narrator", Long.class);
        return count == null ? 0 : count;
    }
}
```

`AmExplanationStagingDao.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;

/** DAO {@code am_staging_explanation} (миграция 72). План 2 alminasa. */
@Repository
public class AmExplanationStagingDao {

    private final JdbcTemplate jdbcTemplate;

    public AmExplanationStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmExplanationRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_explanation (es_id, hadith_id, book_name, author, raw)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (es_id) DO UPDATE SET
                    hadith_id = EXCLUDED.hadith_id,
                    book_name = EXCLUDED.book_name,
                    author = EXCLUDED.author,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.esId(), r.hadithId(), r.bookName(), r.author(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_explanation", Long.class);
        return count == null ? 0 : count;
    }
}
```

`AmRulingStagingDao.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/** DAO {@code am_staging_ruling} (миграция 72). План 2 alminasa. */
@Repository
public class AmRulingStagingDao {

    private final JdbcTemplate jdbcTemplate;

    public AmRulingStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmRulingRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_ruling (es_id, hadith_id, ruler, ruler_dod, narrations_type, raw)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (es_id) DO UPDATE SET
                    hadith_id = EXCLUDED.hadith_id,
                    ruler = EXCLUDED.ruler,
                    ruler_dod = EXCLUDED.ruler_dod,
                    narrations_type = EXCLUDED.narrations_type,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.esId(), r.hadithId(), r.ruler(), r.rulerDod(), r.narrationsType(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_ruling", Long.class);
        return count == null ? 0 : count;
    }
}
```

- [ ] **Step 5: Чекпоинт — record + DAO**

`repository/domain/AmCrawlCheckpoint.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository.domain;

import java.time.OffsetDateTime;

/**
 * Чекпоинт краулинга одного ES-индекса alminasa ({@code am_crawl_checkpoint}).
 * {@code lastSortValue} — последний {@code hadith_serial_id} (search_after).
 */
public record AmCrawlCheckpoint(
        String indexName,
        AmCrawlStatus status,
        Long lastSortValue,
        long fetchedCount,
        Long totalHits,
        String error,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt
) {

    public enum AmCrawlStatus {
        IDLE, RUNNING, PAUSED, FAILED, COMPLETED
    }
}
```

`AmCrawlCheckpointDao.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;

/** DAO {@code am_crawl_checkpoint} (миграция 72). План 2 alminasa. */
@Repository
public class AmCrawlCheckpointDao {

    private static final String COLUMNS =
            "index_name, status, last_sort_value, fetched_count, total_hits, error, started_at, updated_at";

    private static final RowMapper<AmCrawlCheckpoint> ROW_MAPPER = (rs, rn) -> new AmCrawlCheckpoint(
            rs.getString("index_name"),
            AmCrawlStatus.valueOf(rs.getString("status")),
            rs.getObject("last_sort_value", Long.class),
            rs.getLong("fetched_count"),
            rs.getObject("total_hits", Long.class),
            rs.getString("error"),
            rs.getObject("started_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public AmCrawlCheckpointDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AmCrawlCheckpoint> find(String indexName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM am_crawl_checkpoint WHERE index_name = ?",
                ROW_MAPPER, indexName
        ).stream().findFirst();
    }

    /**
     * Перевод в RUNNING (создаёт строку при отсутствии). {@code resetProgress}
     * — обнулить прогресс (свежий краулинг с нуля) или сохранить
     * last_sort_value/fetched_count (resume после PAUSED/FAILED/stale).
     */
    public AmCrawlCheckpoint upsertRunning(String indexName, boolean resetProgress) {
        jdbcTemplate.update("""
                INSERT INTO am_crawl_checkpoint (index_name, status, started_at, updated_at)
                VALUES (?, 'RUNNING', now(), now())
                ON CONFLICT (index_name) DO UPDATE SET
                    status = 'RUNNING',
                    last_sort_value = CASE WHEN ? THEN NULL ELSE am_crawl_checkpoint.last_sort_value END,
                    fetched_count   = CASE WHEN ? THEN 0    ELSE am_crawl_checkpoint.fetched_count   END,
                    error = NULL,
                    started_at = now(),
                    updated_at = now()
                """, indexName, resetProgress, resetProgress);
        return find(indexName).orElseThrow();
    }

    public void setTotalHits(String indexName, long totalHits) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET total_hits = ?, updated_at = now() WHERE index_name = ?",
                totalHits, indexName);
    }

    /** Граница страницы: новый search_after-курсор + приращение счётчика. */
    public void advance(String indexName, long lastSortValue, int addedCount) {
        jdbcTemplate.update("""
                UPDATE am_crawl_checkpoint
                SET last_sort_value = ?, fetched_count = fetched_count + ?, updated_at = now()
                WHERE index_name = ?
                """, lastSortValue, addedCount, indexName);
    }

    public void markCompleted(String indexName) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'COMPLETED', error = NULL, updated_at = now() "
                        + "WHERE index_name = ?", indexName);
    }

    public void markPaused(String indexName) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'PAUSED', updated_at = now() "
                        + "WHERE index_name = ?", indexName);
    }

    public void markFailed(String indexName, String error) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'FAILED', error = ?, updated_at = now() "
                        + "WHERE index_name = ?", error, indexName);
    }
}
```

- [ ] **Step 6: Прогнать IT — PASS**

```bash
cd backend && ./mvnw -Dit.test=AmStagingDaoIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` → `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/repository/ backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/repository/
git commit -m "feat(hadith): staging-DAO alminasa + чекпоинт-DAO с resume-семантикой (Сессия 56)"
```

---

## Task 5: Конфигурация — properties, HttpClient, application.yml

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/api/AlminasaProperties.java`
- Create: `.../api/AlminasaHttpClientConfig.java`
- Modify: `backend/src/main/resources/application.yml` (блок `alminasa:` после блока `sunnah:`; retry-инстанс в существующий `resilience4j.retry.instances`)

- [ ] **Step 1: Properties record**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация доступа к открытому ES-прокси alminasa.ai (ADR-060, спека §A).
 *
 * <p>{@code origin} отдельно от {@code baseUrl}: прокси проверяет только
 * заголовки Origin/Referer (HAR-анализ) — в IT base-url подменяется на
 * локальный stub, а Origin продолжает указывать на alminasa.ai.
 *
 * <p>{@code httpProxy} — опциональный корп-прокси {@code http://[user:pass@]host:port},
 * вешается ТОЛЬКО на alminasa-HttpClient (не глобально). Аутентификация через
 * {@link java.net.Authenticator} безопасна: alminasa не требует серверного
 * Authorization-заголовка (ср. gotcha «LLM за корп-прокси», где Authenticator
 * вырезал Bearer).
 */
@ConfigurationProperties(prefix = "alminasa")
public record AlminasaProperties(
        boolean enabled,
        String baseUrl,
        String origin,
        String indexPrefix,
        String indexSuffix,
        String httpProxy,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        Crawl crawl
) {

    public record Crawl(
            /** Размер страницы hadith-12 (search_after). */
            int pageSize,
            /** Пауза между страницами, мс — консервативный rate-limit (спека §G). */
            long delayMs,
            /** Сколько hadith_id в одном terms-запросе зависимых индексов. */
            int dependentBatchSize,
            /** size для terms-ответов rulings/explanations (warn при переполнении). */
            int dependentFetchSize,
            /** RUNNING-claim старше этого — считается мёртвым (перехват, как ai.edit). */
            int staleTimeoutMinutes
    ) {
    }
}
```

- [ ] **Step 2: HttpClient config**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link HttpClient} для alminasa ES-прокси (План 2, ADR-060). Опциональный
 * корп-прокси per-client (НЕ глобально — иначе внутренний S3/MinIO-трафик
 * пошёл бы через прокси). Authenticator-подход как у shamela: alminasa не
 * шлёт серверный Authorization, так что JDK-вырезание заголовка (gotcha
 * «LLM за корп-прокси») здесь не стреляет.
 *
 * <p>{@code alminasa.enabled=false} выключает ВСЕ alminasa-бины (клиент,
 * краулер, admin-endpoints → 404). Default on: прокси публичный read-only,
 * секретов для конструирования бинов не нужно.
 */
@Configuration
@EnableConfigurationProperties(AlminasaProperties.class)
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaHttpClientConfig {

    @Bean
    public HttpClient alminasaHttpClient(AlminasaProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()));
        String proxyUrl = props.httpProxy();
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI uri = URI.create(proxyUrl.trim());
            if (uri.getHost() != null && uri.getPort() > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(user, pass.toCharArray());
                        }
                    });
                }
            }
        }
        return builder.build();
    }
}
```

- [ ] **Step 3: application.yml**

После блока `sunnah:` добавить (отступы как у соседей):

```yaml
# План 2 alminasa (ADR-060): открытый ES-прокси, краулер → am_staging_*.
# origin отдельно от base-url: прокси проверяет Origin/Referer, в IT
# base-url подменяется stub'ом. Прокси-URL только для alminasa-клиента.
alminasa:
  enabled: ${ALMINASA_ENABLED:true}
  base-url: ${ALMINASA_BASE_URL:https://alminasa.ai}
  origin: https://alminasa.ai
  index-prefix: es-prod-euw1-
  index-suffix: -read
  http-proxy: ${ALMINASA_HTTP_PROXY:}
  connect-timeout-seconds: 10
  request-timeout-seconds: 60
  crawl:
    page-size: 100
    delay-ms: 1000
    dependent-batch-size: 25
    dependent-fetch-size: 500
    stale-timeout-minutes: 10
```

В существующий `resilience4j.retry.instances` (рядом с `llmApi`) добавить:

```yaml
      alminasaApi:
        max-attempts: 3
        wait-duration: 2s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exception-predicate: ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaTransientFailurePredicate
```

(Класс предиката появится в Task 6 — yml-ссылка резолвится в рантайме, компиляции не мешает.)

- [ ] **Step 4: Компиляция**

```bash
cd backend && ./mvnw -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/api/ backend/src/main/resources/application.yml
git commit -m "feat(hadith): alminasa-конфигурация — properties, HttpClient с прокси, yml (Сессия 56)"
```

---

## Task 6: AlminasaEsClient — 4 fetch-метода + retry-предикат

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/api/AlminasaApiException.java`
- Create: `.../api/AlminasaTransientFailurePredicate.java`
- Create: `.../api/dto/AlminasaPage.java`
- Create: `.../api/AlminasaEsClient.java`
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/api/AlminasaEsClientStubIT.java`

- [ ] **Step 1: Написать падающий StubIT** (паттерн `ShamelaApiClientStubIT`: standalone, без Spring — здесь retry-прокси НЕ активен, проверяем форму запросов и парсинг)

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Standalone stub-IT клиента alminasa: форма запросов (путь, заголовки
 * Origin/Referer, тело ES Query DSL) и парсинг ответов на реальных
 * HAR-фикстурах. Без Spring — @Retry здесь не активен (см.
 * {@link AlminasaEsClientRetryIT} для retry-поведения через прокси).
 */
class AlminasaEsClientStubIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private AlminasaEsClient client;
    private final AtomicReference<String> fixtureToServe = new AtomicReference<>();
    private final AtomicReference<Integer> statusToServe = new AtomicReference<>(200);
    private record CapturedRequest(String path, Map<String, List<String>> headers, String body) {}
    private final ConcurrentLinkedQueue<CapturedRequest> captured = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.add(new CapturedRequest(
                    exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body));
            byte[] resp = fixture(fixtureToServe.get());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToServe.get(), resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();
        AlminasaProperties props = new AlminasaProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "https://alminasa.ai",
                "es-prod-euw1-", "-read",
                null, 5, 5,
                new AlminasaProperties.Crawl(100, 0, 25, 500, 10));
        client = new AlminasaEsClient(HttpClient.newHttpClient(), props, MAPPER);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = AlminasaEsClientStubIT.class.getResourceAsStream("/alminasa/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void fetchHadithPage_первая_страница_без_search_after() throws IOException {
        fixtureToServe.set("hadith-page.json");

        AlminasaPage page = client.fetchHadithPage(null, 100);

        CapturedRequest req = captured.poll();
        assertThat(req.path()).isEqualTo("/api/reactivesearchproxy/es-prod-euw1-hadith-12-read/_search");
        assertThat(req.headers().getFirst("Origin")).isEqualTo("https://alminasa.ai");
        assertThat(req.headers().getFirst("Referer")).isEqualTo("https://alminasa.ai/");
        assertThat(req.headers().getFirst("Content-Type")).isEqualTo("application/json");

        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("size").asInt()).isEqualTo(100);
        assertThat(body.path("sort").get(0).path("hadith_serial_id").path("order").asText())
                .isEqualTo("asc");
        assertThat(body.path("track_total_hits").asBoolean()).isTrue();
        assertThat(body.has("search_after")).isFalse();

        assertThat(page.totalHits()).isEqualTo(21);
        assertThat(page.hits()).hasSize(2);
        AlminasaHit first = page.hits().get(0);
        assertThat(first.id()).isEqualTo("146-1");
        assertThat(first.source().path("hadith_id").asText()).isEqualTo("146-1");
    }

    @Test
    void fetchHadithPage_resume_передаёт_search_after() throws IOException {
        fixtureToServe.set("hadith-page-empty.json");

        AlminasaPage page = client.fetchHadithPage(4242L, 50);

        JsonNode body = MAPPER.readTree(captured.poll().body());
        assertThat(body.path("search_after").get(0).asLong()).isEqualTo(4242L);
        assertThat(page.hits()).isEmpty();
    }

    @Test
    void fetchNarratorsByIds_terms_по_id() throws IOException {
        fixtureToServe.set("narrators.json");

        List<AlminasaHit> hits = client.fetchNarratorsByIds(List.of(5719L, 4698L));

        CapturedRequest req = captured.poll();
        assertThat(req.path()).isEqualTo("/api/reactivesearchproxy/es-prod-euw1-narrators-12-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        JsonNode terms = body.path("query").path("terms").path("id");
        assertThat(terms.get(0).asLong()).isEqualTo(5719L);
        assertThat(body.path("size").asInt()).isEqualTo(2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).id()).isEqualTo("5719");
    }

    @Test
    void fetchExplanationsByHadithIds_вложенный_terms() throws IOException {
        fixtureToServe.set("explanations.json");

        AlminasaPage page = client.fetchExplanationsByHadithIds(List.of("146-1"));

        CapturedRequest req = captured.poll();
        assertThat(req.path())
                .isEqualTo("/api/reactivesearchproxy/es-prod-euw1-hadith-explanation-12-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("query").path("terms").path("hadith.hadith_id").get(0).asText())
                .isEqualTo("146-1");
        assertThat(body.path("size").asInt()).isEqualTo(500);

        assertThat(page.hits().get(0).source().path("hadith").path("hadith_id").asText())
                .isEqualTo("146-1");
    }

    @Test
    void fetchRulingsByHadithIds_terms_по_hadith_id() throws IOException {
        fixtureToServe.set("rulings.json");

        AlminasaPage page = client.fetchRulingsByHadithIds(List.of("146-1"));

        CapturedRequest req = captured.poll();
        assertThat(req.path())
                .isEqualTo("/api/reactivesearchproxy/es-prod-euw1-rulings-12_v2-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("query").path("terms").path("hadith_id").get(0).asText())
                .isEqualTo("146-1");

        assertThat(page.hits().get(0).source().path("ruler").asText()).isEqualTo("البخاري");
    }

    @Test
    void не_2xx_бросает_AlminasaApiException_со_статусом() {
        fixtureToServe.set("hadith-page-empty.json");
        statusToServe.set(503);

        assertThatThrownBy(() -> client.fetchHadithPage(null, 10))
                .isInstanceOf(AlminasaApiException.class)
                .satisfies(e -> assertThat(((AlminasaApiException) e).statusCode()).isEqualTo(503));
    }
}
```

- [ ] **Step 2: Прогнать — FAIL (классов нет)**

```bash
cd backend && ./mvnw -Dit.test=AlminasaEsClientStubIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Exception + predicate + page-record**

`api/AlminasaApiException.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

/**
 * Ошибка обращения к alminasa ES-прокси. {@code statusCode}: HTTP-статус
 * при не-2xx ответе; {@code 0} — I/O-ошибка (transient, ретраится);
 * {@code -1} — прерывание потока (НЕ ретраится).
 */
public class AlminasaApiException extends RuntimeException {

    private final int statusCode;

    public AlminasaApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AlminasaApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
```

`api/AlminasaTransientFailurePredicate.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.util.function.Predicate;

/**
 * Resilience4j-предикат retry-инстанса {@code alminasaApi} (как
 * {@code LlmTransientFailurePredicate} у llmApi): ретраим только transient —
 * 5xx, 429, I/O (statusCode 0). 4xx и interrupt (-1) — не ретраим.
 */
public class AlminasaTransientFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof AlminasaApiException e) {
            return e.statusCode() == 0 || e.statusCode() == 429 || e.statusCode() >= 500;
        }
        return false;
    }
}
```

`api/dto/AlminasaPage.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api.dto;

import java.util.List;

/** Страница ES-ответа: точный total ({@code track_total_hits:true}) + хиты. */
public record AlminasaPage(long totalHits, List<AlminasaHit> hits) {
}
```

- [ ] **Step 4: Клиент**

`api/AlminasaEsClient.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Узкий HTTP-клиент открытого ES-прокси alminasa.ai (ADR-060, спека §A).
 * {@code POST {base}/api/reactivesearchproxy/{prefix}{index}{suffix}/_search},
 * обязательные Origin/Referer. Только 4 запроса краулера: страница hadith-12
 * (search_after по hadith_serial_id) и батчевые terms-выборки
 * narrators/explanations/rulings по id.
 *
 * <p>{@code @Retry(name="alminasaApi")} — transient-предикат
 * {@link AlminasaTransientFailurePredicate}. ВАЖНО: вызывать через
 * Spring-прокси (инжект), не self-invoke (регрессия Сессии 55 с llmApi).
 */
@Component
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaEsClient {

    private static final Logger log = LoggerFactory.getLogger(AlminasaEsClient.class);

    static final String HADITH_INDEX = "hadith-12";
    static final String NARRATORS_INDEX = "narrators-12";
    static final String EXPLANATION_INDEX = "hadith-explanation-12";
    static final String RULINGS_INDEX = "rulings-12_v2";

    private final HttpClient httpClient;
    private final AlminasaProperties props;
    private final ObjectMapper objectMapper;

    public AlminasaEsClient(HttpClient alminasaHttpClient,
                            AlminasaProperties props,
                            ObjectMapper objectMapper) {
        this.httpClient = alminasaHttpClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Страница корпуса hadith-12 по {@code hadith_serial_id} asc.
     *
     * @param afterSerialId курсор search_after (null — с начала корпуса)
     */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchHadithPage(Long afterSerialId, int size) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);
        body.set("query", objectMapper.createObjectNode()
                .set("match_all", objectMapper.createObjectNode()));
        body.putArray("sort").addObject()
                .set("hadith_serial_id", objectMapper.createObjectNode().put("order", "asc"));
        body.put("track_total_hits", true);
        if (afterSerialId != null) {
            body.putArray("search_after").add(afterSerialId);
        }
        return toPage(search(HADITH_INDEX, body));
    }

    /** Нарраторы по их numeric id (id рави из narrators[] хадис-дока). */
    @Retry(name = "alminasaApi")
    public List<AlminasaHit> fetchNarratorsByIds(Collection<Long> ids) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", ids.size());
        ArrayNode terms = body.putObject("query").putObject("terms").putArray("id");
        ids.forEach(terms::add);
        return toPage(search(NARRATORS_INDEX, body)).hits();
    }

    /** Шархи по hadith_id (terms по вложенному hadith.hadith_id). */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchExplanationsByHadithIds(Collection<String> hadithIds) {
        return fetchDependents(EXPLANATION_INDEX, "hadith.hadith_id", hadithIds);
    }

    /** Рулинги по hadith_id (все narrations_type — фильтрует маппер Плана 3). */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchRulingsByHadithIds(Collection<String> hadithIds) {
        return fetchDependents(RULINGS_INDEX, "hadith_id", hadithIds);
    }

    private AlminasaPage fetchDependents(String index, String termsField, Collection<String> hadithIds) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", props.crawl().dependentFetchSize());
        ArrayNode terms = body.putObject("query").putObject("terms").putArray(termsField);
        hadithIds.forEach(terms::add);
        body.put("track_total_hits", true);
        AlminasaPage page = toPage(search(index, body));
        if (page.totalHits() > page.hits().size()) {
            // dependent-fetch-size не вместил все доки батча — данные не потеряны
            // навсегда (re-crawl батча), но сигнал поднять size/уменьшить батч
            log.warn("alminasa {}: total={} > возвращено {} (dependent-fetch-size={}) — "
                            + "увеличь alminasa.crawl.dependent-fetch-size или уменьши dependent-batch-size",
                    index, page.totalHits(), page.hits().size(), props.crawl().dependentFetchSize());
        }
        return page;
    }

    private JsonNode search(String index, ObjectNode body) {
        URI uri = URI.create(props.baseUrl() + "/api/reactivesearchproxy/"
                + props.indexPrefix() + index + props.indexSuffix() + "/_search");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Origin", props.origin())
                .header("Referer", props.origin() + "/")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AlminasaApiException(response.statusCode(),
                        "alminasa ES вернул HTTP " + response.statusCode() + " на " + index);
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new AlminasaApiException(0, "alminasa ES I/O ошибка на " + index, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AlminasaApiException(-1, "alminasa ES запрос прерван", e);
        }
    }

    private AlminasaPage toPage(JsonNode response) {
        JsonNode hitsNode = response.path("hits");
        long total = hitsNode.path("total").path("value").asLong();
        List<AlminasaHit> hits = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            hits.add(new AlminasaHit(
                    hit.path("_id").asText(), hit.path("_source"), hit.path("sort")));
        }
        return new AlminasaPage(total, List.copyOf(hits));
    }
}
```

- [ ] **Step 5: Прогнать StubIT — PASS**

```bash
cd backend && ./mvnw -Dit.test=AlminasaEsClientStubIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` → `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/api/ backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/api/
git commit -m "feat(hadith): AlminasaEsClient — search_after страницы + terms-батчи, retry-предикат (Сессия 56)"
```

---

## Task 7: Retry lock-in IT (через Spring-прокси)

**Files:**
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/api/AlminasaEsClientRetryIT.java`

Зеркало `LlmClientRetryIT` (регрессия Сессии 55: self-invocation мимо прокси
молча убивает retry — фиксируем что у alminasa retry реально работает).

- [ ] **Step 1: Написать IT**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Lock-in IT: {@code @Retry(name="alminasaApi")} срабатывает через
 * Spring-прокси {@link AlminasaEsClient} (ср. {@code LlmClientRetryIT} —
 * регрессия Сессии 55 с self-invocation). Stub: 503, 503, 200.
 */
@SpringBootTest(properties = {
        // Быстрый retry — без override тест ждал бы exponential backoff 2s+4s.
        "resilience4j.retry.instances.alminasaApi.wait-duration=10ms",
        "resilience4j.retry.instances.alminasaApi.enable-exponential-backoff=false"
})
@Import(TestcontainersConfiguration.class)
class AlminasaEsClientRetryIT {

    private static HttpServer server;
    private static final AtomicInteger requestCount = new AtomicInteger(0);

    @Autowired
    private AlminasaEsClient client;

    @AfterAll
    static void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DynamicPropertySource
    static void stubBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("alminasa.base-url", () -> "http://127.0.0.1:" + ensureStub());
    }

    private static synchronized int ensureStub() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать stub HttpServer", e);
        }
        server.createContext("/", exchange -> {
            int n = requestCount.incrementAndGet();
            byte[] body;
            int status;
            if (n < 3) {
                status = 503;
                body = "{\"error\":\"overloaded\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                status = 200;
                try (InputStream in = AlminasaEsClientRetryIT.class
                        .getResourceAsStream("/alminasa/hadith-page-empty.json")) {
                    body = in.readAllBytes();
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    @Test
    void retry_503_503_200_успешен_после_трёх_запросов() {
        AlminasaPage page = client.fetchHadithPage(null, 10);

        assertThat(requestCount.get()).isEqualTo(3);
        assertThat(page.hits()).isEmpty();
        assertThat(page.totalHits()).isEqualTo(21); // total из фикстуры
    }
}
```

- [ ] **Step 2: Прогнать — PASS**

```bash
cd backend && ./mvnw -Dit.test=AlminasaEsClientRetryIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `Tests run: 1, Failures: 0` → `BUILD SUCCESS`. Если 1 запрос вместо 3 —
retry-инстанс не подхватился (проверить yml `alminasaApi` из Task 5).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/api/AlminasaEsClientRetryIT.java
git commit -m "test(hadith): lock-in retry alminasaApi через Spring-прокси (Сессия 56)"
```

---

## Task 8: AlminasaCrawlService — resumable цикл + executor

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/service/AlminasaCrawlConfig.java`
- Create: `.../service/AlminasaCrawlService.java`
- Create: `.../web/AlminasaCrawlConflictException.java` (нужен claimStart; контроллер — Task 9)
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/service/AlminasaCrawlServiceIT.java`

- [ ] **Step 1: Написать падающий IT**

```java
package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaCrawlConflictException;

/**
 * IT краулера на stub-сервере (HAR-фикстуры): полный проход, resume по
 * чекпоинту, pause на границе страницы, conflict при двойном старте,
 * перехват stale RUNNING-claim. {@code crawlLoop} зовётся синхронно —
 * детерминизм без Awaitility; @Async-обёртка тонкая.
 *
 * <p>БЕЗ @Transactional: краулер коммитит upsert'ы по ходу — тест чистит
 * таблицы руками в setUp.
 */
@SpringBootTest(properties = {
        "alminasa.crawl.delay-ms=0",
        "alminasa.crawl.page-size=100",
        "alminasa.crawl.dependent-batch-size=25",
        "alminasa.crawl.stale-timeout-minutes=10"
})
@Import(TestcontainersConfiguration.class)
class AlminasaCrawlServiceIT {

    private static HttpServer server;
    private static final ConcurrentLinkedQueue<String> hadithRequests = new ConcurrentLinkedQueue<>();

    @Autowired private AlminasaCrawlService crawlService;
    @Autowired private AmCrawlCheckpointDao checkpointDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DynamicPropertySource
    static void stubBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("alminasa.base-url", () -> "http://127.0.0.1:" + ensureStub());
    }

    /**
     * Stub: hadith-12 → page1 (2 хита) без search_after, пустая страница с
     * search_after; narrators/explanations/rulings → фикстуры.
     */
    private static synchronized int ensureStub() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать stub HttpServer", e);
        }
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-hadith-12-read/_search",
                exchange -> {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    hadithRequests.add(body);
                    serveFixture(exchange,
                            body.contains("search_after") ? "hadith-page-empty.json" : "hadith-page.json");
                });
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-narrators-12-read/_search",
                exchange -> serveFixture(exchange, "narrators.json"));
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-hadith-explanation-12-read/_search",
                exchange -> serveFixture(exchange, "explanations.json"));
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-rulings-12_v2-read/_search",
                exchange -> serveFixture(exchange, "rulings.json"));
        server.start();
        return server.getAddress().getPort();
    }

    private static void serveFixture(com.sun.net.httpserver.HttpExchange exchange, String name)
            throws IOException {
        byte[] body;
        try (InputStream in = AlminasaCrawlServiceIT.class.getResourceAsStream("/alminasa/" + name)) {
            body = in.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @BeforeEach
    void cleanState() {
        hadithRequests.clear();
        jdbcTemplate.update("DELETE FROM am_staging_hadith");
        jdbcTemplate.update("DELETE FROM am_staging_narrator");
        jdbcTemplate.update("DELETE FROM am_staging_explanation");
        jdbcTemplate.update("DELETE FROM am_staging_ruling");
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
    }

    @Test
    void полный_проход_стейджит_всё_и_завершает_чекпоинт() {
        crawlService.claimStart();
        crawlService.crawlLoop();

        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(cp.totalHits()).isEqualTo(21); // total из фикстуры
        assertThat(cp.fetchedCount()).isEqualTo(2);
        // курсор = max(serial) застейдженного
        Long maxSerial = jdbcTemplate.queryForObject(
                "SELECT MAX(hadith_serial_id) FROM am_staging_hadith", Long.class);
        assertThat(cp.lastSortValue()).isEqualTo(maxSerial);

        assertThat(count("am_staging_hadith")).isEqualTo(2);
        assertThat(count("am_staging_narrator")).isGreaterThanOrEqualTo(1);
        assertThat(count("am_staging_explanation")).isEqualTo(2);
        assertThat(count("am_staging_ruling")).isEqualTo(2);
        // raw — валидный jsonb с полным _source
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw->>'matn_with_tashkeel' FROM am_staging_hadith WHERE hadith_id = '146-1'",
                String.class)).isNotBlank();
        // вторая страница запрошена с search_after
        assertThat(hadithRequests).hasSize(2);
        assertThat(hadithRequests.stream().skip(1).findFirst().orElseThrow())
                .contains("search_after");
    }

    @Test
    void resume_продолжает_с_чекпоинта_не_с_нуля() {
        checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);
        checkpointDao.advance(AlminasaCrawlService.HADITH_INDEX_KEY, 9999L, 100);
        checkpointDao.markPaused(AlminasaCrawlService.HADITH_INDEX_KEY);

        crawlService.claimStart();
        crawlService.crawlLoop();

        // ПЕРВЫЙ же запрос — с search_after:[9999] → пустая страница → COMPLETED
        assertThat(hadithRequests).hasSize(1);
        assertThat(hadithRequests.peek()).contains("9999");
        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(cp.fetchedCount()).isEqualTo(100); // прогресс сохранён
    }

    @Test
    void pause_останавливает_на_границе_страницы() {
        crawlService.claimStart();
        crawlService.pause();
        crawlService.crawlLoop();

        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.PAUSED);
        // страница успела застейджиться перед паузой
        assertThat(count("am_staging_hadith")).isEqualTo(2);
        assertThat(hadithRequests).hasSize(1);
    }

    @Test
    void повторный_start_при_живом_RUNNING_конфликтует() {
        crawlService.claimStart();
        assertThatThrownBy(() -> crawlService.claimStart())
                .isInstanceOf(AlminasaCrawlConflictException.class);
    }

    @Test
    void stale_RUNNING_перехватывается() {
        crawlService.claimStart();
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET updated_at = now() - interval '1 hour' "
                        + "WHERE index_name = ?", AlminasaCrawlService.HADITH_INDEX_KEY);

        crawlService.claimStart(); // не бросает
        assertThat(checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow().status())
                .isEqualTo(AmCrawlStatus.RUNNING);
    }

    private long count(String table) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }
}
```

- [ ] **Step 2: Прогнать — FAIL (классов нет)**

```bash
cd backend && ./mvnw -Dit.test=AlminasaCrawlServiceIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Conflict-исключение**

`web/AlminasaCrawlConflictException.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.web;

/**
 * Старт краулинга alminasa при уже идущем (живой RUNNING-claim).
 * Маппится в 409 Conflict в {@code GlobalExceptionHandler}.
 */
public class AlminasaCrawlConflictException extends RuntimeException {

    public AlminasaCrawlConflictException() {
        super("Краулинг alminasa уже выполняется");
    }
}
```

- [ ] **Step 4: Executor config**

`service/AlminasaCrawlConfig.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single-thread executor краулера alminasa (паттерн AiEditConfig).
 * Один краулер за раз: core=max=1, queue=0; второй submit отбивается
 * AbortPolicy (но до него не доходит — claimStart() уже отдал 409).
 * @EnableAsync уже включён в AiEditConfig.
 */
@Configuration
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaCrawlConfig {

    @Bean("alminasaCrawlExecutor")
    public ThreadPoolTaskExecutor alminasaCrawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("alminasa-crawl-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 5: Сервис**

`service/AlminasaCrawlService.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaEsClient;
import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaProperties;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaRows;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaCrawlConflictException;

/**
 * Resumable краулер alminasa → am_staging_* (ADR-060, спека §A).
 *
 * <p>«hadith-first»: один цикл по hadith-12 (search_after по
 * hadith_serial_id), зависимые (нарраторы/шархи/рулинги) добираются
 * батчевыми terms по id текущей страницы — только проверенные HAR'ом
 * формы запросов, без сортировочных требований к зависимым индексам.
 *
 * <p>Чекпоинт на границе КАЖДОЙ страницы; upsert'ы идемпотентны →
 * resume после PAUSED/FAILED/рестарта переигрывает максимум одну
 * страницу. Состояние pause — in-memory volatile (single-instance);
 * рестарт backend'а оставляет RUNNING-строку — её перехватывает
 * stale-timeout (паттерн ai.edit.processing-timeout-minutes).
 *
 * <p>БЕЗ @Transactional вокруг цикла: каждая страница коммитится сама,
 * прогресс не теряется при падении (идемпотентность вместо отката).
 */
@Service
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaCrawlService {

    private static final Logger log = LoggerFactory.getLogger(AlminasaCrawlService.class);

    /** Ключ чекпоинта корпуса хадисов (generic-таблица — Планы 6+ добавят свои). */
    public static final String HADITH_INDEX_KEY = "hadith-12";

    private final AlminasaEsClient client;
    private final AmHadithStagingDao hadithDao;
    private final AmNarratorStagingDao narratorDao;
    private final AmExplanationStagingDao explanationDao;
    private final AmRulingStagingDao rulingDao;
    private final AmCrawlCheckpointDao checkpointDao;
    private final AlminasaProperties props;

    private volatile boolean pauseRequested;

    public AlminasaCrawlService(AlminasaEsClient client,
                                AmHadithStagingDao hadithDao,
                                AmNarratorStagingDao narratorDao,
                                AmExplanationStagingDao explanationDao,
                                AmRulingStagingDao rulingDao,
                                AmCrawlCheckpointDao checkpointDao,
                                AlminasaProperties props) {
        this.client = client;
        this.hadithDao = hadithDao;
        this.narratorDao = narratorDao;
        this.explanationDao = explanationDao;
        this.rulingDao = rulingDao;
        this.checkpointDao = checkpointDao;
        this.props = props;
    }

    /**
     * Claim RUNNING. Живой RUNNING → {@link AlminasaCrawlConflictException}
     * (409); stale RUNNING (updated_at старше stale-timeout — воркер умер)
     * перехватывается. IDLE/COMPLETED → старт с нуля (reset прогресса);
     * PAUSED/FAILED/stale → resume с last_sort_value.
     */
    public synchronized AmCrawlCheckpoint claimStart() {
        Optional<AmCrawlCheckpoint> existing = checkpointDao.find(HADITH_INDEX_KEY);
        if (existing.isPresent() && existing.get().status() == AmCrawlStatus.RUNNING) {
            OffsetDateTime staleBefore =
                    OffsetDateTime.now().minusMinutes(props.crawl().staleTimeoutMinutes());
            if (existing.get().updatedAt().isAfter(staleBefore)) {
                throw new AlminasaCrawlConflictException();
            }
            log.warn("alminasa crawl: stale RUNNING-claim (updated_at={}) — перехватываем",
                    existing.get().updatedAt());
        }
        boolean resetProgress = existing.isEmpty()
                || existing.get().status() == AmCrawlStatus.IDLE
                || existing.get().status() == AmCrawlStatus.COMPLETED;
        pauseRequested = false;
        return checkpointDao.upsertRunning(HADITH_INDEX_KEY, resetProgress);
    }

    /**
     * Асинхронная обёртка цикла. ВАЖНО: контроллер зовёт claimStart() и
     * crawlAsync() как ДВА вызова через Spring-прокси — self-invocation
     * обошёл бы @Async (регрессия Сессии 55 c @Retry).
     */
    @Async("alminasaCrawlExecutor")
    public void crawlAsync() {
        try {
            crawlLoop();
        } catch (Exception e) {
            log.error("alminasa crawl упал", e);
            checkpointDao.markFailed(HADITH_INDEX_KEY, abbreviate(e.toString()));
        }
    }

    /**
     * Синхронный цикл (package-visible для детерминированных IT). Чекпоинт
     * двигается на границе каждой страницы.
     */
    void crawlLoop() {
        // seed дедупликации нарраторов: что уже в staging — не перекачиваем
        Set<Long> stagedNarrators = new HashSet<>(narratorDao.findAllIds());
        while (true) {
            AmCrawlCheckpoint checkpoint = checkpointDao.find(HADITH_INDEX_KEY).orElseThrow();
            AlminasaPage page =
                    client.fetchHadithPage(checkpoint.lastSortValue(), props.crawl().pageSize());
            checkpointDao.setTotalHits(HADITH_INDEX_KEY, page.totalHits());
            if (page.hits().isEmpty()) {
                checkpointDao.markCompleted(HADITH_INDEX_KEY);
                log.info("alminasa crawl завершён: {} хадисов", checkpoint.fetchedCount());
                return;
            }

            List<AmHadithRow> rows = page.hits().stream().map(AlminasaRows::fromHadithHit).toList();
            hadithDao.upsertAll(rows);

            List<String> hadithIds = rows.stream().map(AmHadithRow::hadithId).toList();
            for (List<String> batch : partition(hadithIds, props.crawl().dependentBatchSize())) {
                rulingDao.upsertAll(client.fetchRulingsByHadithIds(batch).hits().stream()
                        .map(AlminasaRows::fromRulingHit).toList());
                explanationDao.upsertAll(client.fetchExplanationsByHadithIds(batch).hits().stream()
                        .map(AlminasaRows::fromExplanationHit).toList());
            }

            List<Long> newNarratorIds = collectNewNarratorIds(page.hits(), stagedNarrators);
            for (List<Long> batch : partition(newNarratorIds, props.crawl().dependentBatchSize())) {
                List<AmNarratorRow> narrators = client.fetchNarratorsByIds(batch).stream()
                        .map(AlminasaRows::fromNarratorHit).toList();
                narratorDao.upsertAll(narrators);
            }
            stagedNarrators.addAll(newNarratorIds);

            long lastSerial = rows.get(rows.size() - 1).hadithSerialId();
            checkpointDao.advance(HADITH_INDEX_KEY, lastSerial, rows.size());
            log.info("alminasa crawl: страница до serial={} (+{} хадисов, +{} рави)",
                    lastSerial, rows.size(), newNarratorIds.size());

            if (pauseRequested) {
                checkpointDao.markPaused(HADITH_INDEX_KEY);
                log.info("alminasa crawl: пауза на serial={}", lastSerial);
                return;
            }
            sleep(props.crawl().delayMs());
        }
    }

    /** Пауза на границе текущей страницы (no-op если краулер не идёт). */
    public void pause() {
        pauseRequested = true;
    }

    public Optional<AmCrawlCheckpoint> checkpoint() {
        return checkpointDao.find(HADITH_INDEX_KEY);
    }

    /** id рави из narrators[] страниц, которых ещё нет в staging. id — строки в источнике. */
    private static List<Long> collectNewNarratorIds(List<AlminasaHit> hits, Set<Long> staged) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AlminasaHit hit : hits) {
            for (JsonNode narrator : hit.source().path("narrators")) {
                long id = narrator.path("id").asLong(0);
                if (id > 0 && !staged.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String message) {
        return message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }
}
```

- [ ] **Step 6: Прогнать IT — PASS**

```bash
cd backend && ./mvnw -Dit.test=AlminasaCrawlServiceIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `Tests run: 5, Failures: 0, Errors: 0` → `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/service/ backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/web/ backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/service/
git commit -m "feat(hadith): AlminasaCrawlService — resumable hadith-first краулер с чекпоинтами (Сессия 56)"
```

---

## Task 9: Admin REST — start/pause/status

**Files:**
- Create: `backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/web/dto/AlminasaCrawlStatusResponse.java`
- Create: `.../web/AlminasaAdminController.java`
- Modify: `backend/src/main/java/ru/basnukaev/argumentmap/exception/GlobalExceptionHandler.java` (+1 handler, рядом с `handleSunnahDumpNotConfigured`)
- Test: `backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/web/AlminasaAdminControllerIT.java`

- [ ] **Step 1: Написать падающий IT** (паттерн `SunnahAdminControllerIT`: MockMvc + X-User-Id; `insertUser`-хелпер скопировать оттуда же — открой файл и возьми приватный метод + импорты)

```java
package ru.basnukaev.argumentmap.hadith.alminasa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;

/**
 * IT admin-endpoints краулера alminasa: ADMIN-only, 202 на start,
 * 409 на двойной старт, форма status-ответа. base-url указывает на
 * закрытый порт — async-краулер быстро падает в FAILED, на контракт
 * endpoint'ов это не влияет (детерминированный happy-path краулера —
 * в {@link ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlServiceIT}).
 */
@SpringBootTest(properties = {
        "alminasa.base-url=http://127.0.0.1:1",
        "alminasa.crawl.delay-ms=0"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AlminasaAdminControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AmCrawlCheckpointDao checkpointDao;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
        adminId = insertUser("alminasa-admin", UserRole.ADMIN);
        userId = insertUser("alminasa-user", UserRole.USER);
    }

    // insertUser: скопировать приватный хелпер из SunnahAdminControllerIT
    // (jdbcTemplate insert в users с ролью, вернуть UUID)

    @Test
    void start_не_админом_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void status_без_чекпоинта_отдаёт_IDLE_и_нулевые_счётчики() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/crawl/status")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.stagedHadiths").value(0))
                .andExpect(jsonPath("$.stagedNarrators").value(0))
                .andExpect(jsonPath("$.stagedExplanations").value(0))
                .andExpect(jsonPath("$.stagedRulings").value(0));
    }

    @Test
    void start_отдаёт_202_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void двойной_start_409() throws Exception {
        checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);

        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", containsString("alminasa-crawl-already-running")));
    }

    @Test
    void pause_отдаёт_200_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/pause")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
```

ВАЖНО: `@Transactional` на тесте откатит insertUser, но `start` запускает
async-поток с собственными транзакциями чекпоинта — в `setUp` чистим
`am_crawl_checkpoint` явно. Если async-поток флакует (запись чекпоинта после
отката теста) — убрать `@Transactional` и чистить users тоже руками, как в
`AlminasaCrawlServiceIT`.

- [ ] **Step 2: Прогнать — FAIL**

```bash
cd backend && ./mvnw -Dit.test=AlminasaAdminControllerIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: DTO**

`web/dto/AlminasaCrawlStatusResponse.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.time.OffsetDateTime;

import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;

/**
 * Статус краулинга alminasa: чекпоинт + счётчики staging-таблиц.
 * {@code status=IDLE} с нулями — краулинг ещё не запускался.
 */
public record AlminasaCrawlStatusResponse(
        String status,
        Long lastSortValue,
        long fetchedCount,
        Long totalHits,
        String error,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt,
        long stagedHadiths,
        long stagedNarrators,
        long stagedExplanations,
        long stagedRulings
) {

    public static AlminasaCrawlStatusResponse of(AmCrawlCheckpoint checkpoint,
                                                 long stagedHadiths,
                                                 long stagedNarrators,
                                                 long stagedExplanations,
                                                 long stagedRulings) {
        if (checkpoint == null) {
            return new AlminasaCrawlStatusResponse("IDLE", null, 0, null, null, null, null,
                    stagedHadiths, stagedNarrators, stagedExplanations, stagedRulings);
        }
        return new AlminasaCrawlStatusResponse(
                checkpoint.status().name(),
                checkpoint.lastSortValue(),
                checkpoint.fetchedCount(),
                checkpoint.totalHits(),
                checkpoint.error(),
                checkpoint.startedAt(),
                checkpoint.updatedAt(),
                stagedHadiths, stagedNarrators, stagedExplanations, stagedRulings);
    }
}
```

- [ ] **Step 4: Контроллер**

`web/AlminasaAdminController.java`:

```java
package ru.basnukaev.argumentmap.hadith.alminasa.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaCrawlStatusResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Admin-endpoints краулинга alminasa (План 2, ADR-060). ADMIN-only
 * (паттерн SunnahAdminController). Полная админка с каталогом сборников
 * и dry-run превью — План 5.
 */
@RestController
@RequestMapping("/api/v1/admin/alminasa")
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaAdminController {

    private final AlminasaCrawlService crawlService;
    private final AmHadithStagingDao hadithDao;
    private final AmNarratorStagingDao narratorDao;
    private final AmExplanationStagingDao explanationDao;
    private final AmRulingStagingDao rulingDao;

    public AlminasaAdminController(AlminasaCrawlService crawlService,
                                   AmHadithStagingDao hadithDao,
                                   AmNarratorStagingDao narratorDao,
                                   AmExplanationStagingDao explanationDao,
                                   AmRulingStagingDao rulingDao) {
        this.crawlService = crawlService;
        this.hadithDao = hadithDao;
        this.narratorDao = narratorDao;
        this.explanationDao = explanationDao;
        this.rulingDao = rulingDao;
    }

    /**
     * Запуск/resume краулинга. 202 + текущий статус; 409 если уже идёт.
     * claimStart() и crawlAsync() — два вызова через Spring-прокси
     * (self-invocation обошёл бы @Async).
     */
    @PostMapping("/crawl/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaCrawlStatusResponse start(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        crawlService.claimStart();
        crawlService.crawlAsync();
        return status(currentUserId);
    }

    /** Пауза на границе текущей страницы (мягкая — чекпоинт сохраняется). */
    @PostMapping("/crawl/pause")
    public AlminasaCrawlStatusResponse pause(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        crawlService.pause();
        return status(currentUserId);
    }

    /** Чекпоинт + счётчики staging-таблиц (поллинг прогресса). */
    @GetMapping("/crawl/status")
    public AlminasaCrawlStatusResponse status(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return AlminasaCrawlStatusResponse.of(
                crawlService.checkpoint().orElse(null),
                hadithDao.count(),
                narratorDao.count(),
                explanationDao.count(),
                rulingDao.count());
    }

    private static void requireAdmin(UUID currentUserId) {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(currentUserId);
        }
    }
}
```

Сверь `requireAdmin` с актуальным телом в `SunnahAdminController` (строки
~209-212) — если сигнатура `AdminOnlyException` другая, скопируй точно оттуда.

- [ ] **Step 5: Handler 409 в GlobalExceptionHandler**

Рядом с `handleSunnahDumpNotConfigured` (после импорта
`ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaCrawlConflictException`):

```java
    @ExceptionHandler(AlminasaCrawlConflictException.class)
    public ProblemDetail handleAlminasaCrawlConflict(AlminasaCrawlConflictException ex) {
        return problem(HttpStatus.CONFLICT,
                "Краулинг alminasa уже идёт", "alminasa-crawl-already-running",
                ex.getMessage());
    }
```

- [ ] **Step 6: Прогнать IT — PASS**

```bash
cd backend && ./mvnw -Dit.test=AlminasaAdminControllerIT -DfailIfNoTests=false -Dsurefire.skip=true verify
```

Expected: `Tests run: 5, Failures: 0, Errors: 0` → `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/web/ backend/src/main/java/ru/basnukaev/argumentmap/exception/GlobalExceptionHandler.java backend/src/test/java/ru/basnukaev/argumentmap/hadith/alminasa/web/
git commit -m "feat(hadith): admin-endpoints краулинга alminasa — start/pause/status (Сессия 56)"
```

---

## Task 10: Документация + регенерация frontend-типов

**Files:**
- Modify: `docs/api-contract.md` (новая секция alminasa admin рядом с sunnah-секцией, в её формате)
- Modify: `docs/gotchas.md` (новая запись)
- Modify: `docs/architecture.md` (дополнить hadith-раздел)
- Regen: `frontend/src/shared/api/types.ts`

- [ ] **Step 1: api-contract.md** — добавить рядом с sunnah-admin секцией, в том же формате документа:

```markdown
### Alminasa crawl admin (План 2, ADR-060) — ADMIN-only

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/admin/alminasa/crawl/start` | Запуск/resume краулинга корпуса alminasa → `am_staging_*`. 202 + статус; 409 (`alminasa-crawl-already-running`) если уже идёт; stale RUNNING (>10 мин без heartbeat) перехватывается |
| POST | `/api/v1/admin/alminasa/crawl/pause` | Мягкая пауза на границе страницы (чекпоинт сохраняется). 200 + статус |
| GET | `/api/v1/admin/alminasa/crawl/status` | Чекпоинт + счётчики staging. 200 |

`AlminasaCrawlStatusResponse`: `status` (IDLE/RUNNING/PAUSED/FAILED/COMPLETED),
`lastSortValue` (курсор hadith_serial_id), `fetchedCount`, `totalHits`, `error`,
`startedAt`, `updatedAt`, `stagedHadiths`, `stagedNarrators`, `stagedExplanations`,
`stagedRulings`.
```

- [ ] **Step 2: gotchas.md** — новая запись:

```markdown
## alminasa.ai — открытый ES-прокси: контракт и ловушки

- Эндпоинт `POST https://alminasa.ai/api/reactivesearchproxy/es-prod-euw1-{index}-read/_search`
  (префикс/суффикс обязательны). Сервер проверяет только `Origin`/`Referer:
  https://alminasa.ai` — クлиент проставляет их сам (`AlminasaEsClient`).
- Индексы датированы (`...12.2024-08-24-...`) — контракт может молча смениться.
  Поэтому staging хранит полный `_source` в raw jsonb: пере-маппинг без пере-краулинга.
- `narrators[].id` в hadith-доках — СТРОКА (`"4698"`); у narrator-дока в `_source`
  НЕТ поля `id` — numeric id берётся из ES `_id` хита.
- Сайт пагинирует `from+size` — упирается в ES-лимит 10k. Наш краулер — `search_after`
  по `hadith_serial_id` (глобально уникален, UNIQUE-индекс в staging это сторожит).
- Вкладки علل/غريب — индексы `hadith-commentary-12`/`chains-*-12`/`ambiguous-12`:
  контракты НЕ сняты в HAR (нет живых запросов). Перед Планом 6 снять свежий HAR
  с кликами по этим вкладкам.
- Rate-limit заголовков нет (CDN-кэш s-maxage=86400), но краулер консервативен:
  `alminasa.crawl.delay-ms=1000` между страницами.
```

(Опечатку «クлиент» при вставке поправить на «клиент» — артефакт примера.)

- [ ] **Step 3: architecture.md** — в hadith-разделе (где описаны hd_*/alminasa-колонки из Плана 1) добавить абзац:

```markdown
**Краулер alminasa (План 2, ADR-060).** `hadith/alminasa/`: `AlminasaEsClient`
(узкий HTTP-клиент ES-прокси, retry `alminasaApi`) → `AlminasaCrawlService`
(«hadith-first» resumable цикл: страница hadith-12 по `search_after`, зависимые
narrators/explanations/rulings — батчевыми `terms` по id страницы) →
`am_staging_*` (raw jsonb + горячие колонки, идемпотентный upsert по природным
ключам) + `am_crawl_checkpoint` (RUNNING/PAUSED/FAILED/COMPLETED, граница
страницы, stale-takeover). Управление: `/api/v1/admin/alminasa/crawl/*`.
Маппинг staging → hd_* — План 3.
```

- [ ] **Step 4: Регенерация frontend-типов** (backend должен бежать с миграцией 72)

```bash
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &
until curl -sf http://localhost:9090/actuator/health > /dev/null; do sleep 2; done
cd ../frontend && npm run generate-api && npx tsc --noEmit -p tsconfig.app.json
```

Expected: `types.ts` обновлён (alminasa-эндпоинты появились), tsc clean.

- [ ] **Step 5: Commit**

```bash
git add docs/api-contract.md docs/gotchas.md docs/architecture.md frontend/src/shared/api/types.ts
git commit -m "docs: api-contract/gotchas/architecture — alminasa краулер + regen types (Сессия 56)"
```

---

## Task 11: Полный verify — граница фазы

- [ ] **Step 1: Полный backend-прогон** (логическая граница фазы — единственный full verify плана)

```bash
cd backend && ./mvnw verify
```

Expected: `BUILD SUCCESS`, все существующие + ~21 новых тестов зелёные.

- [ ] **Step 2: Если что-то красное** — чинить по `superpowers:systematic-debugging`, НЕ комментировать тесты. Учесть gotcha «Testcontainers context-cache flake» (connection refused к TC-порту = флак eviction, перезапустить).

- [ ] **Step 3: Фиксация мелочей** (если правки были)

```bash
git add -A && git commit -m "fix(hadith): правки по итогам полного verify Плана 2 alminasa (Сессия 56)"
```

(Если правок не было — коммита нет.)

---

## Вне плана (для оркестратора, не для исполнителей)

- **Live smoke против реального alminasa.ai** — решение Абдулы (корп-прокси/WSL2
  DNS могут блокировать; есть `alminasa.http-proxy`). Не входит в задачи плана.
- roadmap/progress/SESSION_START_PROMPT — обновляются при handoff сессии.
- План 3 (маппер staging→hd_*) — отдельный план-документ.

## Self-Review notes (применено при написании)

1. **Spec coverage §A:** ES-клиент ✓ (Task 6), фоновый resumable job ✓ (Task 8),
   search_after-пагинация ✓, чекпоинт ✓ (Tasks 2/4), raw JSONB ✓, rate-limit
   delay + retry ✓ (Tasks 5/6/7), корп-прокси ✓ (Task 5), IT на HAR-фикстурах ✓
   (Tasks 1/6/7/8/9). Чекпоинт generic по index_name (спека: «(index, collection,
   last_sort)») — collection-измерение НЕ нужно при глобальном hadith-first
   обходе; admin-каталог Плана 5 возьмёт прогресс по сборникам из
   `count(*) group by book_id` стейджинга.
2. **Отличие от спеки:** `_msearch` не используется (одиночные `_search` c
   `terms` проще и покрывают батчинг); `@ConditionalOnProperty` default-on
   (публичный прокси, секретов нет — у sunnah gate был из-за обязательного
   MySQL-дампа).
3. **Type consistency:** `AlminasaHit(id, source, sort)` единый для фабрики
   (Task 3) и клиента (Task 6); `AmCrawlStatus` вложен в `AmCrawlCheckpoint`;
   `HADITH_INDEX_KEY` public — используется IT Task 8/9.
