# Design Audit - baseline 2026-05-17

Аудит всех 9 экранов фронта + 4 состояний графа на основе скриншотов в `/tmp/screenshots-20260517-0637/`. Цель - **baseline для systematic
design pass** по итогам ADR-018 platform pivot и редизайна `AdminShamelaPage`.

Приоритеты:
- **P0** broken layout / unusable
- **P1** inconsistency между экранами (одни и те же паттерны рендерятся по-разному)
- **P2** polish / refinement (визуально работает, но можно лучше)

## Сводка экранов

| # | Экран | P0 | P1 | P2 | Состояние |
|---|---|---|---|---|---|
| 01 | TopicListPage | - | hero pattern | uneven card heights, pill counter collide | хорошее, требует header polish |
| 02 | CreateTopicPage | - | hero pattern, form layout | - | хорошее, hint-panel справа - **золотой эталон** для form pages |
| 03 | TopicGraphPage | - | - | top bar title size | **flagship экран**, не ломать, только polish |
| 04 | NodeDetailsPanel | - | - | section headers consistency | хорошо |
| 04b | EdgeDetailsPanel | - | - | то же что 04 | хорошо |
| 04c | AddNodeModal | - | - | - | **эталон modal**, образец |
| 05 | CommandPalette | - | - | - | **эталон UX**, образец для всех modals |
| 06 | BookListPage | - | hero pattern, primary CTA variant | book covers очень большие (250×250) | хорошее, нужен alignment с topics-list |
| 07 | BookReaderPage | - | - | - | **уже использует eyebrow + arabic h1** - образец |
| 08 | QuestionListPage | - | hero pattern, count display, sparse layout | item cards неинформативны | требует rework hero + богаче cards |
| 09 | CreateQuestionPage | - | hero pattern, form layout (no hint panel) | - | требует подтянуть к CreateTopicPage |
| 10 | QuestionDetailPage | - | hero pattern, section headers | gap consistency | требует hero rework, секции ок |
| 11 | AdminShamelaPage | - | - | - | **только что сделано** - reference для остальных |
| 12 | NotFoundError | weak error state | - | - | требует illustrated empty state |

## Cross-cutting проблемы (повторяются на нескольких экранах)

### 1. Hero pattern не унифицирован (P1)

Сейчас три разных стиля заголовков:

| Экран | Стиль |
|---|---|
| **admin** (после редизайна) | eyebrow `АДМИН · ИМПОРТ` + serif h1 + sub-line |
| **book reader** | eyebrow `КНИГА · 472 СТР.` + bold h1 + meta |
| **topics/books/qa/forms** | bold sans h1 + sub-line (без eyebrow) |

**Решение**: применить admin/reader pattern (eyebrow + serif h1 + sub-line) ко **всем** hero-секциям list/form/detail pages. Это **главный** объединяющий фикс.

### 2. Count display inconsistency (P1)

| Экран | Как показывается count |
|---|---|
| topics-list | sub-line «Структурированные дискуссии · `7 активных` pill» |
| books-list | sub-line «Импортированные классические труды · `4 книг` pill» |
| qa-list | sub-line «`2 вопросов` в обсуждении» (без pill) |
| admin | в 5-метричной strip |

**Решение**: для list pages унифицировать sub-line формат `[descriptor] · [N suffix]` с inline числом без pill. Pill counter (badge) перегружает и конкурирует с другими chip'ами.

### 3. Primary CTA variant (P1)

| Экран | Кнопка |
|---|---|
| topics-list | `+ Создать тему` **primary** (indigo) |
| books-list | `Импорт из Shamela` **secondary** (white) |
| qa-list | `+ Задать вопрос` **primary** (indigo) |

Books-list использует secondary для CTA - inconsistent. Возможно осознанно (импорт - редкая операция), но визуально книги выглядят менее «proactive». **Решение**: либо все list pages с primary CTA, либо документировать почему books иначе.

### 4. Form pattern hint panel (P1)

| Экран | Hint panel справа |
|---|---|
| CreateTopicPage | **да** - «СОВЕТ» с примером хорошего вопроса |
| CreateQuestionPage | **нет** - просто два поля |

CreateTopicPage показывает **золотой паттерн**: form слева, contextual help справа. CreateQuestionPage должен следовать тому же подходу - подсказка «Что делает вопрос хорошим» с примером (как принято на StackOverflow). **Решение**: добавить hint panel в CreateQuestionPage.

### 5. Empty / error states (P0 для not-found, P1 для list pages)

| Состояние | Сейчас |
|---|---|
| `/topics/{невалидный}` | плоская красная карточка с raw error text |
| `topics-list` пустой | (не виден на скриншоте - проверить) |
| `qa-list` с 2 элементами | список выглядит пустым на огромной странице |

**Решение**:
- `not-found`: illustrated panel + понятное «Тема не найдена» + CTA «← К списку тем», без UUID наружу
- `qa-list` (когда мало data): добавить empty-state-aware рекомендации или подсказки

### 6. Сегмент Search + Filters (P2)

Поиск + filter chips - реализованы по-разному:

| Экран | Раскладка |
|---|---|
| topics-list | только search input |
| books-list | search + type chips (Все/Книга/...) + sort dropdown |
| qa-list | search + status chips (Все/Открытые/...) |
| admin | hero search с glow + match count |

Каждая раскладка **локально валидна**, но они выглядят как разные «дизайн-системы». Можно подтянуть topics-list к books/qa варианту - добавить сортировку (по дате / по нодам), и тогда три list-page становятся parallel.

## Приоритизация для Фазы 3

**Q1 (must)** - cross-cutting hero pattern на 4 экранах
1. `TopicListPage` - eyebrow + serif h1
2. `BookListPage` - eyebrow + serif h1, унифицировать primary CTA
3. `QuestionListPage` - eyebrow + serif h1, доинформатизировать item cards
4. `CreateTopicPage` + `CreateQuestionPage` + `QuestionDetailPage` - eyebrow + serif h1

**Q2 (should)** - empty/error states
5. `NotFoundError` (404 на `/topics/{bad-id}` и аналоги) - illustrated panel

**Q3 (could)** - form parity
6. `CreateQuestionPage` - добавить hint panel как в CreateTopicPage

**Q4 (nice)** - polish
7. `TopicGraphPage` top bar - чуть editorial topic title (serif)
8. `QuestionDetailPage` - проверить гэпы между секциями
9. Unified search+filter pattern на 3 list pages

## Что НЕ трогаем

- **TopicGraphPage** граф - flagship UI, дизайн хороший, ломать не надо
- **AddNodeModal / EdgeDetailsPanel / NodeDetailsPanel** - работают как эталон
- **CommandPalette** - эталон, образец для других modals
- **BookReaderPage** - editorial pattern уже используется
- **Header / nav** - cross-cutting компонент, любые изменения требуют отдельной оценки

## Эталоны для копирования

Если сомневаешься как сделать что-то - смотри сначала на эти экраны/компоненты:

- **hero pattern**: AdminShamelaPage или BookReaderPage
- **modal**: AddNodeModal или CommandPalette
- **right panel**: NodeDetailsPanel или EdgeDetailsPanel
- **section header**: «СВЯЗЬ», «МЕТАДАННЫЕ» в EdgeDetailsPanel (uppercase eyebrow стиль)
- **arabic title**: BookListPage card cover или BookReaderPage h1
