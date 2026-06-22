# Спека: Курация данных через overlay-таблицу `hd_field_overrides`

**Дата:** 2026-06-18
**Статус:** DRAFT (архитектура; ADR-065 ниже)
**Трек:** «Курация данных» = P0-1 аудита (overwrite-protection) + FB-5 фидбека
(править/скрывать любые данные hd_*-сущностей, не трогая первоисточник).
**Зависимости:** ADR-043 (RBAC + audit_log), ADR-060 (alminasa = единственный
источник), ADR-061 (hd_narrator_commentaries), ADR-062 (hadith_grades — отдельная
курируемая ось), ADR-063 (двух-осевая таксономия: status + authenticity).
**Источники:** `PROD-READINESS-AUDIT.md` §2 (manual-edit карта), §3 (реимпорт
перезапись, P0-1); `docs/specs/2026-06-18-phase2-feedback.md` FB-5.

---

## 1. Контекст и цели

### 1.1 Проблема (две сшитые)

**P0-1 (overwrite):** реимпорт alminasa молча затирает любые ручные правки.
- `AlminasaHadithMapper.mapHadith` (`AlminasaHadithMapper.java:153-221`):
  `hadithRepository.update(hadith)` перезаписывает 13 колонок хадиса
  (`collection_id, primary_number, normalized_matn, status, source_id,
  metadata, external_source, external_id, hadith_type, chapter_ar,
  sub_chapter_ar, full_text_ar, authenticity`), затем **delete-recreate**
  всех 6 сателлитов (matns/editions/sanads/crossrefs/rulings/explanations,
  строки `206-211` delete, `213-218` insert). Сохраняются только `id`,
  `created_at`, `source_id`.
- `AlminasaNarratorMapper.mapNarrator` (`AlminasaNarratorMapper.java:91-148`):
  `narratorRepository.update` перезаписывает 20 колонок рави
  (`reliability_grade, tabaqa, grade_text, name_ar, …`); relations и
  commentaries — delete-recreate (`recreateRelations:248`,
  `recreateNarratorCommentaries:261`). Survive только `id`, `authority_id`,
  `transmitted_count_cached`, `created_at`.
- **Особо острый случай:** `insertMatn` пересоздаёт строку матна с
  `UUID.randomUUID()` и `text_ru=NULL, text_en=NULL`
  (`AlminasaHadithMapper.java:305-312`) → даже C9-перевод (PATCH
  `/hadith/matns/{id}/translation`) уничтожается, причём вместе с `matnId`,
  по которому правили.
- Механизма «do-not-overwrite» в схеме/коде **нет вообще** (audit §3.3).

**FB-5 (три оси):** админу нужно
- (a) **править** любые данные любой hd_*-сущности (фикс ошибок импорта:
  authenticity, reliability/tabaqa/grade_text рави, иснады, рулинги…);
- (b) **скрывать/показывать** участки данных без удаления (модерация:
  экстремистские данные, имена/мнения заблудших учёных);
- (c) **гарантия неизменности первоисточника** — текст матна
  (`full_text_ar`/`normalized_matn`/`hd_matns.text_ar`), аят, текст
  цитаты-источника **нельзя** менять (можно скрывать вторичное — вердикты,
  commentary-цитаты, имена — но не первоисточник).

### 1.2 Почему overlay (выбран)

Та же таблица решает все три оси FB-5 + P0-1 одним механизмом:
override-значение (правка), `hidden`-флаг (модерация), `edited_by/at` +
`reason` (аудит). Импорт пишет hd_* «как есть» — **import-логику не трогаем
вовсе** → правки автоматически переживают реимпорт, т.к. живут в отдельной
таблице, читаемой на слое сериализации DTO.

Это тот же доказавший себя паттерн, что `hadith_grades` (ADR-062): отдельная
таблица курации поверх импортного корпуса, которую маппер не трогает.
Разница: `hadith_grades` — узкая ось (scholar-атрибутированные оценки,
завязана на citation-граф через `source_id`); overlay — generic слой над
любым полем любой сущности.

### 1.3 Отвергнутые альтернативы (кратко; полно — ADR-065)

- **(B) Колонки-локи `*_locked boolean` + условный UPDATE** (`SET col = CASE
  WHEN col_locked THEN col ELSE ? END`). Отвергнуто: N колонок × M таблиц
  раздувает мапперы и каждую миграцию; не решает hide/show (нужны ещё
  `*_hidden` колонки); правка трогает import-write-path (риск регрессии
  идемпотентности delete-recreate). Не масштабируется на сателлиты, которые
  delete-recreate'ятся (lock-колонка исчезнет вместе со строкой).
- **(C) Merge-стратегия (upsert сателлитов по природному ключу с сохранением
  правленых колонок).** Отвергнуто как генеральное решение: требует
  природного ключа на каждом сателлите (у matn — `hadith_id+printed_number`,
  у ruling — композит из 5 полей, у explanation — kind+esId…), хрупко при
  смене источника; не решает hide/show и не даёт аудита. **НО** для перевода
  матна это минимальный быстрый фикс (см. фаза P0-1a, §9) — допустимо как
  промежуток до миграции matn-перевода в overlay.

**Принцип:** overlay — generic; первоисточник защищается **whitelist'ом**
(§5), а не схемой (текстовые поля просто не входят в editable-набор).

---

## 2. Схема `hd_field_overrides`

### 2.1 Колонки

