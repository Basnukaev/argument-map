# Бэклог

Идеи и задачи без привязки к активному этапу. Не закрытые - в
`docs/roadmap.md`. Закрытые в общем виде - в `docs/progress.md`

Когда задача созревает (становится приоритетной или блокирует
другую) - переезжает в новый Этап в `roadmap.md`

## Фронт - общие улучшения

- [ ] **Шрифт title книг в BookListPage** - текущий не нравится
  Абдуле. Подобрать более красивый serif для title (возможно
  Source Serif 4 уже подключённый, либо новый - PT Serif/Lora/
  EB Garamond/Crimson Text). Reference - скрин от Абдулы
  `BookListPage_(1).png` (16 мая). Заодно проверить spacing между
  заголовком и метаданными карточки
- [x] **Фикс 12 pre-existing test failures** - закрыто в Сессии 36
  через ruflo test-regression-diagnoser subagent. Root cause найдено
  и verified: **Node 24 + undici 7 AbortSignal instanceof bug**
  (nodejs/undici#2596, nodejs/node#56644). Это **внешний bug**, не
  наша проблема. Гипотеза про React 19 + act() отвергнута. Fix -
  monkey-patch `globalThis.fetch` в `frontend/src/test-setup.ts`
  beforeAll() после `server.listen()` чтобы strip `signal` из
  RequestInit. После fix - 142/143 passes (12 регрессий все
  восстановлены, остался 1 unrelated pre-existing fail в
  AddSourceModal.test.tsx про reliability radio). Полный gotcha с
  reproducer + альтернативами + рисками - в `docs/gotchas.md` секция
  «Node 24 + undici 7 - AbortSignal instanceof check».
- [ ] Полнотекстовый поиск (когда появится на беке, Этап 6)
- [ ] Экспорт графа в PNG / SVG
- [ ] Тёмная тема
- [ ] Локализация (i18n) при появлении второй локали
- [ ] **Smart edge routing** (опционально, если 4-handles + dagre
      мало) - elkjs или custom edge с pathfinding
- [ ] **Z-index full-stack persistence** для узлов и рёбер
      (миграция + поле + DTO + фронт). Сейчас локально, при
      refetch теряется. Делать только если станет критично -
      z-order между сессиями редко важен

## Responsive / mobile-планшетная адаптация

UI сейчас спроектирован под desktop (viewport 1280+). При работе
над mobile/tablet нужно пересмотреть:

- **Select.maxVisibleItems** в `shared/components/ui/Select.tsx` -
  сейчас default 12 (без scrollbar при ≤12 опций). На мелком
  viewport или с большим zoom 12 опций могут не уместиться
  вертикально - получим overflow без scrollbar. Сделать adaptive:
  либо count меньше для small screens (через breakpoint hook), либо
  CSS-based max-height через `min(64rem, 50vh)` чтобы scrollbar
  appearance зависел от реальной высоты viewport, не от count
- **BookReaderPage layout** - двухколонник 280px sidebar + main
  сейчас. На mobile нужно либо drawer/sheet для chapters tree, либо
  bottom-tabs. PdfViewer внутри bottom-sheet (h-65vh) на mobile
  занимает весь экран - нужна другая UX flow
- **Sticky text toolbar** (Сессия 27) - sticky top-2 z-30 работает
  на desktop. Mobile: нужно учесть browser bottom address-bar
  collapsing, sticky может прыгать. Возможно `position: sticky`
  заменить на `position: fixed top-0` с padding на main
- **PdfViewer toolbar** - 6+ items в одну строку (prev/next + page
  input + zoom + download + PDF tab). На mobile нужно либо вынести
  в overflow menu, либо переключить на вертикальный stack

## Будущие фичи (исламский контекст и расширения из дизайн-референса)

В `frontend/design-reference/project/islamic.jsx` и `extras.jsx`
дизайн показывает большое количество секций про работу с
исламскими текстами, sanad-цепочками, multi-grading и пр. Каждая
секция здесь - заготовка под будущий ADR и этап

- [ ] **Source picker для Корана** - таб «Коран» с навигацией по
      сурам, выбор аята, inline-вставка с цитатой и переводом.
      Бэк не готов: нужна интеграция с источниками типа quran.com
      или локальный mushaf-датасет _(SourcePickerQuran)_
- [ ] **Source picker для хадисов** - таб «Хадисы» с 9 сборниками
      (Бухари, Муслим, Тирмизи и т.д.), фильтр по grade
      (sahih/hasan/daif), показ иснада. Потенциальная интеграция
      с sunnah.com _(SourcePickerHadith)_
- [ ] **Source picker для книг** - таб «Книги» с навигацией том /
      страница, интеграция с shamela.ws. Самая большая работа
      из source pickers _(SourcePickerBooks)_
- [ ] **Source detail panel** - параллельная боковая панель
      (800px) с полным содержанием цитируемого источника,
      контекстом и метаданными _(SourceDetailPanel)_
- [ ] **Library overview** - страница `/library` с обзором
      источников темы _(LibraryOverview)_
- [ ] **Inline citations** - формат `[1]` в тексте с popover,
      привязанные к node-source records _(InlineCitations)_
- [ ] **Sanad explorer** - визуализация цепочки передатчиков
      хадиса (8-звенная от Пророка ﷺ до составителя). Каждое
      звено - карточка передатчика (имя / поколение / tier).
      Связи типизированы (`sama'` / `'an'ana` / `haddathana` /
      мункати'). Альтернативные пути. Серьёзная доменная фича -
      потребует расширения доменной модели (новые сущности
      `Rawi`, `Sanad`, `SanadLink`) _(SanadExplorer, SANAD demo
      data)_
- [ ] **Multi-grading хадисов** - один хадис может быть оценён
      несколькими учёными по-разному (Бухари: sahih, Тирмизи:
      hasan). Сейчас `Reliability` - single-value. Расширение на
      M:N таблицу `hadith_grades` (rawi / scholar / grade / source)
      _(MultiGradingSection, SCHOLAR_GRADES demo)_
- [ ] **Bilingual карточки** - двуязычный режим узла
      (EVIDENCE / ARGUMENT с арабским оригиналом + русским
      переводом). Toggle режима оригинал / перевод / оба.
      Требует RTL-поддержки и naskh-шрифтов _(BilingualNodeCard)_
- [ ] **Translator attribution** - при показе перевода аята /
      хадиса - указание переводчика (Кулиев, Sahih International,
      Османов и т.д.). Dropdown переключения переводов
      _(TranslatorSection)_
- [ ] **Tashkeel toggle** - на canvas карточки можно отключить
      огласовки (`harakat`) для краткости. Side-by-side
      сравнение с / без _(TashkeelSection)_
- [ ] **RTL-режим** - для арабского UI: зеркальный layout графа,
      RTL-toolbar, naskh / kufi-шрифты. Большая работа, выделить
      в отдельный этап _(RTLGraphScreen, RTLSection)_
- [ ] **Language switcher (RU / EN / AR)** - в header или
      settings. Идёт в комплекте с i18n и RTL
      _(LanguageSwitcher)_
- [ ] **Settings screen** - язык, выбор арабского шрифта, размер
      текста, тогглы tashkeel / транслит, drag-приоритет
      источников _(SettingsScreen)_
- [ ] **Onboarding** - 4-шаговый чеклист для новой темы
      («создай корневой вопрос», «добавь тезис-ответ» и т.д.) +
      hint-указатели на canvas _(OnboardingChecklist,
      OnboardingHint)_
- [ ] **Topic settings drawer** - 480px drawer над затемнённым
      canvas: title / desc, корневой вопрос (lock), радио
      Private / Shared / Public, метаданные, danger zone
      _(TopicSettingsDrawer)_. Требует расширения Topic на
      бэке полем `visibility` (после auth)
- [ ] **Multi-select с floating action bar** - лассо или
      Shift+click несколько узлов, всплывающая action-bar для
      массовых операций (изменить статус, переместить, удалить,
      экспорт) _(MultiSelectScreen)_
- [ ] **Cross-references drawer** - 600px drawer «узел использован
      в N темах»: группировка по темам, прыжок в граф. Cross-topic
      graph-навигация. Требует backend аггрегата по cross-topic
      ссылкам _(CrossRefDrawer)_
- [ ] **Print preview** - A4-toolbar с тогглами (включить узлы,
      источники, иснады) + полноценная печатная страница темы.
      Граф как SVG, источники в академическом формате
      _(PrintPreviewSection)_

## Бэк - бэклог

- [ ] Пагинация для GET-list эндпоинтов (`/sources`,
      `/authorities`) - пока не нужна, справочники маленькие
- [ ] Фильтрация `?type=`, `?reliability=`, `?era=`, `?madhab=` -
      пока есть только `?q=`
- [ ] Аутентификация (Spring Security + JWT) - см. Этап 21 в
      roadmap
- [ ] Реализация Dung's argumentation framework для продвинутого
      пересчёта статусов
- [ ] Импорт / экспорт темы в JSON (для бэкапа и обмена)
- [ ] Голосование за вес аргументов
