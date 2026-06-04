# Codebase audits

Аудиты кодовой базы - инвентаризация технического долга для cleanup-сессий.

## Файлы

- `2026-05-11-codebase-audit.md` - первый полный heavy audit после 24
  сессий (backend Java + frontend TS/TSX + tests + docs). 46 findings.
  Источник для Cleanup Marathon (см.
  `docs/specs/2026-05-11-codebase-cleanup-marathon-design.md`)

## Когда читать

- Перед началом cleanup-фазы (Phase backlog в audit-документе указывает
  что попадает в текущую фазу)
- При решении вопроса "должен ли я править этот файл сейчас" - сверить
  с findings, если в backlog для будущей Phase - не трогать
- При найденной проблеме, не входящей в audit - это сигнал на дополнение
  audit'а перед изменениями

## Что НЕ хранится здесь

- Текущие задачи (см. roadmap.md)
- Реализация (см. `docs/specs/` + `docs/plans/`)
- Лог сессий (см. progress.md)