| Колонка | Тип | Null | Семантика |
|---|---|---|---|
| `id` | uuid PK | no | `UUID.randomUUID()` |
| `entity_table` | varchar(40) | no | имя таблицы: `hd_hadiths`, `hd_narrators`, `hd_sanads`, `hd_sanad_narrators`, `hd_rulings`, `hd_explanations`, `hd_narrator_commentaries`, `hd_matns`. CHECK whitelist (mirror enum `OverrideEntity`). |
| `entity_id` | uuid | no | PK правимой строки. Для `hd_sanad_narrators` (композитный PK `sanad_id+position`) — `sanad_id`, а `field_name` кодирует позицию (см. §5 примечание). |
| `field_name` | varchar(60) | no | snake_case-колонка ИЛИ синтетический ключ записи-уровня `__record__` (hide всей строки). |
| `override_value` | text | yes | новое значение (текстом; каст на apply — §3.4). NULL допустим, если строка несёт только `hidden=true` (скрытие без замены) ИЛИ если правка явно выставляет поле в NULL — различение через `is_null_override` ниже. |
| `is_null_override` | boolean | no, default false | true → apply ставит поле в `null` (отличает «правка в NULL» от «нет override-значения, только hide»). |
| `hidden` | boolean | no, default false | true → на чтении поле/запись отдаётся как скрытое (§4). |
| `edited_by` | uuid | no | FK `users(id)` ON DELETE RESTRICT (кто правил — нельзя осиротить аудит). |
| `edited_at` | timestamptz | no, default now() | |
| `reason` | text | yes | обоснование. **Обязателен на сервисном уровне для `hidden=true`** (модерация — кто/почему скрыл); для правок опционален. |

### 2.2 Индексы и ограничения

- **PK:** `id`.
- **UNIQUE `(entity_table, entity_id, field_name)`** — одна правка на поле
  записи (upsert-семантика; повторный PATCH обновляет ту же строку).
- **INDEX `idx_hd_overrides_lookup (entity_table, entity_id)`** — apply-слой
  читает ВСЕ overrides записи одним запросом (batch по списку id — `IN`).
- **CHECK** `entity_table IN (...)` — whitelist таблиц.
- **CHECK** `override_value IS NOT NULL OR is_null_override OR hidden` —
  строка должна нести хоть что-то (значение, явный NULL, или скрытие).
- **FK** `edited_by → users(id)` ON DELETE RESTRICT.

> Сознательно **без FK на `entity_id`** — polymorphic ref на 8 таблиц;
> целостность поддерживается на уровне сервиса (валидация существования
> строки при записи override) + cleanup-проход (orphan override после
> удаления записи — редкость, append-only корпус; janitor по образцу
> audit-retention опционален, в backlog).

### 2.3 Liquibase-миграция

Файл: `backend/src/main/resources/db/changelog/changes/20260618-78-hd-field-overrides.xml`
(следующий после `20260617-77`; формат `YYYYMMDD-NN`). Регистрация в
`db.changelog-master.xml` после строки 84 (include 77). Author `Abdula Basnukaev`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!--
        Курация данных (ADR-065): overlay-таблица правок и hide-флагов поверх
        импортного корпуса hadith-домена. Импорт alminasa hd_* не трогаем —
        правки живут здесь и накладываются на ЧТЕНИИ (apply-слой), потому
        переживают delete-recreate реимпорта.

        Решает P0-1 (overwrite-protection) + FB-5 (править/скрывать любые
        вторичные данные, первоисточник защищён whitelist'ом в сервисе).

        entity_table — whitelist 8 hd_*-таблиц. field_name — колонка или
        '__record__' (hide всей строки). override_value текстом, каст в тип
        поля на apply. hidden — модерация без удаления. reason обязателен
        для hidden (на сервисе). UNIQUE(entity_table,entity_id,field_name).
    -->
    <changeSet id="20260618-78-hd-field-overrides" author="Abdula Basnukaev">
        <comment>Курация данных: hd_field_overrides (override-значения + hide-флаги)</comment>
        <createTable tableName="hd_field_overrides">
            <column name="id" type="UUID">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="entity_table" type="VARCHAR(40)">
                <constraints nullable="false"/>
            </column>
            <column name="entity_id" type="UUID">
                <constraints nullable="false"/>
            </column>
            <column name="field_name" type="VARCHAR(60)">
                <constraints nullable="false"/>
            </column>
            <column name="override_value" type="TEXT"/>
            <column name="is_null_override" type="BOOLEAN" defaultValueBoolean="false">
                <constraints nullable="false"/>
            </column>
            <column name="hidden" type="BOOLEAN" defaultValueBoolean="false">
                <constraints nullable="false"/>
            </column>
            <column name="edited_by" type="UUID">
                <constraints nullable="false"
                             foreignKeyName="fk_hd_overrides_user"
                             references="users(id)"
                             deleteCascade="false"/>
            </column>
            <column name="edited_at" type="TIMESTAMPTZ" defaultValueComputed="now()">
                <constraints nullable="false"/>
            </column>
            <column name="reason" type="TEXT"/>
        </createTable>

        <addUniqueConstraint
                tableName="hd_field_overrides"
                columnNames="entity_table, entity_id, field_name"
                constraintName="hd_field_overrides_unique"/>

        <createIndex tableName="hd_field_overrides" indexName="idx_hd_overrides_lookup">
            <column name="entity_table"/>
            <column name="entity_id"/>
        </createIndex>

        <sql>
            ALTER TABLE hd_field_overrides ADD CONSTRAINT hd_field_overrides_table_check
                CHECK (entity_table IN (
                    'hd_hadiths','hd_narrators','hd_sanads','hd_sanad_narrators',
                    'hd_rulings','hd_explanations','hd_narrator_commentaries','hd_matns'));
            ALTER TABLE hd_field_overrides ADD CONSTRAINT hd_field_overrides_payload_check
                CHECK (override_value IS NOT NULL OR is_null_override OR hidden);
        </sql>

        <rollback>
            <dropTable tableName="hd_field_overrides"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

---

## 3. Apply-слой (КЛЮЧЕВОЕ решение)

### 3.1 Текущие read-пути hadith-домена (карта целей apply)

DTO собираются **прямо в контроллерах** из доменных records (без сервис-слоя
сборки). Точки, где hd_* → DTO:

| Read-путь | Файл:строка | Что отдаёт | Сущности в payload |
|---|---|---|---|
| `GET /hadiths/{id}/detail` | `HadithController.java:175-253` | `HadithDetailResponse` | hd_hadiths + matns + sanads(+narrator-links) + grades + editions + rulings + explanations + crossrefs |
| `GET /hadiths` (list) | `HadithController.java:109-128` | `PagedResponse<HadithResponse>` | hd_hadiths (+ matn preview) |
| `GET /hadiths/{id}` | `HadithController.java:130-135` | `HadithResponse` | hd_hadiths |
| `GET /hadiths/{id}/sanad-graph`, `/turuq-graph` | `SanadGraphService.buildGraph/buildTuruqGraph` (`:112/:129`) | `SanadGraphResponse` | hd_narrators (узлы), hd_sanad_narrators (рёбра) |
| `GET /hadiths/{id}/sibling-matns` | `HadithController.java:420-454` | `List<SiblingMatnDto>` | hd_matns параллельных передач |
| `GET /narrators` (list) | `NarratorController.java:56-72` | `PagedResponse<NarratorResponse>` | hd_narrators |
| `GET /narrators/{id}` | `NarratorController.java:74-87` | `NarratorResponse` + relations + commentaries | hd_narrators + hd_narrator_relations + hd_narrator_commentaries |
| `GET /narrators/{id}/transmitted` | `NarratorController.java:94-109` | `PagedResponse<HadithResponse>` | hd_hadiths |

**Вывод:** apply нельзя сунуть в один RowMapper — payload собирается из 8
репозиториев в контроллере. Per-repository apply → дублирование в каждом
`findById/findByHadithId/findPage` (≈15 методов). Нужен **централизованный
декоратор, работающий на уже собранном DTO**.

### 3.2 Дизайн: `OverrideApplyService` (декоратор на DTO)

Новый сервис `hadith/curation/service/OverrideApplyService` + repository
`OverrideRepository`. Контроллер собирает DTO как сейчас, затем **прогоняет
DTO через декоратор** перед возвратом.

Ядро — **рефлексивно-нейтральный, типобезопасный применитель** на основе
явной декларации полей. Не reflection по DTO (хрупко, медленно): apply
работает на уровне ДОМЕННЫХ records ДО маппинга в DTO — там, где значения
типизированы и колонки известны. Точка вставки — **между fetch домена и
построением DTO** в контроллере, одним вызовом на тип сущности.

```java
// hadith/curation/service/OverrideApplyService.java
@Service
public class OverrideApplyService {

    private final OverrideRepository repo;

    /** Один батч-fetch overrides для набора записей одной таблицы. */
    public OverrideSet load(OverrideEntity table, Collection<UUID> ids) {
        // SELECT * FROM hd_field_overrides
        //   WHERE entity_table = ? AND entity_id IN (?)
        // → Map<UUID, List<FieldOverride>>; пусто → NO_OP (без аллокаций)
    }

    /** Применить overrides к ОДНОЙ доменной записи hd_hadiths → новый Hadith. */
    public Hadith apply(Hadith h, OverrideSet ov) {
        var rec = ov.forEntity(h.id());
        if (rec.isEmpty()) return h;
        return new Hadith(
            h.id(), h.collectionId(),
            ov.applyInt(rec, "primary_number", h.primaryNumber()),
            h.normalizedMatn(),                                  // PROTECTED — не трогаем
            ov.applyStr(rec, "status", h.status()),
            h.sourceId(), h.metadata(), h.createdAt(),
            h.externalSource(), h.externalId(),
            ov.applyStr(rec, "hadith_type", h.hadithType()),
            ov.applyStr(rec, "chapter_ar", h.chapterAr()),
            ov.applyStr(rec, "sub_chapter_ar", h.subChapterAr()),
            h.fullTextAr(),                                      // PROTECTED
            ov.applyStr(rec, "authenticity", h.authenticity()));
    }
    // analogous: apply(Narrator), apply(HadithRuling), apply(HadithExplanation),
    //            apply(NarratorCommentary), apply(Matn), apply(Sanad/SanadNarrator)
}
```

**Почему apply на доменном record, а не на DTO:**
- Колонки = поля record'а 1:1 (snake_case `field_name` ↔ camelCase accessor —
  однозначно). Тип поля известен компилятору → каст в `applyInt/applyStr/…`
  безопасен.
