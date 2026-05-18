# Frontend Architecture Audit 2026-05-18 (deep)

Глубокий архитектурный аудит `frontend/src/` - не базовые smells
(`any`/`enum` уже cleaned в Cleanup Marathon 2026-05-11 и косметическом
audit того же дня), а **structural patterns**: code-splitting, error
isolation, store consolidation, cross-app boundaries, god-component
decomposition, prop drilling, data-fetching hooks.

Baseline: 489 vitest pass, main bundle `index.js` 1,051.80 kB / 306.77 kB
gzip + `TopicGraphPage` lazy chunk 406.19 kB / 127.65 kB gzip +
`PdfViewer` lazy chunk 469.87 kB / 139.20 kB gzip.

## Critical findings

### C-1. Нет ни одного ErrorBoundary

```
grep -rn "componentDidCatch\|<ErrorBoundary\|getDerivedStateFromError" src/  →  0 matches
```

Любой runtime error в любом компоненте обрушивает всё SPA до blank
screen. React 19 не имеет встроенного error fallback - need explicit
`ErrorBoundary` class. Особенно болезненно для:

- `TopicGraphPage` - React Flow + ELK / dagre layout; ошибка в layout
  algorithm => весь UI слетает
- `BookReaderPage` - lazy PdfViewer; ошибка в react-pdf => весь UI слетает
- `AdminPageEditorPage` - Tiptap editor; ошибка в любом extension =>
  весь UI слетает

**Fix:** добавить `<ErrorBoundary>` класс-компонент в `shared/components/`
и обернуть им App или каждый Route. Top-level fallback с reload + report
button - baseline.

### C-2. Большой main bundle: heavy admin/reader/QA в initial chunk

`index.js` 1,051 kB (306 kB gzip). React Flow и PDF.js уже в lazy chunks,
но **Tiptap** (`AdminPageEditorPage`), **AdminShamelaPage**,
**AdminAuditPage**, **QA pages**, **BookListPage**, **BookReaderPage**
до сих пор статически импортируются в `App.tsx`. User заходящий на
`/topics` тащит весь admin/QA/library code.

Только `TopicGraphPage` lazy. **Fix:** перевести все нестартовые pages
на `lazy(() => import(...))` - значительно сократит initial bundle для
USER role (admin pages вообще не нужны).

## Important findings

### I-1. Cross-app boundary violations: admin ↔ library

`apps/admin/components/BookEditModal.tsx:9` импортирует
`BookMembersModal` из `apps/library`, а
`apps/library/pages/BookListPage.tsx:16` импортирует `BookEditModal`
из `apps/admin`. Двусторонняя зависимость между apps - violation ADR-022
boundary (apps друг с другом общаются только через `shared/`).

**Fix:** переместить `BookMembersModal` в `shared/components/library/`
либо в `shared/components/` (он generic membership modal, не library-
specific) и/или `BookEditModal` тоже. Минимально - сломать circular
import переносом одного компонента в `shared/`.

### I-2. God components - 9 files >500 LOC

```
AdminPageEditorPage     1077 LOC
GraphCanvas             1010 LOC
BookReaderPage           740 LOC
AdminShamelaPage         733 LOC
BookListPage             699 LOC
TopicSettingsDrawer      636 LOC
HadithGradesSection      600 LOC
AdminAuditPage           589 LOC
CitationPicker           536 LOC
```

`GraphCanvas` - state-машина с >15 useState + 10+ useCallback handlers +
3 модалки + 2 деталь-панели. Кандидаты на extract:

- `useGraphSelection` - selectedNodeIds/selectedEdgeIds + clearSelection
  + handleSelectionChange + sync в graphSelectionStore
- `useGraphZOrder` - bringNodeToFront/sendNodeToBack/bringEdgeToFront/
  sendEdgeToBack + edgeZRef + persistence (~40 LOC)
- `useGraphDelete` - runDelete + restoreNodeFromSnapshot + deleteOne*
  + undo toast (~100 LOC)
- `useGraphHotkeys` - useGraphEscape + useHotkey(del/backspace) (уже
  выделено частично)

В этом аудите делаем `useGraphZOrder` extract как proof-of-concept (small,
self-contained, no breaking API).

