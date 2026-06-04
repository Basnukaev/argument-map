# Линковка хадисов в узлы тем (под-проект #2) — Design Spec

**Дата:** 2026-06-01 (Сессия 53)
**Контекст:** пивот Абдулы — строим инструменты для заполнения контента.
Узлы графа аргументации должны уметь ссылаться на хадисы из `hd_*` как на
опору (مُسْتَنَدٌ). Дизайн утверждён (Абдула делегировал выбор).
**Связанные:** ADR-017 (трёхуровневая citation), ADR-043 (permissions),
`2026-06-01-hadith-viewing-tool-design.md` (#1).

## 1. Архитектура — переиспользуем мост `Source`

Узлы цитируют `sources` через `node_sources` (НЕ сущности напрямую).
`Source.sourceType` уже включает `HADITH`, а `Hadith.sourceId → sources.id` —
мост, заложенный ровно для citation UI. Поэтому **хадис-цитата = обычная
опора-источник**, без параллельной node↔hadith таблицы.

**Поток прикрепления:**
1. `ensureSourceForHadith(hadith)`: если `hadith.sourceId` пуст — создать
   `Source(sourceType=HADITH, title="<сборник> №<номер>", reliability=<grade?>)`
   и выставить `hd_hadiths.source_id`. Иначе — взять существующий.
2. `NodeSourceService.attachSource(nodeId, sourceId, actor, role)` (существующий).
3. Хадис появляется в секции «Опора» узла как source-цитата.

## 2. Бэкенд

- **`HadithCitationService`** (`hadith.service` или `service`):
  - `attachHadithToNode(nodeId, hadithId, actorUserId, actorRole)`:
    ensureSource + attachSource. authz — внутри attachSource (assertCanWrite
    на тему узла, ADR-043). 404 если хадиса нет.
  - `ensureSourceForHadith(Hadith)`: find-or-create + set source_id (идемпотентно).
- **`POST /api/v1/nodes/{nodeId}/hadith-citations`** body `{hadithId}` → attach.
  Возвращает `HadithCitationResponse {nodeSourceId, hadithId, sourceId,
  collectionName, primaryNumber, previewMatn}`.
- **Список:** хадис-опоры едут в существующем `GET /nodes/{id}/sources` (один
  список «Опора»). Для HADITH-источников **обогащаем** ответ полями
  `hadithId` + `previewMatn` + `collectionName` + `primaryNumber` (reverse-
  lookup `hd_hadiths by source_id`, batch). Не-хадис источники — поля null.
- **Detach:** существующий `DELETE /nodes/{id}/sources/{nodeSourceId}`.
- `HadithRepository.findBySourceIds(List<UUID>)` (batch reverse-lookup).

## 3. Фронт

⚠️ **Предусловие:** §2 backend-обогащение (`NodeSourceResponse.hadithId/
previewMatn/...` + `HadithRepository.findBySourceIds`) ДОЛЖНО быть сделано и
прогнано `generate-api` ДО фронта — иначе рендерить нечего. `#2.A` (только
attach-endpoint) этого НЕ включает.

- В секции «Опора» (`NodeCitationsSection.tsx`, `apps/argument-map/components/
  graph/`): 3-я кнопка «прикрепить хадис» рядом с существующими (которые
  открывают `CitationPicker` для library/freeform).
- **Picker — НОВАЯ модалка `HadithPickerModal`** (идиома `{open && <Modal/>}`).
  `HadithListPage` НЕ переиспользуется напрямую (это full-page: `<Header/>` +
  `<Link>`-навигация, нет `onSelect`/reusable export). Переиспользовать **хук
  `usePagedSearch` + `GET /api/v1/hadith/hadiths`** (q/status/collectionId/sort,
  `PagedResponse<HadithResponse>`, previewMatn на карточке) с
  `onSelect(hadithId)` вместо Link. Структурный референс — `CitationPicker.tsx`
  (модалка цитирования), паттерн attach-then-reload — `AddSourceModal`.
- На выбор → `POST /nodes/{id}/hadith-citations {hadithId}` → reload опор.
- Рендер: ветка HADITH-опоры в `CitationsList` (по `hadithId` из обогащённого
  source-ответа, ПЕРЕД `FreeformCite` fallback) — карточка «сборник·№ +
  matn-сниппет (naskh/RTL) + ссылка на `/hadith/{hadithId}`».

## 4. Решения (зафиксированы)

1. Мост `Source` (НЕ новая таблица) — переиспользуем `Hadith.sourceId`.
2. Picker — модалка, переиспользует список хадисов (#1).
3. Хадис-опора в общем списке «Опора», не отдельным списком.
4. ensureSource идемпотентен (один Source на хадис).

## 5. Out of scope

- Иснад-граф на узле (под-проект #4).
- Freeform-хадисы (только из `hd_*`).
- Цитирование конкретного matn-варианта / диапазона (на узле — весь хадис).

## 6. Тестирование

- `HadithCitationServiceIT`: attach создаёт source + node_source; повторный
  attach идемпотентен (тот же source); 404 нет хадиса; authz.
- `NodeSource` list IT: HADITH-источник обогащён hadithId/previewMatn.
- Frontend: picker-модалка (выбор → POST), HadithCite рендер + ссылка.