- Один `apply(Hadith)` покрывает ВСЕ пути, отдающие хадис (detail, list,
  getOne, transmitted, sibling) — вызывается в `findById`/после `findPage`.
- DTO остаётся тупым проектором record'а (как сейчас) — не размазываем
  override-логику по 8 DTO-мапперам.

### 3.3 Где именно вызывать (минимум точек)

Два варианта размещения вызова. **Выбран Вариант 1 (репозиторный fold)** для
single-entity путей + явный декоратор для bundled detail:

- **Single-entity репозитории** (`HadithRepository.findById`,
  `NarratorRepository.findById`, `findPage`): внедрить `OverrideApplyService`
  и применять в самом репозитории — `findById` → `apply(h, load(...))`,
  `findPage` → батч `load` по списку id, затем `map(apply)`. Это покрывает
  list/getOne/transmitted/sibling «бесплатно» (все они идут через эти методы).
- **Bundled detail** (`getDetail`, `NarratorController.getOne`) дополнительно
  гоняет сателлиты (rulings/explanations/commentaries/matns/sanad-links).
  Здесь декоратор вызывается в контроллере на списках сателлитов:
  `rulings.stream().map(r -> overrideApply.apply(r, rulingOv))`. Один
  `load(HD_RULINGS, rulingIds)` на тип — без N+1.

> **Трейд-офф зафиксирован:** репозиторный fold делает apply «невидимым»
> (любой новый read-путь хадиса/рави получает overrides автоматически) ценой
> того, что репозиторий перестаёт быть чистым «как в БД». Это осознанно: для
> данных hadith-домена «как видит пользователь» = «БД + overlay» всегда;
> raw-доступ нужен только маппёру импорта, который ходит мимо
> (`findByExternalId` — для upsert берёт raw, overrides не применяет, что
> ПРАВИЛЬНО: импорт сравнивает/пишет базовый слой). → `findByExternalId`
> **НЕ** применяет overrides; `findById`/`findPage` — применяют. Зафиксировать
> явным javadoc на обоих.

### 3.4 Безопасный каст `override_value` (text) → тип поля

`override_value` хранится текстом. На apply кастится в тип целевого поля:

