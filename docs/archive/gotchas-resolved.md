# Архив resolved-gotchas (без active rule)

Записи, закрытые и не несущие действующих правил «не делать X впредь»
— чистая история. Живые ловушки и resolved-с-правилом — в `docs/gotchas.md`.

---

## Tashkeel removal через ProseMirror JSON transform (закрыто)

**Симптом исторически:** Reader имел кнопку «С огласовками / Без
огласовок» которая ставила класс `.hide-tashkeel` на article-wrapper,
но визуально текст **не менялся** - арабские диакритические знаки
(`َ`, `ِ`, `ُ`, `ْ`, `ّ`, `ٰ`) оставались на экране даже при
`hideTashkeel=true`. Это было MVP placeholder в Этапе 17.0.c.

**Причина:** диакритические знаки (Unicode range `U+064B`-`U+065F` +
superscript alef `U+0670`) - это **combining characters**, не отдельные
glyphs. Чистый CSS не может их «скрыть» через `display: none` или
`visibility: hidden` - они часть text node того же символа. Реальные
способы убрать огласовки:

1. **JSON transform перед render** - модифицировать ProseMirror
   document tree (заменить `text` поля у text-nodes через regex)
   **до** того как Tiptap отрендерит. Functional, React-friendly,
   без DOM-walk
2. **runtime regex по DOM** через `TreeWalker(NodeFilter.SHOW_TEXT)`
   после mount + замена `textContent`. Конфликтует с React reconciler
3. **font-feature-settings** через специальный шрифт где tashkeel -
   separate ligature glyphs (требует custom font asset)
4. **double render** - хранить два text representation в данных и
   переключаться между ними (raise data volume + breaks editing UX)

**Решение (закрыто):** выбрана опция 1 - чистая functional
трансформация ProseMirror JSON. Утилиты в
`frontend/src/shared/components/editor/utils/stripTashkeel.ts`:

- `stripTashkeelText(s)` - regex `/[ً-ٰٟ]/g` по строке
- `stripTashkeelFromDoc(doc, strip)` - рекурсивный walk JSON-tree,
  трансформ text-nodes, сохранение marks и attrs (включая сам
  `tashkeel` mark - он остаётся как семантический маркер)

`RichTextRenderer` принимает `hideTashkeel: boolean` prop, через
useMemo вычисляет processed content и передаёт в Tiptap. Toggle
обратно - тот же useMemo с другим input, возвращает оригинал
(идемпотентно, без mutation). Tatweel `U+0640` НЕ удаляется -
это горизонтальное растяжение буквы (каллиграфия), не диакритик;
отдельный feature в backlog при необходимости.

В legacy fallback path (когда `page.formattedContent` = null и
рендерится sanitized HTML) `hideTashkeel` применяется через
`stripTashkeelText` к raw text до `sanitizePageHtml`.

Покрытие: 17 тестов (15 для утилит + 2 интеграционных в
RichTextRenderer) - идемпотентность, рекурсия nested структур,
сохранение marks и attrs, no-mutation, latin/tatweel
негативные случаи.

---

## Параллельная сессия на Page record - не свой код, не трогать
**Симптом:** `./mvnw test-compile` падает на PageRepositoryIT /
QuestionCitationServiceIT с `constructor Page cannot be applied to
given types` - стало 12 параметров вместо 11

**Причина:** parallel subagent (Tiptap, Этап 17.0) добавил
`formattedContent` поле в `library.domain.Page` record. Существующие
тесты ещё не обновлены, потому что он в процессе работы. Не моя зона
ответственности - не трогать его код

**Решение:** игнорировать ошибки `library.repository.PageRepositoryIT`
и `qa.service.QuestionCitationServiceIT` в этой сессии. Подождать пока
parallel subagent закроет свою задачу - он сам обновит конструкторы
