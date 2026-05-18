# Frontend Audit 2026-05-18

Кодовая база frontend (`frontend/src/`) после ~37 сессий. Цель -
выявить кандидатов на cleanup без изменения public API компонентов
и без breaking changes для тестов.

## Метрики

| Метрика | Значение |
|---------|----------|
| TSX файлов всего (без test) | ~150+ |
| Test файлов (`.test.*`) | 65 |
| Vitest passed | 468 |
| Largest tsx file | AdminPageEditorPage 1077 LOC |
| Components >500 LOC | 9 |
| Components 200-500 LOC | ~20 |
| `any` usage (production code) | 0 (1 в test с eslint-disable + 1 в комментарии) |
| `enum` usage | 0 |
| Физические Tailwind направленные (`pl-`/`pr-`/`ml-`/`mr-`/`text-left`) | 2 (оба в NodeCard, документировано) |
| `console.log` / `debugger` в production | 0 |
| `useMemo` / `useCallback` | 88 occurrences |
| Index keys в списках | 7 - все оправданы (parser segments, split, static SVG) |
| Hardcoded cyrillic UI strings | 8 (в CreateTopicPage) |

## Critical (must fix) - 0

Не найдено: проект в очень хорошем состоянии после Cleanup Marathon
2026-05-11. Все ранее критические находки (large components,
duplicate patterns, no-any policy) закрыты или сильно ослаблены.

## Important (should fix)

### I-1. Hardcoded cyrillic strings в CreateTopicPage

`src/apps/argument-map/pages/CreateTopicPage.tsx` содержит 8 hardcoded
русских строк в JSX: labels/hints Field'ов, кнопки «Создать»/«Отмена»,
текст подсказки в aside. Эти строки должны идти через `useT()` для
поддержки локалей en/ar (Этап 28 i18n был frozen, но прецеденты в
других страницах - все через `t()`).

Затронуто:
- `label="Название"` / `hint="Краткая формулировка темы дискуссии"`
- `label="Описание"` / `hint="Необязательно. Поможет другим..."`
- `label="Корневой вопрос"` / `hint="Это станет корневым QUESTION-узлом графа"`
- `submitting ? 'Создаём' : 'Создать'`
- `'Отмена'`
- `'Совет'`, текст «Хороший корневой вопрос...» и пример

### I-2. Hardcoded `aria-label="Локаль интерфейса"` в LocaleSwitch

`src/shared/components/layout/LocaleSwitch.tsx:26` - hardcoded русская
aria-label. Должна идти через `useT()`. Это компонент переключения
локали, ironic.

### I-3. `aria-label` в Локаль-switch'е требует proper i18n

Тот же файл, та же проблема. См. I-2.

### I-4. Большие компоненты без явных subcomponent splits

`AdminPageEditorPage` (1077 LOC), `GraphCanvas` (1010 LOC),
`BookReaderPage` (742 LOC), `AdminShamelaPage` (733 LOC),
`BookListPage` (699 LOC), `TopicSettingsDrawer` (636 LOC) - все
>500 LOC, превышают порог из coding-standards.md.

GraphCanvas и BookReaderPage были предметом F-01/F-02 в audit
2026-05-11 - но findings были помечены в Cleanup Marathon Phase 2,
проверить статус. Если работы по split'у были выполнены, заходить
с нового угла нет смысла. Если нет - **этот audit НЕ берёт на себя
эту работу** (превышает scope cleanup-only без больших refactor'ов).

Помечаю как **отложено** в backlog.

## Minor (nice to have)

### M-1. `as any` в test с обоснованием

`NodeCard.test.tsx:75` - использует `as any` с `eslint-disable` и
комментарием объясняющим почему (NodeProps в @xyflow/react имеет
много полей, для тестов нужны только data + selected). Это правильный
паттерн, **не fix**.

### M-2. Index keys в FloatingActionBar / InlineCitationBody / TopicListPage / AdminShamelaPage

Все 7 случаев - parser segments либо static visualizations (SVG dots
в TopicListPage, placeholder log в AdminShamelaPage). Не требуют fix.

### M-3. UI компоненты без тестов

Shared UI primitives без unit-тестов:
- `Badge` - generic chip с tone variants
- `Card` (+ Card.Cover, Card.Body, Card.Eyebrow, Card.Title, Card.Meta)
- `Chip` - generic inline label
- `Field` (+ Field.Input, Field.Textarea, Field.Meta)
- `FormModal`
- `IconButton`
- `Kbd`
- `Select`
- `StatusBadge`
- `TypeChip`

Большинство простые, но `Field` (составной с Context API, ARIA-аттрибуты)
и `IconButton` (a11y label / title contract) - high-value для
тестирования. `StatusBadge` имеет логику через STATUS_TOKENS.

### M-4. ESLint disable'ы в hooks/state-in-effect

5 случаев `react-hooks/set-state-in-effect` disable'ов:
- `OnboardingChecklist.tsx:48`
- `useOnboardingProgress.ts:130`
- `useApiQuery.ts:44`
- `TopicMembersModal.tsx:89`
- `BookReaderPage.tsx:144`

Все имеют комментарии-обоснования. Это idiomatic pattern проекта
(см. CLAUDE.md - `conditional render для одноразовых модалок` обходит
этот lint). Не требуют fix.

### M-5. `useMemo`/`useCallback` без явной reason

88 occurrences. Случайный sampling показал что почти все имеют:
- комментарий-обоснование (стабильная ref для deps, parser cache)
- RF context (`nodeTypes`/`edgeTypes` - documented exception)
- `useCallback` для refetch/cancel-функций для включения в deps

Точечная очистка возможна в TopicGraphPage / BookListPage, но без
profiler measure это спекуляция. **Не fix в этом audit.**

## Recommendations

1. **Закрыть I-1 / I-2** одним коммитом - i18n cleanup CreateTopicPage
   + LocaleSwitch. Это улучшает консистентность и подготавливает
   страницу к multi-locale поддержке (когда вернёмся к Этапу 28).
2. **Добавить tests на high-value UI primitives** - Field, IconButton,
   StatusBadge, FormModal. По 1 тестовому файлу на компонент с
   accessibility / variant tests.
3. **Документировать критерий index-as-key exceptions** в
   coding-standards.md - чтобы reviewer не флагал parser segments.
4. **Большие компоненты (I-4)** - не в scope этого audit, добавить
   в backlog для отдельной сессии.

## Что НЕ нашли (проверки прошли чисто)

- `: any` / `as any` / `<any>` - **0** в production
- TypeScript `enum` - **0**
- Физические Tailwind направленные классы - **0** вне документированных исключений
- `console.log` / `console.debug` / `debugger` - **0** в production
- Class components - **0**
- Отдельные `.css` / `.scss` файлы - **0**

Это говорит о высокой дисциплине после Cleanup Marathon 2026-05-11.

## Acceptance после fix'ов

- `npm run lint && npm run build && npx tsc --noEmit -p tsconfig.app.json && npm run test:run` clean
- Vitest count 468+ (рост от новых тестов)
- 5-10 атомарных commits `refactor(frontend)` / `test(frontend)` / `docs(frontend)`