```java
// внутри OverrideSet
String applyStr(rec, field, base)  → override присутствует ? value : base
                                      (для String/enum-as-String: authenticity,
                                       status, hadith_type, reliability_grade,
                                       name_ar, grade_text, ruling_text, …)
Integer applyInt(rec, field, base) → parse; не парсится → лог WARN + base
                                      (НЕ роняем read; правка-битьё видна в логе)
```

Правила безопасности:
- **enum-поля** (`authenticity` ∈ SAHIH/HASAN/DAIF/MAUDU; `status`;
  `reliability_grade`; `chain_grade`; explanation `kind`): валидация
  **на ЗАПИСИ override** (PATCH) против того же whitelist'а, что CHECK
  constraint в схеме hd_*. На apply каста нет — строка уже валидный
  enum-литерал. Если БД-значение всё же невалидно (ручной SQL) → отдаём как
  есть (фронт игнорирует неизвестный бейдж, см. `authenticityClass` default).
- **числовые** (`primary_number`, `page`, `volume`, `year_death_hijri`,
  `ruler_death_year`, `cnt`, `position`): `Integer.parseInt`; fail → WARN +
  базовое значение (apply не должен ронять detail из-за битой правки).
- **boolean** (`is_primary`, `primary_chain`): `"true"/"false"`.
- **is_null_override=true** → поле в null независимо от `override_value`.
- **hidden=true** → §4 (поле→null/маркер, запись→исключается).

Каст-помощники — package-private, покрыты unit-тестами (битый int → base +
WARN; невалидный enum на ЗАПИСИ → 400, не на apply).

---

## 4. Семантика `hidden`

Два уровня, кодируются `field_name`:

### 4.1 Поле-уровень — `hidden=true` на конкретной колонке
Apply отдаёт это поле как `null` (или маркер — см. ниже). Примеры: скрыть
`grade_text` рави (спорный джарх), `ruler_name` одиозного «учёного» в
рулинге, оставив сам вердикт.

### 4.2 Запись-уровень — `field_name='__record__'`, `hidden=true`
Apply **исключает всю запись** из списка-сателлита (ruling/commentary/
explanation/sanad-link/matn-вариация целиком не отдаётся). Реализация: при
сборке списка в контроллере `.filter(x -> !ov.isRecordHidden(x.id()))`.

### 4.3 Как фронт показывает «скрыто администратором»

Два режима, **по типу сущности** (решение продукта):
- **Модерация (default, «просто отсутствует»):** скрытая запись/поле не
  приходит в payload вообще (apply вырезал). Фронт ничего не знает —
  чистое сокрытие (имена/мнения заблудших, экстремизм). Это безопаснее:
  нет «приманки» вида «здесь что-то скрыто».
- **Прозрачное (опционально, для не-чувствительных правок):** payload несёт
  плоский флаг — DTO сателлита получает доп. поле `hidden: boolean`
  (для записи-уровня) ИЛИ значение поля = маркер. Фронт рисует пилюлю
  «скрыто администратором» (i18n `hadith.curation.hidden_by_admin`).

**Решение:** на старте — **режим «просто отсутствует»** (проще, безопаснее,
не требует менять каждый сателлит-DTO). Прозрачный индикатор — фаза-расширение
для админ-вида (ADMIN видит скрытое с маркером, чтобы мочь раскрыть; обычный
читатель — не видит вообще). Различение по роли запрашивающего: ADMIN-режим
apply передаёт `revealHidden=true` → hidden-записи приходят с флагом
`hiddenByAdmin=true` вместо вырезания. См. §8 (admin reveal-toggle).

> **Первоисточник + hidden:** скрывать первоисточник (`full_text_ar`,
> `text_ar`, `normalized_matn`) **запрещено** так же, как править — эти
> `field_name` не в whitelist (§5), PATCH с ними → 400. Скрыть можно только
> вторичные данные.

---

## 5. Whitelist редактируемых/скрываемых полей

Источник истины — enum `OverrideEntity` + per-entity Map в коде
(`hadith/curation/domain/CurationWhitelist.java`); валидируется на каждом
PATCH. **Принцип:** РЕДАКТИРУЕМО = метаданные/классификации/атрибуции, где
ошибка импорта реальна и фикс не искажает первоисточник. СКРЫВАЕМО =
вторичные суждения/имена (модерация). ЗАПРЕЩЕНО = первоисточник (текст
откровения/предания/цитаты).

| Сущность (таблица) | ПРАВИТЬ | СКРЫВАТЬ | ЗАПРЕЩЕНО (первоисточник) | Обоснование |
|---|---|---|---|---|
| **hd_hadiths** | `status`, `authenticity`, `primary_number`, `hadith_type`, `chapter_ar`, `sub_chapter_ar` | (запись не скрывается — хадис = единица) | `normalized_matn`, `full_text_ar` | authenticity выводится эвристикой (2228 NULL) → главный кейс правки; матн/огласованный текст = первоисточник, неизменен. |
| **hd_matns** | `printed_number`, `page_no`, `volume`, `divergence_summary`, **`text_ru`, `text_en`** | запись-уровень (скрыть вариацию) | `text_ar`, `text_ar_normalized` | Перевод (`text_ru/en`) — **наш контент, не первоисточник** → редактируем (мигрирует C9, §9). Арабский матн неизменен. |
| **hd_narrators** | `reliability_grade`, `tabaqa`, `grade_text`, `name_ar`, `kunya`, `laqab`, `year_birth_hijri`, `year_death_hijri`, `birthplace`, `death_place`, `primary_residence`, `reliability_comment` | поле `grade_text`/`reliability_comment` (спорный джарх) | — (имя рави — данные, не первоисточник; правится) | reliability/tabaqa — 31% NULL, частый фикс. Имя рави исправимо (не откровение). |
| **hd_sanads** | `chain_grade`, `primary_chain` | запись-уровень (скрыть слабую цепь) | — | оценка цепи — суждение, правится/скрывается. |
| **hd_sanad_narrators** | `transmission_phrase` | поле `transmission_phrase` | `narrator_id`, `position` (структура цепи — менять = перестроить иснад, вне overlay) | `entity_id='sanad_id'`, `field_name='transmission_phrase@{position}'` (композитный PK). Структуру цепи overlay не меняет (это не правка поля, а реструктуризация — отдельный механизм, backlog). |
| **hd_rulings** | `ruler_name`, `ruler_death_year`, `ruling_text`, `book_name`, `page`, `volume` | запись-уровень + поле `ruler_name` (имя одиозного «учёного») | — | вердикт = вторичное суждение (НЕ цитата-первоисточник): правим/скрываем. |
| **hd_explanations** (SHARH/ILAL/GHARIB) | `book_name`, `author`, `author_death_year`, `page`, `volume`, `text` | запись-уровень + `author`/`text` | — | комментарий = вторичный текст учёного, не откровение: правим/скрываем (экстремистский шарх). |
| **hd_narrator_commentaries** | `commenter`, `commenter_death_year`, `book_name`, `author`, `page`, `volume` | запись-уровень + `commenter` | `comments` (jsonb массив verbatim-цитат — **цитата-первоисточник риджаль-книги**, не правим; можно только СКРЫТЬ запись целиком) | джарх/таʿдиль verbatim — цитата из источника → текст неизменен, но запись скрываема (заблудший критик). |

