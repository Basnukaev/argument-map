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
   - docs/roadmap.md (текущий этап, открытые пункты)
   - docs/decisions.md (все ADR, особенно последние 3-5)
   - docs/gotchas.md (все ловушки, чтобы не наступить)
   - docs/api-contract.md (бегло, источник истины контракта)
   - frontend/CLAUDE.md и backend/CLAUDE.md (правила работы и
     чек-лист документации после коммита)
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
4. Скажи Абдуле: "вижу - последний раз X. Следующее по приоритету -
   Y. Продолжаем с этого или хочешь другое?"
5. ЖДИ ПОДТВЕРЖДЕНИЕ. Не начинай работу без него.

══════════════════════════════════════════════
ТЕКУЩЕЕ СОСТОЯНИЕ (зафиксировано на 2026-05-05 после сессии 14)
══════════════════════════════════════════════

ЗАКРЫТО:
- Бэк: этапы 0-5 целиком, 148+ IT тестов
- Фронт MVP (этап 7): TopicListPage, CreateTopicPage, TopicGraphPage
  с полным CRUD + side-panel деталей узла + редактирование + ревизии
- Этап 8: семантика связей (ADR-010 матрица, бэк-валидация, фронт
  фильтрация, контекстные подписи рёбер, toggle подписей)
- Этап 9 целиком: 4 handles, drag-create, контекстное меню (правый
  клик на pane/узле/ребре), z-index управление, persistence позиций
  узлов (full-stack миграция БД pos_x/pos_y, ADR-012)
- Toast-инфраструктура: useToastStore + Toaster компонент
- ADR-011 удаление weight, ADR-012 координаты узлов

ОТКРЫТО (по приоритету): <!-- AUTOFILL -->
1. **sourceHandle/targetHandle persistence для edges** (full-stack)
   - аналог posX/posY: миграция БД (2 nullable VARCHAR в edges),
     Edge модель/DTO, фронт onConnect передаёт в POST /edges,
     CustomEdgeData при render использует. Сейчас drag-create
     работает но после refetch RF auto-routing'ом выбирает стороны
     по позициям, не уважая исходный выбор пользователя
2. **Координаты при "Создать здесь"** из контекстного меню pane
   - сейчас ContextMenu pane не передаёт clientX/Y в AddNodeModal,
     новый узел получает posX=null. Нужно: расширить POST /nodes
     принимать opt posX/posY (либо фронт делает POST + PATCH)
3. **Smart edge routing** (опционально): elkjs или custom edge с
   pathfinding, если 4-handles + dagre мало
4. **AddEdgeModal полировка**: кастомный dropdown с lucide-иконками
   вместо нативных select (после-MVP)
5. **Бэк-долг с этапа 4**: springdoc + @CurrentUser - параметр
   userId неправильно в OpenAPI

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

КЛЮЧЕВЫЕ ФАЙЛЫ:
- frontend/src/pages/TopicGraphPage.tsx — hub-страница графа,
  собирает все компоненты (RF, NodeCard, CustomEdge, AddNodeModal,
  AddEdgeModal, NodeDetailsPanel, ContextMenu)
- frontend/src/components/graph/ — узлы и рёбра
- frontend/src/components/ui/ — переиспользуемые (Modal, Button,
  Toaster, ContextMenu)
- frontend/src/utils/edgeRules.ts — матрица ADR-010
- frontend/src/utils/graphLayout.ts — layout (mixed-режим dagre+saved)
- frontend/src/stores/toastStore.ts — Zustand toast-store
- frontend/src/api/client.ts — apiGetRaw/apiPostRaw/apiPatchRaw/
  apiDeleteRaw + ApiError
- backend service/ EdgeSemantics.java — матрица ADR-010 на беке
- backend service/ NodeService.updatePosition — изолированный
  метод без revision и updatedAt

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
- ADR/gotcha/api-contract пишутся СРАЗУ, не в конце сессии

ПЕРЕД КАЖДЫМ КОММИТОМ:
- Фронт: npm run lint && npm run build && npm run test:run
- Бэк: ./mvnw verify
- Если фича работает с API - smoke через curl с X-User-Id

КОММИТЫ:
- Conventional Commits с обязательным scope: feat(frontend),
  fix(backend), chore, docs, refactor, test, style, perf, build, ci
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

"вижу - в сессии 14 закрыли этап 9 целиком (Miro UX, координаты
узлов на канвасе сохраняются, контекстное меню, z-index). 14
коммитов, всё зелёное (148 IT, 94 unit). По приоритету следующее:
sourceHandle/targetHandle persistence для edges (full-stack аналог
posX/posY) - закрывает второй пользовательский фидбек после E.a.
Поехали?"

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
