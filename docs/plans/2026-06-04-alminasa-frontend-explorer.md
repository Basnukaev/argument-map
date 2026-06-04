# alminasa Hadith Ingestion — Plan 6: Hadith Explorer на alminasa-данных

> **Оркестрация:** OMC. ГЕЙТ: вкладки علل (иляль) / غريب (гариб) — контракты
> ES-индексов НЕ сняты (нет HAR) → НЕ реализуются и НЕ выдумываются;
> фиксируются как «ждёт HAR от Абдулы». Всё остальное — в скоупе.

**Goal:** фронт Hadith Explorer раскрывает богатые alminasa-данные из `hd_*`:
тип хадиса, глава, **кликабельный иснад** (рендер `full_text_ar`), печатные
издания, вердикты учёных (rulings), шархи, такхридж/طرق (crossrefs), у рави —
табака/джарх-та'диль/сеть передатчиков. Вместо прежнего sunnah-сэмпла.

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §E.
**База:** разведка API (карта ниже выверена живым кодом), Планы 1-5.

## Карта изменений backend (web-слой; миграции НЕ нужны)

Существующие endpoints НЕ переименовываются — расширяются DTO:

1. **`HadithDetailResponse` +7 полей**: `hadithType`, `chapterAr`,
   `subChapterAr`, `fullTextAr`, `editions: List<EditionDto>`,
   `rulings: List<RulingDto>`, `explanations: List<ExplanationDto>`,
   `crossrefs: List<CrossrefDto>` (такхридж — В СКОУПЕ, пользовательский
   запрос). `HadithController.getDetail` батч-грузит 4 репозитория Плана 1
   (`findByHadithId` × editions/rulings/explanations/crossrefs — по одному
   запросу каждый, это single-detail, не list: N+1 нет).
2. **`NarratorResponse` +6 полей**: `tabaqa`, `gradeText`, `bornOnText`,
   `diedOnText`, `deathPlace` (поле домена ЕСТЬ, в DTO не было),
   `relations: List<NarratorRelationDto>` — ТОЛЬКО в getOne (detail);
   list-эндпоинт relations не грузит (null) — без N+1.
3. **`SanadGraphResponse.NarratorData` +3 поля**: `tabaqa`, `gradeText`,
   `externalId` (для клик-резолва иснада на фронте). Сеть передатчиков в
   узлы графа НЕ кладём (раздувание; relations живут в NarratorPanel
   detail-фетчем).
4. Новые DTO: `EditionDto`, `RulingDto` (rulerName, rulerDeathYear,
   rulingText, bookName, page, volume), `ExplanationDto` (kind, bookName,
   author, page, volume, text), `CrossrefDto` (relatedExternalId,
   relatedHadithId nullable, note), `NarratorRelationDto`
   (relatedNarratorId nullable, relatedName, role, cnt).
5. `NarratorRelationRepository.findByNarratorId` есть; батч-метод НЕ нужен
   (relations только в narrator-detail).
6. IT: расширить `HadithControllerIT`/`NarratorControllerIT` — detail
   alminasa-хадиса возвращает новые поля (фикстура через репозитории),
   пустые списки для хадиса без сателлитов; sanad-graph отдаёт externalId.
7. api-contract.md: обновить 3 секции (detail/narrator/sanad-graph).

## Карта изменений frontend

`frontend/src/apps/hadith/` (структура из разведки: pages + components):

1. **HadithDetailPage**:
   - бейдж `hadithType` (مرفوع/موقوف/…) + `chapterAr`/`subChapterAr` в
     шапке (RTL, `hasArabicScript`);
   - **секция «Иснад (текст)»**: рендер `fullTextAr` — парс
     `<a class=rawy id=N>` в React-элементы (НЕ dangerouslySetInnerHTML;
     util `parseIsnadHtml.ts` + тест), клик по рави → NarratorPanel
     (резолв external id → narratorId по карте из sanad-graph nodes
     externalId; нет в карте → не-кликабельный span); `<a class=matn>` —
     выделение матна стилем;
   - **секция «Вердикты» (rulings)**: список ruler + (ум. N г.х.) + текст
     + книга/страница;
   - **секция «Шарх»**: explanations kind=SHARH, collapsible (текст до
     59KB!), book/author в заголовке; kind=ILAL/GHARIB пока не приходят
     (гейт) — рендер generic по kind, ничего не выдумывать;
   - **секция «Такхридж»**: crossrefs — «передаётся в N местах»;
     resolved (`relatedHadithId != null`) → линк на detail сиблинга,
     unresolved → текст external id + note-номера;
   - **«Издания»**: компактный список editions.
   - Все секции graceful при пустых данных (legacy/без сателлитов —
     секция скрыта).
2. **NarratorPanel / NarratorDetailPage**: табака, джарх-та'диль verbatim
   (gradeText), born/died проза, deathPlace; **сеть передатчиков**:
   relations STUDENT/SCHOLAR списками с cnt, resolved → линк на рави.
3. **HadithListPage**: фильтр по сборнику уже есть (collectionId) —
   проверить, что 12 alminasa-коллекций появляются в фильтре
   (CollectionResponse без изменений); бейдж типа в карточке списка НЕ
   добавляем (list-DTO не расширяем — экономия; тип виден в detail).
4. **Вкладки علل/غريب**: НЕ реализуются. В UI НЕ показывать пустые табы.
   Зафиксировано «ждёт HAR» (handoff).
5. i18n `hadith.detail.*` новые ключи ru+ar; vitest: parseIsnadHtml unit,
   detail-секции рендер с MSW-фикстурой (alminasa-подобный ответ),
   клик-резолв рави, graceful-пустота.

## Tasks

- [ ] **T1 backend** (коммит 1): DTO-расширения + 5 новых DTO +
  контроллеры + SanadGraphService externalId/tabaqa/gradeText + IT +
  api-contract.md.
- [ ] **T2 regen** (коммит 2): backend up → generate-api → tsc.
- [ ] **T3 frontend** (коммит 3): parseIsnadHtml + секции
  HadithDetailPage + NarratorPanel/Detail + i18n + тесты.
- [ ] **T4 верификация**: verify + vitest + tsc; playwright headless по
  живым данным (дев-краул, как План 5 T4, совместить прогон с ним если
  Планы идут подряд) — скриншот detail с кликабельным иснадом; очистка
  dev-данных (SQL Плана 5 T4).
- [ ] **T5 review**: независимый review → fixes → roadmap.

## Definition of Done

1. Detail хадиса 146-1 (dev-краул) показывает: тип, главу, кликабельный
   иснад (клик открывает карточку рави), 2 вердикта, шарх, такхридж,
   2 издания.
2. У рави: табака + verbatim джарх + сеть передатчиков.
3. علل/غريب отсутствуют в UI; зафиксированы «ждёт HAR» в handoff.
4. verify + vitest + tsc зелёные; review 0 Critical/Important.