**Записи-уровень hide** (`__record__`) разрешён для: hd_matns (вариация),
hd_sanads (цепь), hd_rulings, hd_explanations, hd_narrator_commentaries.
**НЕ разрешён** для hd_hadiths (скрыть хадис целиком = убрать из корпуса —
это не модерация поля, делается статусом/удалением вне overlay).

> **Граница первоисточника зафиксирована:** запрещены к правке —
> `hd_hadiths.normalized_matn`, `hd_hadiths.full_text_ar`,
> `hd_matns.text_ar`, `hd_matns.text_ar_normalized`,
> `hd_narrator_commentaries.comments`. (Аят/цитата-источник в других доменах
> — node citations — вне hadith-overlay; если понадобится — отдельный overlay
> с тем же принципом, но это другой трек.)

---

## 6. REST API

### 6.1 Generic эндпоинты (выбран generic, не per-entity)

Базовый путь `/api/v1/admin/curation/overrides`. Generic, т.к. форма
override одинакова для 8 сущностей; per-entity дал бы 8× boilerplate.
Контроллер `CurationOverrideController` (`hadith/curation/web/`).

| Метод | Путь | Тело / параметры | Назначение |
|---|---|---|---|
| `PUT` | `/admin/curation/overrides` | `{entityTable, entityId, fieldName, value?, isNull?, hidden?, reason?}` | upsert override (правка поля и/или hide). Idempotent по UNIQUE-ключу. |
| `DELETE` | `/admin/curation/overrides?entityTable=&entityId=&fieldName=` | — | откатить правку к импортному значению (удалить строку override). |
| `GET` | `/admin/curation/overrides?entityTable=&entityId=` | — | список overrides записи (для admin-вида: что переопределено/скрыто). |

PUT-тело (валидация Bean Validation + сервис):
```json
{
  "entityTable": "hd_narrators",
  "entityId": "uuid",
  "fieldName": "reliability_grade",
  "value": "THIQA",          // nullable; обязателен если !hidden && !isNull
  "isNull": false,           // явная правка в NULL
  "hidden": false,           // скрыть поле/запись (fieldName='__record__')
  "reason": "фикс: alminasa дал UNKNOWN, у Ибн Хаджара ثقة"
}
```

### 6.2 RBAC

- **ADMIN** — полный доступ (правка + hide + delete + list). Гейт как у
  alminasa-импорта: `requireAdmin` (образец `ShamelaAdminController:268`)
  ИЛИ `permissionService.assertHasRoleAtLeast(role, ADMIN)`
  (`PermissionService.java:249`).
- **SCHOLAR** — **не на старте.** FB-5 — про admin-курацию/модерацию.
  Хадис-оценки SCHOLAR'а уже есть через `hadith_grades` (ADR-062, отдельная
  ось). Открытие SCHOLAR'у части полячей (напр. authenticity/reliability как
  «предложение») — отдельное расширение с моделью review (backlog). На старте
  **ADMIN-only**, как все mutating hadith-эндпоинты сейчас.

### 6.3 Коды ошибок (RFC 7807, как везде)

| Код | type | Когда |
|---|---|---|
| 400 | `curation-field-not-editable` | `fieldName` не в whitelist сущности (или первоисточник). |
| 400 | `curation-invalid-enum-value` | `value` не проходит enum-whitelist поля (authenticity/status/…). |
| 400 | `curation-reason-required` | `hidden=true` без `reason`. |
| 400 | `curation-empty-override` | ни `value`, ни `isNull`, ни `hidden`. |
| 403 | `forbidden-insufficient-role` | роль ниже ADMIN. |
| 404 | `curation-entity-not-found` | `(entityTable, entityId)` не существует в целевой таблице. |
| 404 | `curation-override-not-found` | DELETE несуществующего override. |

`api-contract.md` — при реализации добавить секцию «Курация данных
(ADR-065)» по формату документа (Base URL `/api/v1`, camelCase, ProblemDetails;
шаблон — как существующие эндпоинты §«Базовые решения»). `npm run generate-api`
после добавления DTO.

---

## 7. Аудит-лог

**Решение: двойной.**
- **`edited_by`/`edited_at`/`reason` в самой `hd_field_overrides`** —
  «текущее состояние правки» (кто владеет актуальным override). Этого хватает
  для admin-вида «список правок записи».
- **`AuditLogService` (ADR-043) — историю изменений правок.** Каждый
  PUT/DELETE override пишет `audit_log` row:
  - `entity_type = 'HD_FIELD_OVERRIDE'` (новое значение; CHECK на audit_log
    — `action`/`entity_type` свободные varchar, расширять не нужно).
  - `entity_id = override.id`, `parent_entity_type = entityTable`
    (`'hd_narrators'…`), `parent_entity_id = entityId` — чтобы admin-вид
    записи видел все курации через `findByParentOrSelf`.
  - `action = 'UPDATE'` (или `'CREATE'` на первом PUT, `'DELETE'` на откате).
  - `changes` = `diff()` старого/нового override (`old/new` value+hidden) +
    `reason` в snapshot. Образец вызова — `AuditLogService.logUpdate` с
    `DiffBuilder` (`AuditLogService.java:367`).