### I-3. `useApiQuery` существует, но используется 0 раз

`shared/hooks/useApiQuery.ts` - generic fetch hook с AbortController +
AsyncState discriminated union. Используется только в **самом файле**
(в JSDoc'е) - все компоненты копируют `useEffect + useState + apiGetRaw +
abort controller` руками. Минимум 18 файлов с этим pattern'ом.

**Fix не в этом аудите** (migration 18 файлов = риск регрессий).
Документировать как rec для следующего refactor sprint в backlog.

### I-4. Theme split между themeStore и preferencesStore

`useThemeStore` (mode/effectiveTheme/setMode + matchMedia system listener)
+ `usePreferencesStore.theme` (backend-persisted + cache в localStorage).
`PreferencesEffect` синхронизирует preferences → themeStore. **Это не
дубликат**, это правильное layering: prefs - persistence layer,
themeStore - runtime UI с system-prefs reactivity. Документировать
inline JSDoc что они НЕ дубликаты (issue для нового contributor'а).

**Не fix.**

## Minor findings

### M-1. Suspense fallbacks - generic "Загрузка графа"

`App.tsx:33` - `<GraphFallback>` показывает только `Загрузка графа`.
Без skeleton'а / shimmer'а - cold start выглядит сломанным. Не критично
для MVP.

### M-2. Heavy hooks без cleanup pattern

7 `useEffect` без явного cleanup в production code (включая API requests
без AbortController). Большинство - one-shot logic; реальный leak risk
в `OnboardingChecklist`, `TopicMembersModal` (polling-like). Не критично.

### M-3. Prop drilling: canWrite через GraphCanvas → GraphPanels → ...

`canWrite` пропагируется через 3-4 уровня в graph компоненты. Можно
вынести в context либо в graphSelectionStore (но он про selection -
смешивать domain'ы плохо). Альтернатива - `useTopicPermissions()` hook
читающий из контекста темы. **Не fix в этом аудите** - канал поведения
ясный, refactor рискованный.

### M-4. Compound components: Card уже compound, остальные нет

`Card.Cover / Card.Body / Card.Eyebrow / Card.Title / Card.Meta` -
compound pattern есть. Modal / Dialog / Tabs - можно перевести, но
сейчас они используются однотипно и compound API не упростит call sites.
**Не fix.**

### M-5. Type duplication: openapi types vs domain types

`shared/api/types.ts` - 5,122 LOC autogenerated. Множество мест делают
`components['schemas']['XResponse']` и переопределяют (например
`AdminPageEditorPage` переопределяет `formattedContent` так как openapi
неточно маппит JsonNode). Это legit override, не duplication.

## Actions taken в этом аудите

1. **Top-level ErrorBoundary** (C-1) - `shared/components/ErrorBoundary.tsx`
   + интеграция в `App.tsx`. Fallback UI с reload-кнопкой.
2. **Route-level lazy loading** (C-2) - все pages кроме `LoginPage` /
   `RegisterPage` / `TopicListPage` (стартовая) переведены на `lazy()`.
   Heavy admin/QA/library/reader chunks выпадают из initial bundle.
3. **Cross-app boundary fix** (I-1) - `BookMembersModal` переезжает в
   `shared/components/library/`; `BookEditModal` остаётся в admin (он
   только admin-only); library больше не зависит от admin.
4. **GraphCanvas split** (I-2) - extract `useGraphZOrder` hook.

ADR-045 (route-level lazy loading) задокументирует решение
code-splitting и его trade-offs.

## Что отложено

- I-2 полная декомпозиция (`useGraphDelete`, `useGraphSelection`) -
  риск регрессий в hot path. Требует separate session.
- I-3 миграция на `useApiQuery` для 18+ файлов - backlog.
- M-1 skeleton loaders - UX polish, отдельный sprint.
- M-3 canWrite context - low priority, текущий drilling прозрачен.

## Acceptance после fix'ов

- `npm run lint && npm run build && npx tsc --noEmit -p tsconfig.app.json && npm run test:run` clean
- vitest 489+ (без регрессий)
- initial bundle index.js значительно сокращается (heavy pages в lazy chunks)
- ErrorBoundary покрывает все routes
- нет cross-app imports admin↔library
