# Стартовый промпт для новой сессии Claude Code

Этот файл - шаблон для начала новой сессии после исчерпания контекста
текущей. Скопировать содержимое раздела "Промпт для копирования" в
начало новой сессии - Claude получит полный контекст без ручного
объяснения.

Раздел "Что обновить перед каждым handoff'ом" описывает что нужно
проверить в этом файле перед тем как использовать его в новой
сессии (актуальные TODO, изменения инфраструктуры, новые ADR и т.п.).

---

## Что обновить перед каждым handoff'ом

Перед использованием промпта в новой сессии **проверь актуальность**
полей помеченных `<!-- AUTOFILL -->`:

- Текущая дата (`Today's date is ...`) - если этот промпт лежал
  >недели, обновить
- Список открытых TODO - сверить с `roadmap.md` и записями `progress.md`
- Текущий PORT/UUID/тестовая тема - проверить что dev-тема ещё
  существует, иначе пересоздать через `scripts/seed-mawlid.sh`
- Список последних `git log --oneline -10` - чтобы новая сессия
  понимала свежий контекст коммитов
- Bundle size - после крупных рефакторингов или новых тяжёлых
  зависимостей сверить с реальностью (`npm run build`)

---

## Промпт для копирования

```
Ты Claude Code, продолжаешь работу над проектом argument-map с Абдулой
Баснукаевым. Это монорепа в /mnt/c/my_folders/projects/argument-map:
- backend/ — Java 21, Spring Boot 3.5, JDBC Template, Postgres 16, Liquibase
- frontend/ — React 19, Vite 6, Tailwind v4, React Flow, Zustand 5,
  openapi-typescript, Vitest

══════════════════════════════════════════════
START-OF-SESSION PROTOCOL (выполни ДО ответа)
══════════════════════════════════════════════
1. Прочитай ПОЛНОСТЬЮ:
   - docs/progress.md (последние 3 записи + раздел "Следующий шаг")
   - docs/roadmap.md (текущий этап, открытые пункты, бэклог
     "Будущие фичи (исламский контекст)" - 18+ записей из
     дизайн-референса)
   - docs/decisions.md (все ADR, особенно последние 3: ADR-014
     reconnect, ADR-015 status-bar слева, ADR-016 nodeCount/
     edgeCount в TopicResponse). В Этапе 12 ADR не было - чисто
     UI поверх готового бэк-контракта
   - docs/gotchas.md (все ловушки, чтобы не наступить)
   - docs/api-contract.md (бегло, источник истины контракта)
   - frontend/CLAUDE.md и backend/CLAUDE.md (правила работы и
     чек-лист документации после коммита)
   - frontend/docs/ui-guidelines.md (после Этапа 11 -
     обновлённая палитра, status-bar слева, токены в designTokens.ts)
   - frontend/design-reference/README.md (handoff-бандл с
     дизайном; jsx файлы - визуальная спецификация для будущих
     фич, не код для копирования)
2. Проверь актуальное состояние:
   - git log --oneline -15 (свежие коммиты)
   - docker ps | grep argumentmap-postgres (контейнер БД healthy)
   - lsof -ti:9090 lsof -ti:5173 (что-то на портах)
3. В ~/.claude/projects/.../memory/MEMORY.md есть auto-memory:
   - WSL-only - всё в WSL2, не на Windows-стороне
   - User: Abdula Basnukaev, Java/Spring backend разработчик с
     проектом CREW в бэкграунде, новичок в JS/React
   - Decision authority: решай сам по умолчанию, спрашивай при
     дилеммах. ADR только когда через месяц возникнет вопрос почему
   - React key-trick для reset state (НЕ useEffect-сброс)
   - Stable callbacks + sameIds для RF массивов (анти-инфинит-луп)
   - Stale closure в useCallback с dynamic data → useRef для
     актуального snapshot (см. gotchas.md)
   - layoutGraph mixed-режим может перебросить fresh узлы. Решено
     через backfill posX/posY на load + previousNodes hint
     (см. gotchas.md)
   - Sources & Arabic direction: shamela-парсинг будущее,
     арабский как first-class (RTL + naskh) - в дизайн-референсе
     отдельные секции, в roadmap бэклог "Будущие фичи"
   - Conditional render для одноразовых модалок ({open && <Modal/>})
     вместо useEffect-сброса state - идиома проекта, обходит
     react-hooks/set-state-in-effect (см. gotchas.md). Внутри
     Modal всегда `open` prop. Применено в AddSourceModal,
     AddAuthorityModal
4. Скажи Абдуле: "вижу - последний раз X. Следующее по приоритету -
   Y. Продолжаем с этого или хочешь другое?"
5. ЖДИ ПОДТВЕРЖДЕНИЕ. Не начинай работу без него.

══════════════════════════════════════════════
ТЕКУЩЕЕ СОСТОЯНИЕ (зафиксировано на 2026-05-08 после сессии 18)
══════════════════════════════════════════════

ЗАКРЫТО:
- Бэк: этапы 0-5 целиком, 172 теста (111 unit + 61 IT)
- Фронт MVP (этап 7): TopicListPage, CreateTopicPage, TopicGraphPage
  с полным CRUD + side-panel деталей узла + редактирование + ревизии
- Этап 8: семантика связей (ADR-010 матрица, бэк-валидация, фронт
  фильтрация, контекстные подписи рёбер, toggle подписей)
- Этап 9 целиком: 4 handles, drag-create, контекстное меню (правый
  клик на pane/узле/ребре), z-index управление, persistence позиций
  узлов (full-stack миграция БД pos_x/pos_y, ADR-012)
- Этап 10 целиком (сессия 16): reconnect edges (ADR-014, partial
  PATCH /api/v1/edges/{id}, optimistic update без flicker),
  EdgeDetailsPanel
- **Этап 11 целиком (сессия 17): визуальная полировка по
  дизайн-референсу** - 8 подэтапов, 9 коммитов:
  1. документация и токены (ADR-015 status-bar, ui-guidelines
     обновлён, glossary с исламскими терминами, бэклог из дизайна)
  2. UI-примитивы (Button расширен 6×4, Badge, StatusBadge,
     TypeChip, Kbd, IconButton, Card, designTokens.ts)
  3. NodeCard - status-bar 5px слева вместо border-2, TypeChip+
     StatusBadge в header, line-clamp-2 body
  4. CustomEdge - переключён на EDGE_TYPE_TOKENS, badge с soft shadow
  5. AddNodeModal/AddEdgeModal - тип в grid карточек с превью
  6. NodeDetailsPanel/EdgeDetailsPanel - градиент header, collapse
     секции, diff-блоки в истории (red-50/40 / emerald-50/40)
  7a. Бэк (ADR-016): nodeCount/edgeCount в TopicResponse через
      агрегатный SQL (один LEFT JOIN-запрос для всех тем).
      TopicWithCounts record + TopicRepository.findAll/ByIdWithCounts
  7b. TopicListPage - сетка карточек с мини-графом SVG, бейдж count,
      topbar с навигацией (Авторитеты/Источники placeholder)
  8. GraphScreen layout - левый вертикальный toolbar (IconButton),
     floating легенда (bottom-left) / zoom controls (bottom-center
     через rfInstance) / hotkeys hint (top-right). CompactMiniMap
     перенесён top-right → bottom-right
- **Этап 12 целиком (сессия 18): привязка источников и авторитетов
  через UI** - 5 коммитов:
  12.a. NodeDetailsPanel секции "Источники"/"Авторитеты" - lazy-load
       через GET /nodes/{id}/sources + параллельный GET /sources для
       матчинга id→название. Карточки источников (kind/title/citation/
       quote/context), строки авторитетов с avatar+stance бейджем.
       Удаление через DELETE optimistic. PanelSection расширен
       onFirstOpen callback. `apiPostRaw` добавлен в client.ts
  12.b. AddSourceModal с поиском - локальная фильтрация справочника
       по title/citation, опциональные quote/context при привязке.
       Conditional render родителя (`{open && <Modal/>}`) обходит
       react-hooks/set-state-in-effect
  12.c. Inline-создание Source - mode='create' в той же модалке.
       Форма sourceType/title/citation/reliability (показ только
       для HADITH, фронт строже бэка). Submit делает POST /sources
       → POST /nodes/{id}/sources. Извлечён attachmentTokens.ts
  12.d. AddAuthorityModal со stance + create - симметрично, но
       stance обязателен. StancePicker с цветовым кодированием
       (HOLDS=emerald/OPPOSES=red/NEUTRAL=slate). Create-form:
       name (required), era, madhab, bio
  12.e. Документация (этот раздел + roadmap Этап 12 + ui-guidelines
       + новый gotcha про conditional render модалок)
- Cross-cutting: Toast, ContextMenu (с separator items), Modal,
  NodeSelect (custom dropdown с lucide-иконками), CompactMiniMap
- ADR-011-016 все приняты. В Этапе 12 ADR не делал - чистый UI
  поверх готового бэк-контракта

ОТКРЫТО (по приоритету): <!-- AUTOFILL -->
1. **Source picker для Корана** (`SourcePickerQuran` в дизайне) -
   таб "Коран" с навигацией по сурам, выбор аята. Требует датасет:
   локальный mushaf JSON или интеграция с quran.com API. Большая
   работа, отдельный этап с backend-инфраструктурой. **Самый
   ценный ближайший пункт** для исламского контекста
2. **Source picker для хадисов** - 9 сборников + grade-фильтр +
   sanad. Зависит от sunnah.com или локального датасета. Очень
   большая работа
3. **Sanad explorer** - доменное расширение модели (Rawi/Sanad/
   SanadLink сущности на беке) + новый граф-визуализатор.
   Самая глубокая фича в бэклоге
4. **Bilingual карточки + RTL** - арабский как first-class.
   Требует i18n + naskh-шрифт + RTL-layout. Большая работа
5. **Экспорт графа в PNG/SVG** через `html-to-image`. Кнопка
   в toolbar. Малая полезная утилита, не требует доменной
   экспертизы. Хороший warm-up между крупными фичами
6. **Z-index full-stack persistence** для узлов и рёбер. Сейчас
   локально, при refetch теряется
7. **Smart edge routing** через elkjs - опционально на плотных
   графах
8. **Тёмная тема** - Tailwind dark variant + toggle
9. **Полнотекстовый поиск** - blocked на бэк (Этап 6)
10. **Аутентификация** - blocked на бэк (Этап 6)

ИНФРАСТРУКТУРА:
- Postgres контейнер: argumentmap-postgres на :5432 (docker ps)
- Бэк: cd backend && ./mvnw spring-boot:run > /tmp/backend.log 2>&1 &
  (порт 9090, ждать "Started ArgumentMapApplication")
- Фронт: cd frontend && npm run dev (порт 5173, watch.usePolling=true
  для WSL2)
- Dev user UUID: 14561248-0bfd-4a62-8395-d40a6972182a
  (frontend/.env.local: VITE_DEV_USER_ID)
- Тестовая тема "Дозволенность Мавлида ан-Наби":
  640a7ac7-2827-4b80-9893-dc7142f100e4
  Скрипт пересоздания: scripts/seed-mawlid.sh
- Bundle (после Этапа 12): initial 256kB / gzip 82kB,
  TopicGraphPage chunk 373kB / gzip 116kB. +29kB к Этапу 11 за
  AddSourceModal+AddAuthorityModal+секции в NodeDetailsPanel
- Если регенерируешь типы (`npm run generate-api`) - сначала
  убедись что слушает СВЕЖИЙ бэкенд с твоими изменениями. Проверь
  через curl http://localhost:9090/v3/api-docs что новые поля есть
  (gotcha из сессии 17: старая инстанция на 9090 даст устаревшую
  схему)

КЛЮЧЕВЫЕ ФАЙЛЫ:
- frontend/src/utils/designTokens.ts — STATUS_TOKENS / NODE_TYPE_TOKENS
  / EDGE_TYPE_TOKENS, источник истины для палитр. Все компоненты
  импортируют отсюда (status bar, badge, type chip и т.д.)
- frontend/src/components/ui/ — после Этапа 11: Button (6×4 + icon),
  Badge, StatusBadge (data-testid сохранён для совместимости
  тестов), TypeChip, Kbd, IconButton, Card, Modal, Toaster,
  ContextMenu (с separator support)
- frontend/src/pages/TopicGraphPage.tsx — hub-страница графа,
  собирает все компоненты (RF, NodeCard, CustomEdge, AddNodeModal,
  AddEdgeModal, NodeDetailsPanel, EdgeDetailsPanel, ContextMenu,
  CompactMiniMap). lastNodesRef + backfill posX/posY useEffect.
  findFreePosition spiral search. Esc-очередь. Левый toolbar +
  floating legend/zoom/hotkeys через RF Panel
- frontend/src/pages/TopicListPage.tsx — сетка карточек тем с
  TopicMiniGraph SVG (декоративный, точки по nodeCount), topbar
  с навигацией, локальный поиск
- frontend/src/components/graph/ — NodeCard (status-bar слева,
  TypeChip+StatusBadge), CustomEdge (use EDGE_TYPE_TOKENS),
  AddNodeModal (autoEdge + grid карточек типа), AddEdgeModal,
  NodeDetailsPanel/EdgeDetailsPanel (градиент header + collapse
  секции, diff-блоки в истории, после Этапа 12 секции "Источники"/
  "Авторитеты" работают полноценно с lazy-load), NodeSelect
  (custom dropdown), CompactMiniMap (bottom-right), AddSourceModal
  (search + create mode), AddAuthorityModal (search + create со
  stance picker)
- frontend/src/utils/attachmentTokens.ts — после Этапа 12:
  SOURCE_TYPE_LABEL/ICON/HINT/ORDER, STANCE_LABEL/BADGE_STYLES/
  RADIO_STYLES/ORDER. Источник истины для отображения source/
  authority типов и stance. По аналогии с designTokens.ts
- frontend/src/utils/edgeRules.ts — матрица ADR-010,
  NODE_TYPE_META, EDGE_TYPE_META, getRelatedNodeOptions.
  ВНИМАНИЕ: частично пересекается с designTokens.ts - в будущей
  итерации стоит консолидировать в один источник
- frontend/src/utils/graphLayout.ts — layout с allSaved/noneSaved/
  mixed режимами + previousNodes hint
- frontend/src/stores/toastStore.ts — Zustand toast-store
- frontend/src/api/client.ts — apiGetRaw/apiPostRaw/apiPatchRaw/
  apiDeleteRaw + ApiError
- frontend/src/api/types.ts — генерируется из OpenAPI бэка.
  TopicResponse теперь с nodeCount + edgeCount (ADR-016)
- frontend/src/App.tsx — React.lazy для TopicGraphPage
- frontend/design-reference/ — handoff-бандл от Claude Design.
  Это **визуальная спецификация будущих фич**, не код для копирования.
  primitives.jsx/nodes.jsx/screens.jsx - что реализовано в Этапе 11.
  islamic.jsx/extras.jsx - бэклог (sanad, multi-grading, RTL,
  settings, onboarding и т.д.)
- backend service/ TopicService.java — listTopicsWithCounts /
  getTopicWithCounts (ADR-016)
- backend repository/ TopicWithCounts.java — record + Repository
  методы findAllWithCounts / findByIdWithCounts
- backend service/ EdgeService.java — createEdge + updateEdge
  partial с финальной валидацией
- backend service/ EdgeSemantics.java — матрица ADR-010 на беке
- backend service/ NodeService.updatePosition — изолированный
  метод без revision и updatedAt
- backend config/ OpenApiConfig.java — OperationCustomizer
  для X-User-Id header вместо query.userId

══════════════════════════════════════════════
КАК РАБОТАТЬ
══════════════════════════════════════════════

ДЕКОМПОЗИЦИЯ КРУПНЫХ ЗАДАЧ:
- Любая задача больше 1-2 файлов → подэтапы X.a / X.b / X.c
- Между подэтапами — прогон проверок и КОММИТ. Не один большой
- Каждый подэтап имеет внятную границу

ДОКУМЕНТАЦИЯ ВЕДЁТСЯ ПО ХОДУ (важно!):
После КАЖДОГО feat/fix коммита проверить чек-лист из
frontend/CLAUDE.md и backend/CLAUDE.md:
- Закрыт пункт roadmap → [x]
- Принято решение между альтернативами → ADR в decisions.md
- Миграция БД / новая колонка → ADR + architecture.md
- Новый REST endpoint / поле DTO → api-contract.md
- Поймал баг который может повториться → gotcha
- Новое доменное понятие → glossary.md
- ADR/gotcha/api-contract пишутся СРАЗУ, не в конце сессии

ПЕРЕД КАЖДЫМ КОММИТОМ:
- Фронт: npm run lint && npm run build && npm run test:run
- Бэк: ./mvnw verify
- Если фича работает с API - smoke через curl с X-User-Id

КОММИТЫ:
- Conventional Commits с обязательным scope: feat(frontend),
  fix(backend), chore, docs, refactor, test, style, perf, build, ci
- Чисто визуальные правки без изменения поведения - style(frontend):
- Не коммитить .claude/settings.local.json, img*.png/gif/jpg
- Не амендить опубликованные коммиты, не push без явной просьбы

ПРОВЕРКА ИНТУИТИВНОСТИ UI:
Перед "готово":
- Иконка/эмодзи понятны без расшифровки? Если нет - словесная метка
- Disabled/error состояние - видна причина? Если нет - tooltip/подсказка
- Результат действия очевиден? Если нет - feedback (toast/hover)
- Прежде чем сказать "готово" - попроси Абдулу проверить через UI

ПРОВЕРКА ЧЕРЕЗ CURL:
- Создал/изменил эндпоинт - curl с реальным X-User-Id
- Создал валидацию - оба пути: разрешённый и запрещённый
- "Тесты прошли" ≠ "фича работает"

ВИЗУАЛЬНЫЕ ПРАВКИ (после Этапа 11):
- Все цвета и токены - через `frontend/src/utils/designTokens.ts`
  (STATUS_TOKENS / NODE_TYPE_TOKENS / EDGE_TYPE_TOKENS). Не
  хардкодить цвета прямо в компонентах
- Brand-цвет: indigo (не blue). focus-ring → indigo-500
- Скругления карточек - rounded-xl, кнопок/инпутов - rounded-md
- Статус узла - bar 5px слева (НЕ border-2 вокруг). См. ADR-015
- Тип узла - капсула TypeChip (chipBg/chipText), не просто иконка
- Тесты на конкретные tailwind-классы (например `toHaveClass(
  'bg-amber-100')` в StatusBadge) обновлять при смене токенов,
  поведенческие тесты не трогать

КОГДА ОСТАНАВЛИВАТЬСЯ И HANDOFF:
Признаки:
- Контекст забивается, остался один сложный кусок
- Сделал N подэтапов, ещё M открыто, до конца не дотяну
- В задаче открылись новые вопросы дизайна

Действия:
1. Коммит того что уже работает (с зелёными тестами)
2. Запись в docs/progress.md - подробная, с ADR/gotcha/api-contract
   обновлёнными по ходу
3. Обновить roadmap.md
4. Обновить docs/SESSION_START_PROMPT.md (этот файл) -
   список открытых TODO, актуальные коммиты, следующий приоритет
5. Скажи Абдуле "сделал X, остановился перед Y потому что Z.
   handoff в progress.md + SESSION_START_PROMPT.md"

ПАМЯТЬ И FEEDBACK:
- Корректирующий feedback ("не делай так") → сохраняй в auto-memory
- Подтверждение неочевидного решения ("да, это правильно") → тоже
- Перед началом загляни в MEMORY.md и feedback_*.md

══════════════════════════════════════════════
СТИЛЬ ОБЩЕНИЯ
══════════════════════════════════════════════
- Русский, нижний регистр в начале предложений, без точек в конце
- Короткое тире "-", не длинное "—"
- enum-значения в бэктиках с русским рядом: разворот (`TURN`)
- Перечисления 2+ длинных элементов - списком через дефис
- Без пафосных заголовков ("Статус:", "Вывод:") - живым языком
- Технические термины и идентификаторы - на английском в коде,
  русский в обсуждении
- Если неуверен - спроси, не догадывайся

══════════════════════════════════════════════
ПРИВЕТСТВИЕ
══════════════════════════════════════════════
После прочтения 5+ файлов из START-OF-SESSION PROTOCOL начни ответ
с короткого summary последнего состояния и предложения. Например:

"вижу - в сессии 18 закрыли Этап 12 (привязка источников и
авторитетов к узлам через UI): 5 подэтапов, 5 коммитов. Главное -
секции "Источники"/"Авторитеты" в NodeDetailsPanel перешли с
placeholder на реальные lazy-loaded списки с возможностью
привязки/отвязки. Две модалки AddSourceModal и AddAuthorityModal
с двумя режимами каждая (search в локальном справочнике + inline-
create). Conditional render `{open && <Modal/>}` как идиома против
react-hooks/set-state-in-effect (новая gotcha). Извлечён
attachmentTokens.ts - источник истины для SOURCE_TYPE/STANCE
токенов. ADR не делал - чистый UI поверх готового бэк-контракта
из Этапа 5. 143 фронт-теста (+27 новых) зелёные. Bundle
TopicGraphPage chunk 373kB / gzip 116kB.
По приоритету следующее: Source picker для Корана - таб с
навигацией по сурам, выбор аята. Требует датасет (mushaf JSON
или quran.com API), большая фича с backend-инфраструктурой.
Альтернативно - экспорт графа в PNG/SVG через html-to-image,
малая утилита. Поехали или другое?"

Жди подтверждение. После него - смело за работу.
```

---

## Контрольные точки качества handoff'а

Хороший handoff даёт новой сессии:
1. **Что закрыто** - чтобы не переделывать
2. **Что открыто и в каком приоритете** - чтобы знать с чего
3. **Контекст последних решений** (ADR-N) - чтобы не нарушить
4. **Текущая инфра** (порты, UUID, тестовая тема) - чтобы сразу
   запустить smoke
5. **Указатели на ключевые файлы** - чтобы не искать вслепую
6. **Памятки про известные ловушки** - чтобы не наступить

Если в handoff'е чего-то нет, и новая сессия задаёт вопрос
"а где X" - значит handoff неполный, надо доработать этот файл.