- **Особо для hide-модерации:** `reason` обязателен (валидация §6.3) и
  попадает И в `hd_field_overrides.reason`, И в `audit_log.changes` — «кто
  скрыл экстремистский контент и почему» восстанавливается из обоих.

Сервис `CurationOverrideService` инжектит `AuditLogService`, пишет в **той же
транзакции** что и override (consistency: rollback override → rollback audit,
как везде в ADR-043).

---

## 8. Frontend

### 8.1 Паттерн (как C9 `MatnTranslateControls`)

C9 — образец инлайн-edit (`MatnTranslateControls.tsx`): рядом с полем
карандаш `Pencil` (lucide), клик → инлайн-`<textarea>` + Save/Cancel → PATCH,
ADMIN-гейт через `hasRoleAtLeast(role, 'ADMIN')` (`authStore.ts:24`).
Роль приходит пропом из страницы (`userRole = useAuthStore(s => s.user?.role)`,
`HadithDetailPage.tsx:136`).

Обобщаем в переиспользуемый компонент:

```tsx
// apps/hadith/components/curation/EditableField.tsx
<EditableField
  entityTable="hd_narrators"
  entityId={bio.id}
  fieldName="reliability_grade"
  value={bio.reliabilityGrade}
  kind="enum"            // 'text' | 'enum' | 'number'
  options={RELIABILITY_OPTIONS}  // для enum
  role={userRole}        // ADMIN-гейт внутри
  onSaved={refetch}
/>
```
- ADMIN видит карандаш рядом со значением; не-ADMIN — чистый рендер (как
  `{isAdmin && <button…Pencil>}` в C9).
- `kind='enum'` → `<select>` с whitelist-опциями (authenticity/status/
  reliability); `'text'` → textarea; `'number'` → input.
- Save → `PUT /api/v1/admin/curation/overrides` (через `apiPutRaw`),
  тост успеха/ошибки (`formatApiError`), инвалидация кэша detail
  (`invalidateCache` + nonce-рефетч, как грейд-флоу `HadithDetailPage:687`).

### 8.2 Hide-тогл

```tsx
// apps/hadith/components/curation/HideToggle.tsx — на скрываемых блоках
<HideToggle entityTable="hd_rulings" entityId={ruling.id} record  // __record__
            hidden={ruling.hiddenByAdmin} role={userRole} onSaved={refetch}/>
```
- Иконка `EyeOff`/`Eye`; клик → модалка с обязательным `reason` (textarea) →
  PUT `{hidden:true, reason}`. ADMIN-only.
- Размещение по странице:
  - **HadithDetailPage** (`HadithDetailPage.tsx`): карандаши на бейджах шапки
    (status/authenticity/hadithType, `:377-417`), на `chapterAr/subChapterAr`;
    hide+edit на карточках вкладок Вердикты (`RulingsList`), Шарх/Иляль/Гариб
    (`ExplanationsList`), на вариациях матна. **НЕ** на тексте «Текст»
    (`:442-466` — первоисточник, карандаша нет).
  - **NarratorDetailPage** (`NarratorDetailPage.tsx`): карандаши на полях-
    карточках `<dl>` (`:188-251` — tabaqa/laqab/kunya/годы/места), на
    reliability-бейдже (`:165-172`), на verbatim-баре grade_text (`:256-268`);
    hide на карточках комментариев (`NarratorCommentaryList`).

### 8.3 Индикатор «отредактировано/скрыто администратором»

- **Отредактировано:** маленький значок-точка/`Pencil`-outline рядом с
  переопределённым полем (ADMIN видит, что значение — override, не импорт;
  tooltip «исправлено администратором {date}»). Источник — payload несёт
  `overriddenFields: string[]` в admin-режиме (apply при `revealHidden`/
  ADMIN-роли добавляет список). Для обычного читателя — без значка (видит
  просто исправленное значение).
- **Скрыто (ADMIN reveal-режим):** скрытая запись приходит с
  `hiddenByAdmin=true` + `reason` → рисуется приглушённо с пилюлей «скрыто:
  {reason}» и кнопкой «показать снова» (DELETE override). Обычный читатель
  записи не видит вовсе (apply вырезал).

> **i18n:** `hadith.curation.*` ключи (edit/hide/hidden_by_admin/reason/…)
> в `shared/i18n/dictionary.ts` (RU + EN), как все строки.

---

## 9. Фазовый план

Декомпозиция по правилу проекта (подэтапы X.a/X.b/…), независимый review
после каждой логической фазы (ADR-043 cadence — каждые 5-7 коммитов / на
закрытии фазы).

### P0-1a (быстрый промежуток — спасти перевод merge'ом, ДО overlay)
**Альтернатива C для частного случая.** `insertMatn` сейчас delete-recreate
теряет `text_ru/en`. Минимальный фикс **до** готовности overlay: при re-map
матна **сохранить** `text_ru/text_en` существующей primary-строки матна (по
`hadith_id` + `is_primary`) и перенести в новую строку (или upsert вместо
delete+insert для primary-матна). Это спасает накопленные переводы на время,
пока overlay строится. Файл: `AlminasaHadithMapper.insertMatn:305` +
`MatnRepository`. **Решение:** сделать как страховку, т.к. перевод —
единственный накопленный курируемый контент (1 строка на момент аудита, но
растёт). После фазы 6 (миграция matn-перевода в overlay) этот merge **снять**
(перевод будет жить в overlay, delete-recreate матна станет безопасным).

### Фаза 1 — Схема + repository + домен
- 1.a Миграция `20260618-78` (§2.3) + регистрация в master.
- 1.b `FieldOverride` record + `OverrideEntity` enum + `OverrideRepository`
  (CRUD + `findByEntity(table, ids)` батч).
- 1.c `CurationWhitelist` (§5) + unit-тесты whitelist.
- IT: миграция применяется, UNIQUE/CHECK работают.

### Фаза 2 — Apply-слой
- 2.a `OverrideApplyService` + `OverrideSet` + каст-помощники (§3.4) с
  unit-тестами (битый int → base+WARN; null-override; hidden).
