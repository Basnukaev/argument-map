# alminasa Hadith Ingestion — Plan 3: маппер am_staging_* → hd_*

> **Оркестрация:** OMC (см. корневой CLAUDE.md «Оркестрация (OMC)»). Задачи
> исполняются по чекбоксам (`- [ ]`), независимый review после исполнения.

**Goal:** детерминированный (БЕЗ AI) маппер staging-снапшота alminasa в доменные
`hd_*`-таблицы: хадисы + матны + издания + иснад (парс `<a class=rawy>` из
`full_text_ar`) + рулинги + шархи + такхридж-crossrefs + рави с сетью
передатчиков. Идемпотентный upsert по `(external_source='alminasa', external_id)`.

**Architecture:** двухпроходный импорт. Проход 1 — нарраторы (11,221 строк
`am_staging_narrator` → `hd_narrators` + `hd_narrator_relations`). Проход 2 —
хадисы (`am_staging_hadith` → `hd_hadiths` + сателлиты; рави, отсутствующие в
staging, до-создаются из `narrators[]` hadith-дока). Финальный resolve-проход —
проставление FK `related_hadith_id` (crossrefs) и `related_narrator_id`
(relations) одним UPDATE-ом по уже импортированным сущностям. Каждый
хадис/нарратор — в собственной транзакции (ошибка одного дока не валит прогон;
маппер-бины отделены от orchestration-бина — gotcha self-invocation).

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC Template, Jackson (re-parse raw
JSONB), Testcontainers. Новых миграций НЕТ (схема готова Планами 1-2). REST/админка
— НЕ здесь (План 5); только service-слой + dry-run-метод под будущий endpoint.

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §C.
**Планы 1-2 (закрыты):** `2026-06-03-alminasa-hadith-ingestion-schema.md`,
`2026-06-04-alminasa-crawler-staging.md`.

---

## Ключевые факты данных (фикстуры `backend/src/test/resources/alminasa/`, live-верифицировано С56)

- `hadith`-поле: `حَدَّثَنَا <a class=rawy id=4698>الْحُمَيْدِيُّ …</a> ، قَالَ : حَدَّثَنَا <a class=rawy id=3443>…`
  — теги БЕЗ кавычек вокруг атрибутов; в конце `<a class=matn>…</a>` с матном.
  Порядок rawy-тегов = `narrators[]` (collector→companion; проверено: 4698, 3443,
  8272, 6796, 5719, 5913=عمر companion).
- `narrators[]` в hadith-доке: `{id: "4698"(строка!), full_name, level, grade,
  is_companion, is_unknown, hasCommentary, reference}` — метаданные звеньев,
  даже если рави нет в staging (краулер мог не дойти).