- 2.b `apply(Hadith)` + `apply(Narrator)`; интеграция в
  `HadithRepository.findById/findPage`, `NarratorRepository.findById/findPage`
  (НЕ в `findByExternalId` — §3.3). IT: правка authenticity видна в
  `GET /hadiths/{id}`, импорт её не трогает.

### Фаза 3 — Пилотные сущности (hd_hadiths + hd_narrators)
- 3.a Generic write-API (`PUT/DELETE/GET /admin/curation/overrides`) +
  `CurationOverrideService` + audit-интеграция (§7) + RBAC. IT: PATCH
  authenticity, откат, whitelist-reject первоисточника (400).
- 3.b Frontend `EditableField` + интеграция в шапку HadithDetailPage и
  поля-карточки NarratorDetailPage (reliability/tabaqa/authenticity/status).
  Playwright smoke.
- **Review-чекпоинт** (пилот закрыт).

### Фаза 4 — Hide/show
- 4.a Запись-уровень `__record__` hide + поле-уровень hide в apply
  (вырезание из списков сателлитов); reveal-режим для ADMIN (§4.3).
- 4.b Frontend `HideToggle` + reason-модалка + индикатор «скрыто
  администратором» на rulings/explanations/commentaries.

### Фаза 5 — Расширение на сателлиты
- Apply + DTO-интеграция для hd_rulings, hd_explanations,
  hd_narrator_commentaries, hd_matns(meta-поля), hd_sanads(chain_grade),
  hd_sanad_narrators(transmission_phrase). Каждый — editable+hide по §5.

### Фаза 6 — Миграция C9-перевода в overlay
- 6.a `text_ru/text_en` matn → overlay (`hd_matns.text_ru/en` editable в
  whitelist). **PATCH `/hadith/matns/{id}/translation` (C9) → переписать на
  overlay:** либо роутить старый эндпоинт в `CurationOverrideService`
  (сохранить URL для фронта), либо фронт переключить на generic-API.
  **Решение:** оставить публичный URL `/matns/{id}/translation` (фронт
  `MatnTranslateControls` не трогаем), но внутри писать в overlay вместо
  `MatnRepository.updateTranslation`. AI-перевод (`POST /translate`) пишет
  в **базовый** `hd_matns.text_ru` (как сейчас) → override поверх — это
  правка человека поверх AI; merge-приоритет override.
- 6.b Снять P0-1a merge-страховку (перевод теперь в overlay → delete-recreate
  матна безопасен; override живёт по `hadith_id`-резолву, не по `matnId`).
  **Внимание:** override matn-перевода ключуется `entity_id = matn.id`,
  который меняется на реимпорте (новый UUID). → для matn-перевода ключевать
  override по **стабильному** идентификатору: `(hadith_id, is_primary)` или
  по `hd_hadiths.id` + поле `primary_text_ru`. **Открытый вопрос §10.**
- **Review-чекпоинт** (трек закрыт; roadmap + handoff).

---

## 10. Риски / открытые вопросы

1. **Перф apply на каждый read.** Mitigation: батч-`load` (один `IN`-запрос
   на тип сущности за payload — нет N+1). Подавляющее большинство записей
   overrides не имеют (пустой `OverrideSet` = NO_OP, ноль аллокаций). List
   на 20 хадисов = +1 запрос. Кэш **не нужен на старте** (overrides редки);
   если профиль покажет горячий путь — короткоживущий per-request кэш или
   eviction-кэш по `entity_id`. **Решение:** без кэша, измерять.

2. **Ключ matn-перевода нестабилен** (`matn.id` = новый UUID на реимпорте).
   **Открытый вопрос:** ключевать matn-overrides по `(hadith_id,is_primary)`
   синтетическому ключу ИЛИ перенести `text_ru/en` на уровень hd_hadiths.
   Решение в фазе 6 (рекомендация: `entity_table='hd_matns'`,
   `entity_id=hadith_id`, `field_name='primary_text_ru'` — резолв primary-матна
   на apply; abstracts от пересоздания строки). **Эскалация Абдуле перед
   фазой 6.**

3. **Миграция существующего C9-перевода в overlay.** На момент аудита — 1
   строка `text_ru`. One-shot data-миграция (Liquibase `<sql>` или джоба):
   прочитать существующие `hd_matns.text_ru/en != null` → создать
   override-строки `edited_by` = системный/первый ADMIN. Тривиально (низкий
   объём). **Решение:** Liquibase data-migration в фазе 6.

4. **Взаимодействие с `hadith_grades` (ADR-062).** Не пересекается:
   `hadith_grades` — scholar-атрибутированные оценки (своя таблица, свой
   REST `/hadiths/{id}/grades`, ось `grades` в detail). Overlay **не дублирует
   и не трогает** grades. authenticity (ADR-063, выводимая ось) — наоборот,
   главный кандидат на overlay-правку (2228 NULL). **Зафиксировать:** overlay
   правит `authenticity`/`status`; ручные scholar-оценки остаются в
   `hadith_grades`. Не городить authenticity в grades и наоборот.

5. **List-эндпоинты и скрытые записи.** `GET /hadiths`, `/narrators` list:
   apply field-overrides — **да** (правленый authenticity/reliability виден
   в фасетах/превью). Скрытие на уровне хадиса/рави в list **не применяется**
   (hd_hadiths/hd_narrators не имеют `__record__`-hide, §5). Фильтрация
   скрытых **сателлитов** в list не нужна (list не отдаёт сателлиты).
   **Фасет-фильтр по authenticity:** `findPage` фильтрует по БД-колонке —
   override authenticity в фасете **не учтётся** в WHERE (фильтр идёт по
   базовому значению). Это известное ограничение: правка authenticity видна
   в карточке, но фасет-счётчик считает по импорту. **Решение:** приемлемо на
   старте (overrides редки); если станет важно — материализовать override в
   колонку фоновым проходом ИЛИ фильтровать пост-apply (дороже). В backlog.

6. **Orphan overrides** после удаления записи (нет FK на entity_id).
   Append-only корпус → удаления редки. Janitor-проход (по образцу
   audit-retention) — в backlog, не блокер.

7. **Структурные правки иснада** (добавить/убрать звено, переставить рави)
   — overlay поля НЕ покрывает (это реструктуризация, не правка колонки).
   Явно вне скоупа; если понадобится — отдельный механизм (backlog).
   Overlay покрывает только `transmission_phrase`/`chain_grade`.

---

## ADR-065 draft

> Готовый блок для вставки в `docs/decisions.md` (после ADR-064). Файл сам
> НЕ редактирую — вставить при реализации фазы 1.

```markdown
## ADR-065: Overlay-таблица hd_field_overrides для курации данных hadith-домена (миграция 78)

**Статус:** ✅ Принято (Сессия NN, 2026-06-18)
**Связанные:** ADR-043 (RBAC + audit_log — переиспользуем), ADR-060 (alminasa —
единственный источник, реимпорт затирает), ADR-061 (hd_narrator_commentaries),
ADR-062 (hadith_grades — отдельная курируемая ось, НЕ дублируем),
ADR-063 (authenticity — выводимая ось, главный кандидат на правку).

### Контекст

Реимпорт alminasa (`AlminasaHadithMapper.mapHadith`,
`AlminasaNarratorMapper.mapNarrator`) делает upsert хадиса/рави с перезаписью
всех колонок + delete-recreate сателлитов → **молча затирает любые ручные
правки** (P0-1 аудита). Механизма do-not-overwrite в схеме/коде нет.
Параллельно FB-5 требует: (a) править любые данные любой hd_*-сущности (фикс
ошибок импорта), (b) скрывать/показывать данные без удаления (модерация),
(c) при гарантии неизменности первоисточника (текст матна/аята/цитаты).
Перевод матна (C9 PATCH) — самый острый случай: `insertMatn` пересоздаёт
строку с `text_ru/en = NULL` и новым `matnId`.

### Решение

Generic **overlay-таблица `hd_field_overrides`** (`entity_table, entity_id,
field_name, override_value, is_null_override, hidden, edited_by, edited_at,
reason`; UNIQUE по `(entity_table, entity_id, field_name)`). Импорт hd_*
**не трогаем** — пишет базовый слой как есть. Правки и hide-флаги живут в
overlay и накладываются **на ЧТЕНИИ** централизованным `OverrideApplyService`
(декоратор на доменных records ДО маппинга в DTO; вызывается в
`findById/findPage` репозиториев + на списках сателлитов в контроллере). →
правки автоматически переживают delete-recreate реимпорта.

Первоисточник защищён **whitelist'ом редактируемых полей** (а не схемой):
`normalized_matn`, `full_text_ar`, `hd_matns.text_ar`,
`hd_narrator_commentaries.comments` — не входят в editable-набор, PATCH с
ними → 400. Перевод (`text_ru/en`) — наш контент, редактируем.

Hide: поле-уровень (поле→null) + запись-уровень (`field_name='__record__'`
→ запись вырезается из payload). `reason` обязателен для hide (модерация).
Аудит — двойной: `edited_by/at/reason` в самой таблице + `AuditLogService`
(ADR-043) на каждый PUT/DELETE. RBAC: ADMIN-only на старте (SCHOLAR — позже,
с моделью review). REST: generic `PUT/DELETE/GET /api/v1/admin/curation/overrides`.

### Отвергнутые альтернативы

- **(B) Колонки-локи `*_locked/*_hidden boolean` + условный UPDATE.** N колонок
  × M таблиц раздувают мапперы и миграции; трогают import-write-path (риск
  регрессии идемпотентности); не работают на delete-recreate-сателлитах
  (lock исчезает со строкой); hide требует ещё колонок. Не масштабируется.
- **(C) Merge-стратегия (upsert сателлитов по природному ключу, сохраняя
  правленые колонки).** Требует природного ключа на каждом сателлите (хрупко),
  не даёт hide/show и аудита. Принята лишь как **частный быстрый фикс для
  перевода** (P0-1a) до готовности overlay, затем снимается.

### Последствия

- **+** Не трогаем import-логику → правки переживают реимпорт; один механизм
  на правку + hide + аудит; расширяется на любое поле любой hd_*-сущности;
  первоисточник защищён декларативным whitelist'ом; переиспользует audit_log.
- **−** Новый apply-слой на чтении (батч-fetch, NO_OP при отсутствии
  overrides — перф-цена мала); `findById/findPage` перестают быть «чистым БД»
  (raw — через `findByExternalId` для импорта); фасет-фильтр list по
  authenticity считает по базовому значению (override в WHERE не виден —
  backlog); ключ matn-перевода нестабилен на реимпорте (резолв по
  `hadith_id`, фаза 6).
- Миграция: один-shot перенос существующего C9-перевода в overlay; C9-эндпоинт
  переписан на overlay внутри (URL сохранён).
```

---

## Сводка ключевых решений

1. **Apply-слой = `OverrideApplyService`, декоратор на ДОМЕННЫХ records**
   (не DTO, не per-RowMapper). Вызывается в `findById/findPage` репозиториев
   хадиса/рави (покрывает list/getOne/transmitted/sibling) + на списках
   сателлитов в `getDetail`/`getOne` контроллеров. `findByExternalId` (импорт)
   overrides НЕ применяет. Батч-`load` по `(entity_table, entity_id IN …)` —
   без N+1; NO_OP при отсутствии overrides.
2. **Whitelist-принцип:** редактируемо = метаданные/классификации/атрибуции
   (authenticity, reliability, tabaqa, вердикты, переводы); скрываемо =
   вторичные суждения/имена; **запрещено = первоисточник** (`full_text_ar`,
   `normalized_matn`, `text_ar`, commentary `comments`). Защита whitelist'ом
   в сервисе, не схемой.
3. **Фазы:** P0-1a (merge-страховка перевода) → 1 схема+repo → 2 apply →
   3 пилот (hadiths+narrators)+review → 4 hide/show → 5 сателлиты →
   6 миграция C9-перевода в overlay+review.
4. **Аудит двойной** (таблица + AuditLogService ADR-043); `reason` обязателен
   для hide. **RBAC ADMIN-only**. ADR-065 (черновик внутри). Не дублируем
   `hadith_grades` (ADR-062).