- narrator-док (`am_staging_narrator.raw`): full_name, nickname (кунья), origin
  (нисба), level (табака), grade (джарх-та'диль verbatim), born_on/died_on
  (ПРОЗА), lived_in/died_in, book_titles[], top_students[]/top_scholars[]
  (`"الزهري - (24)"` = имя + частота).
- rulings-док: `{hadith_id, ruler, ruler_dod, narrations_type, rulings:[{hadith_id,
  ruling, type, book_name, number, page, volume}]}` — один док = один учёный ×
  хадис; **внутренние `hadith_id` указывают на ПАРАЛЛЕЛЬНЫЕ передачи** (для 146-1
  док Муслима содержит только 158-3537). Прямой вердикт на сам хадис — в embedded
  `rulings[]` hadith-дока. → нужен **union двух источников + дедуп**.
- explanation-док: `{hadith:{hadith_id,…}, explanation:{explanation_book_name,
  explanation_book_author (бывает с trailing space!), explanation_page,
  explanation_volume, hadith_explanation_array:[{id, sharh}]}}` — сегменты шарха.
- `raw_narrations[]` **содержит сам хадис** (146-1 в списке у 146-1) — self
  пропускать. `narrations_numbers[]` — номера сиблингов в их сборниках.
- `hadith_serial_id` — per-book, НЕ глобален (хотфикс 10b26b2): порядок обхода
  staging — `ORDER BY book_id, hadith_serial_id`, пагинация keyset по `hadith_id`
  нельзя (строка) → keyset по `(book_id, hadith_serial_id)`.
- Лимиты схемы: `transmission_phrase varchar(40)`, `hd_hadiths.hadith_type
  varchar(40)`, `external_id varchar(40)`, `tabaqa varchar(120)`,
  `hd_explanations.kind CHECK (SHARH|ILAL|GHARIB)`,
  `hd_narrator_relations.role CHECK (STUDENT|SCHOLAR)`.

## Book-id → slug map (полный, live-зонд С56)

| book_id | slug | nameAr | nameRu |
|---|---|---|---|
| 19 | muwatta | موطأ مالك | Муватта Малика |
| 121 | ahmad | مسند أحمد بن حنبل | Муснад Ахмада |
| 137 | darimi | سنن الدارمي | Сунан ад-Дарими |
| 146 | bukhari | صحيح البخاري | Сахих аль-Бухари |
| 158 | muslim | صحيح مسلم | Сахих Муслима |
| 173 | ibn-majah | سنن ابن ماجه | Сунан Ибн Маджи |
| 184 | abu-dawud | سنن أبي داود | Сунан Абу Дауда |
| 195 | tirmidhi | جامع الترمذي | Джами ат-Тирмизи |
| 319 | nasai | سنن النسائي الصغرى | Сунан ан-Насаи |
| 345 | ibn-khuzaymah | صحيح ابن خزيمة | Сахих Ибн Хузаймы |
| 454 | ibn-hibban | صحيح ابن حبان | Сахих Ибн Хиббана |
| 594 | mustadrak | المستدرك على الصحيحين | Мустадрак аль-Хакима |

`nameAr` при создании коллекции берём из `book_name` дока (он авторитетнее
таблицы), slug/nameRu — из статической карты. Slug `bukhari` уже существует в
dev-БД (sunnah-пилот) — `findBySlug` переиспользует строку.

## Дизайн-решения (зафиксированы до исполнения)

1. **Иснад из тегов, narrators[] — метаданные.** Источник порядка и состава
   цепи — rawy-теги `full_text_ar`. `narrators[]` — лукап-таблица метаданных
   по id. Расхождение количества (теги ≠ narrators[]) — WARN + строим по тегам.
   Нет rawy-тегов вообще → хадис импортируется БЕЗ цепи (не ошибка: бывают
   доки без разметки).
2. **Transmission formula — семантика «сегмент ПОСЛЕ тега» (фикс C1 критик-ревью).**
   Сегмент текста сразу ПОСЛЕ закрывающего тега рави `c_i` (до следующего тега,
   а для последнего — до `<a class=matn>`) — это собственная речь `c_i` о том,
   как ОН получил хадис от следующего звена («قَالَ : حَدَّثَنَا سُفْيَانُ» —
   слова аль-Хумайди). Контракт парсера: `receivedVia(c_i)` = сегмент после
   тега `c_i`. После реверса (position 0 = сподвижник, Prophet-side — зеркало
   `IsnadPersistenceService` javadoc и `SanadGraphService.buildEdges`) формула
   позиции `p` = `receivedVia` нарратора на этой позиции — БЕЗ сдвигов.
   Сегмент ПЕРЕД первым тегом (формула составителя «حدثنا») →
   `metadata.collectorPhrase` цепи. Эталонный вектор 146-1 (фикстура):
   pos0 عمر=سمعت(хвост), pos1 علقمة=سمعت, pos2 محمد=سمع, pos3 يحيى=أخبرني,
   pos4 سفيان=حدثنا, pos5 الحميدي=حدثنا; collectorPhrase=حدثنا.
   Из сегмента извлекаем НОРМАЛИЗОВАННОЕ формула-слово по приоритетному списку
   (حدثنا/حدثني/أخبرنا/أخبرني/أنبأنا/سمعت/سمع/عن/أن — потом фолбэк قال/يقول;
   диакритика снимается `ArabicTextNormalizer`); ничего не нашли → NULL.
   IT обязан включать round-trip через `SanadGraphService.buildGraph` с
   проверкой label ребра у Prophet-стороны — контракт визуализации залочен.
3. **Resolve рави звена**: по `external_id` в `hd_narrators` (уже импортирован
   проходом 1) → если нет, создать из staging-narrator (полный маппинг) → если
   и там нет, минимальный stub из `narrators[]`-entry hadith-дока (full_name,
   grade→enum, level→tabaqa, is_companion→SAHABI) с тем же external_id —
   последующий проход 1 дообогатит upsert-ом. Текст имени из тега НЕ источник
   (содержит падежные формы). Edge: tag-id отсутствует И в staging, И в
   `narrators[]` → stub с именем из текста тега (trim) + grade UNKNOWN +
   `metadata.stubFromTag=true`, WARN. Дубль одного рави в цепи (один
   external_id на двух позициях) — допустим, тот же UUID на обеих позициях.
4. **Reliability enum (производная, verbatim не теряем):** `is_companion=true`
   ИЛИ `level=='صحابي'` → SAHABI; иначе `is_unknown=true` → UNKNOWN (флаг
   alminasa авторитетнее текста grade); иначе по началу grade: `ثقة`→THIQA,
   `صدوق`→SADUQ, `مقبول`→MAQBUL, `ضعيف`→DAIF, `متروك`→MATRUK; иначе UNKNOWN
   (реальный кейс: مالك grade=`الفقيه إمام دار الهجرة` → UNKNOWN осознанно,
   тест-кейс обязателен). Поля `is_unknown`/`hasCommentary`/`reference`/
   `extended_full_name` → `hd_narrators.metadata` (forward-compat, не теряем).
5. **Хиджри-годы из прозы** — best-effort: `سنة (\d{1,4})` ИЛИ голое 1-4-значное
   число в died_on/born_on → INTEGER; иначе NULL (verbatim в `*_text` остаётся).
6. **Статус**: book_id ∈ {146,158} → CANONICAL, иначе VARIANT (консервативно,
   спека §C; уточнение из рулингов — отдельный будущий шаг, НЕ здесь).
7. **Рулинги = union двух источников (фикс I2):**
   (а) embedded `rulings[]` hadith-дока → по строке на entry
   (metadata.source='embedded');
   (б) rulings-доки, выбранные по **ВЕРХНЕУРОВНЕВОМУ** `hadith_id` дока
   (inner-entries — это локации параллельных передач, НЕ фильтр) → **одна
   строка на док** (= один учёный): `ruler_name`/`ruler_death_year` с верха
   дока; `ruling_text` = уникальные inner `ruling`-значения join «؛ » (обычно
   одно); `book_name`/`page`/`volume` = inner-entry с `hadith_id == текущий`
   если есть, иначе первый inner; metadata = {source:'index',
   relatedExternalId: выбранный inner.hadith_id, narrations:[все inner
   {id,page,volume}], narrationsType}.
   Дедуп по (ruler_name, ruling_text, book_name, page, volume) — embedded
   приоритетен. Эталон 146-1: ровно 2 строки — Бухари (page 6, vol 1;
   embedded схлопнулся с index-доком) + Муслим (page 48, vol 6,
   relatedExternalId=158-3537). Дедуп-логика — отдельный unit-тестируемый
   метод (не только сквозь IT).
8. **Шарх**: один док → одна строка `hd_explanations` kind=SHARH; text = join
   сегментов `hadith_explanation_array[].sharh` через `\n\n`; author/book trim;
   `author_death_year` NULL (нет в данных); metadata={esId}. علل/غريب НЕ здесь
   (контракты не сняты — План 6, гейт HAR).
9. **Идемпотентность**: хадис — upsert по external_id; сателлиты
   (matns/editions/sanads/crossrefs/rulings/explanations) — delete-recreate per
   hadith (паттерн IsnadPersistenceService); relations — delete-recreate per
   narrator. Повторный прогон не плодит строк. Порядок ВНУТРИ транзакции
   `mapHadith` (фикс I3): resolve hadith UUID (find|insert) → deleteByHadithId
   ВСЕХ сателлитов → re-insert. `deleteByHadithId` уже ЕСТЬ у editions/
   crossrefs/rulings/explanations/sanads — НЕ пересоздавать (compile error);
   добавить только у Matn.
10. **Транзакции**: orchestration-цикл БЕЗ @Transactional; `@Transactional` на
    public-методах mapper-бинов (отдельные бины — НЕ self-invocation). Ошибка
    дока → лог + счётчик failed + продолжаем (cap примеров в summary: 20).
11. **Resolve-проход** (после полного импорта батча, re-runnable — фикс I5/I6):
    (а) crossrefs — один SQL `UPDATE hd_hadith_crossrefs c SET related_hadith_id
    = h.id FROM hd_hadiths h WHERE c.related_hadith_id IS NULL AND
    h.external_source='alminasa' AND h.external_id = c.related_external_id`
    (индекс idx_hd_crossrefs_related есть, миграция 71);
    (б) narrator-relations — В JAVA (НЕ SQL: related_name хранится verbatim,
    нормализация = `ArabicTextNormalizer`, из SQL не вызвать; новая колонка =
    миграция, вне скоупа): загрузить Map<normalized_name, List<id>> всех
    alminasa-рави в память (~11k — дёшево), пройти relations с NULL FK, резолв
    ТОЛЬКО при ровно одном кандидате (гомонимы → NULL). ⚠️ ИЗВЕСТНОЕ
    ОГРАНИЧЕНИЕ (зафиксировать в плане и коде): top_students/scholars содержат
    КОРОТКИЕ формы («الزهري»), full_name — полные → hit-rate низкий, FK-резолв
    best-effort MVP; настоящая риджаль-резолюция — backlog. Доки, упавшие при
    импорте, оставляют свои crossref-FK NULL — лечится повторным прогоном.
12. **Матн и номера (фикс I1/M2)**: `matn_with_tashkeel` → primary `hd_matns`
    (textAr + normalized, collectionId, printedNumber, pageNo=page, volume).
    Пустой `matn_with_tashkeel` → fail дока (без матна хадис бессмыслен).
    `number[]`: элементы бывают int И строки (контракт плавает между
    индексами) — defensive parse; пустой/отсутствует → NULL. `primary_number`
    = number[0], НО на `hd_hadiths` есть UNIQUE(collection_id, primary_number)
    (миграция 57) — alminasa-namespace номеров может коллидировать: пре-чек
    `findByCollectionIdAndPrimaryNumber`; занято ДРУГИМ external_id → у нового
    primary_number=NULL (номера целиком живут в `metadata.numbers`) + WARN.
    `book_name` null → nameAr из статической карты.

## File Structure

```
backend/src/main/java/ru/basnukaev/argumentmap/hadith/alminasa/
  etl/AlminasaIsnadParser.java                [T2]  pure: full_text_ar → ParsedIsnad
  etl/dto/ParsedIsnad.java                    [T2]  (links[], collectorPhrase, matnSpan)
  etl/dto/IsnadLink.java                      [T2]  (externalId, nameInText, formula)
  service/AlminasaCollections.java            [T3]  static book-id→(slug,nameRu) map
  service/AlminasaNarratorMapper.java         [T3]  @Transactional mapNarrator(row)
  service/AlminasaHadithMapper.java           [T4]  @Transactional mapHadith(row) + dryRun
  service/AlminasaImportService.java          [T5]  orchestration: importNarrators/importHadiths/resolveRefs
  service/dto/AlminasaImportSummary.java      [T5]  (processed, failed, failures[≤20], …)
  service/AlminasaMappingException.java       [T4]  per-док ошибка маппинга
repository (расширения, modify):
  repository/NarratorRepository.java          [T1]  +update(n)
  repository/HadithRepository.java            [T1]  +update(h)
  repository/MatnRepository.java              [T1]  +deleteByHadithId
  repository/HadithCrossrefRepository.java    [T1]  +resolveRelatedHadithIds()
  repository/NarratorRelationRepository.java  [T1]  +resolveRelatedNarratorIds()
  alminasa/repository/AmHadithStagingDao.java [T1]  +findPage(keyset) +findById +countByBookId
  alminasa/repository/AmNarratorStagingDao.java [T1] +findPage(afterId) +findById
  alminasa/repository/AmRulingStagingDao.java [T1]  +findByHadithId
  alminasa/repository/AmExplanationStagingDao.java [T1] +findByHadithId
tests:
  etl/AlminasaIsnadParserTest.java            [T2]  unit на РЕАЛЬНОМ HTML фикстуры
  service/AlminasaNarratorMapperTest.java     [T3]  unit: enum-таблица, хиджри, relations parse
  service/AlminasaMapperIT.java               [T4]  Testcontainers: e2e один хадис staging→hd_*
  service/AlminasaImportServiceIT.java        [T5]  батч + идемпотентность + failure isolation + resolve
  repository/AmStagingDaoIT.java              [T1, modify] read-методы
docs:
  docs/architecture.md                        [T6, modify] mapping-пайплайн
  docs/glossary.md                            [T6, modify] такхридж/طرق/табака (если нет)
```

REST-контракт НЕ меняется → api-contract.md и types.ts НЕ трогаем (План 5).

---

## Task 1: read-методы staging-DAO + расширения hd_*-репозиториев

- [x] `AmHadithStagingDao`: `findPage(Integer afterBookId, Long afterSerial, int limit)`
      → `ORDER BY book_id, hadith_serial_id` keyset; `findById(hadithId)`;
      `countByBookId()` → `Map<Integer,Long>` (пригодится Плану 5; group by).
      Row-маппинг включает `raw::text` → `rawJson`.
- [x] `AmNarratorStagingDao`: `findPage(Long afterId, int limit)` (keyset по PK),
      `findById(long)`.
- [x] `AmRulingStagingDao.findByHadithId(String)`, `AmExplanationStagingDao.findByHadithId(String)`.
- [x] `NarratorRepository.update(Narrator)` — полный UPDATE по id (все колонки
      кроме id/created_at); `HadithRepository.update(Hadith)` — аналогично.
- [x] `MatnRepository.deleteByHadithId(UUID)` (у editions/crossrefs/rulings/
      explanations/sanads deleteByHadithId УЖЕ есть — не дублировать).
- [x] `HadithCrossrefRepository.resolveRelatedHadithIds()` — `UPDATE
      hd_hadith_crossrefs c SET related_hadith_id = h.id FROM hd_hadiths h
      WHERE c.related_hadith_id IS NULL AND h.external_source='alminasa' AND
      h.external_id = c.related_external_id`; вернуть affected.
- [x] Под Java-resolve relations (реш. 11б):
      `NarratorRepository.findExternalNormalizedNameIds()` →
      `Map<String, List<UUID>>` (name_ar_normalized → ids, только
      external_source='alminasa');
      `NarratorRelationRepository.findUnresolved(limit, offset)` +
      `updateRelatedNarratorId(relationId, narratorId)`.
- [x] Расширить `AmStagingDaoIT` + IT новых методов репозиториев (round-trip,
      keyset-пагинация через границу book_id, resolve-crossrefs с
      позитив/негатив-кейсами).
- [x] Коммит `feat(backend): read-методы staging-DAO + upsert/resolve расширения hd_*-репозиториев`

## Task 2: AlminasaIsnadParser (pure) + unit-тесты на реальном HTML

- [x] Парсер: regex `<a class=rawy id=(\d+)>(.*?)</a>` + `<a class=matn>` —
      порядок, сегменты между тегами, формула по приоритетному списку
      (нормализация `ArabicTextNormalizer`), collectorPhrase, хвостовой сегмент
      → формула position-0 (применяется ПОСЛЕ реверса в маппере — парсер
      возвращает collector→companion и formulaForLink семантику «как предыдущий
      получил от этого»).
- [x] Тесты на фикстуре `hadith-page.json` (146-1: 6 звеньев, ids в порядке
      4698→5913, формулы حدثنا/حدثنا/حدثنا/أخبرني/سمع/سمعت, companion-формула
      сمعت, collectorPhrase حدثنا) + 146-53 (второй хит) + edge: без тегов →
      пустой список; пустая строка/NULL; тег без id; вложенный мусор; текст
      без matn-тега.
- [x] Коммит `feat(backend): детерминированный парсер иснада из full_text_ar (alminasa)`

## Task 3: AlminasaNarratorMapper + AlminasaCollections

- [x] `mapNarrator(AmNarratorRow)`: re-parse raw → upsert по external_id
      (find→update|insert); поля по решениям 4-5 (nameAr=full_name, kunya=
      nickname, laqab=origin, lived_in→primaryResidence, died_in→deathPlace,
      tabaqa=level, gradeText=grade, bornOnText/diedOnText + best-effort hijri);
      relations delete-recreate из top_students(STUDENT)/top_scholars(SCHOLAR),
      parse `"имя - (N)"`.
- [x] `AlminasaCollections`: static map 12 сборников + `resolveOrCreate`
      (кэш в маппере не нужен — CollectionRepository.findBySlug дешёв, но
      in-memory cache на прогон допустим в ImportService).
- [x] Unit-тесты: маппинг полей с narrators.json; таблица enum-производной
      (صحابي/ثقة ثبت/صدوق/مقبول/ضعيف/متروك/мусор→UNKNOWN); хиджри из прозы
      (есть число/нет числа/سنة N); relations parse вкл. имя с дефисом.
- [x] Коммит `feat(backend): маппер рави alminasa staging→hd_narrators + relations`

## Task 4: AlminasaHadithMapper (ядро)

- [x] `mapHadith(AmHadithRow)` по решениям 1-3, 6-9, 12: re-parse raw →
      resolve/create collection → upsert hadith → delete-recreate матн/издания/
      цепь (реверс + формулы + resolve рави по реш. 3)/crossrefs (minus self,
      type='TARIQ', note=numbers сиблинга из narrations_numbers)/rulings
      (union+дедуп)/explanations (join сегментов).
- [x] `dryRunHadith(String hadithId)`: тот же маппинг в transaction с
      `setRollbackOnly` + вернуть снапшот результата (под План 5; без REST).
- [x] `AlminasaMapperIT` (Testcontainers): засеять staging фикстурами (146-1 +
      его narrator/ruling/explanation доки) → `mapHadith` → assert: hadith
      (external_id, type=مرفوع, chapter, full_text_ar, status=CANONICAL,
      collection slug=bukhari), primary matn (текст+normalized), editions=2,
      sanad: 6 звеньев, position 0=external 5913 (عمر, SAHABI), ПОЛНЫЙ вектор
      формул по позициям (реш. 2: pos0=سمعت, pos1=سمعت, pos2=سمع, pos3=أخبرني,
      pos4=حدثنا, pos5=حدثنا) + metadata.collectorPhrase=حدثنا + round-trip
      `SanadGraphService.buildGraph` (label ребра Prophet-стороны), crossrefs
      БЕЗ 146-1 и с note-номерами, rulings: РОВНО 2 строки (Бухари page 6
      vol 1 — embedded схлопнут с index; Муслим page 48 vol 6
      relatedExternalId=158-3537), explanations: 1×SHARH (фатх аль-Бари, text
      non-empty). Повторный mapHadith → те же counts (идемпотентность), hadith
      UUID стабилен.
- [x] Unit-тест дедупа рулингов изолированно (M5): embedded+index collapse,
      разные ruler'ы не схлопываются.
- [x] Edge-IT: хадис без rawy-тегов → без цепи, без ошибки; пустой матн → fail;
      коллизия (collection_id, primary_number) двух external_id → у второго
      primary_number=NULL, оба импортированы (I1).
- [x] Коммит `feat(backend): маппер хадисов alminasa staging→hd_* (ядро Плана 3)`

## Task 5: AlminasaImportService (orchestration + resolve)

- [x] `importNarrators()`: keyset-цикл по staging → mapNarrator в per-док
      транзакции (бин-граница); счётчики; cap-20 failures.
- [x] `importHadiths(Integer bookIdFilter)`: keyset-цикл (ORDER BY book_id,
      serial) → mapHadith; после цикла — `resolveCrossrefs()` (SQL) +
      `resolveNarratorRelations()` (Java, реш. 11б: in-memory map нормализ.
      имён, единственный кандидат); вернуть `AlminasaImportSummary`.
- [x] `AlminasaImportServiceIT`: полный e2e (нарраторы → хадисы 146-1 и 146-53 →
      resolve: crossref 146-1↔146-53 получил related_hadith_id, relation
      «الزهري» остался NULL-FK с именем (короткая форма не матчится — known
      limitation), позитивный Java-resolve кейс на синтетическом точном имени;
      битый raw JSON одного дока → failed=1, остальные импортированы;
      повторный полный прогон → row-counts стабильны.
- [x] Perf-примечание в javadoc ImportService: 82k × per-док tx — one-shot
      admin-операция, минуты-десятки минут приемлемы; НЕ оптимизировать
      преждевременно (батчинг — если живой прогон покажет боль).
- [x] Коммит `feat(backend): orchestration импорта alminasa + resolve-проход FK`

## Task 6: доки + финальная верификация

- [x] `docs/architecture.md`: маппинг-пайплайн (staging→hd_*, двухпроходность,
      resolve), ссылка на план.
- [x] `docs/glossary.md`: такхридж/طرق, табака, джарх ва та'диль (если
      отсутствуют).
- [x] gotcha при любой находке времени исполнения.
- [x] Полный `./mvnw verify` (граница плана) — BUILD SUCCESS.
- [x] Коммит `docs(backend): архитектура маппинга alminasa (План 3)`
- [x] Независимый review (BASE=HEAD перед Task 1) → Critical/Important →
      fix-коммиты → чекбоксы/handoff.

## Верификация плана (Definition of Done)

1. `./mvnw verify` зелёный, новые тесты: парсер ≥6, narrator-маппер ≥4, IT ≥8.
2. e2e: фикстурный 146-1 проходит staging→hd_* со ВСЕМИ сателлитами.
3. Идемпотентность доказана IT (повторный прогон без дублей).
4. Независимый review: 0 открытых Critical/Important.
5. РУНТАЙМ-данные alminasa НЕ требуются (фикстуры); dev-краул НЕ нужен для
   тестов (фикстуры покрывают; live dry-run — на этапе Плана 5 руками).
