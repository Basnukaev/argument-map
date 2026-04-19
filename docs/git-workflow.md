# Git Workflow

## Формат коммитов — Conventional Commits

```
<тип>(<scope>): <краткое описание>

<подробное описание, опционально>

<футер, опционально>
```

### Типы
| Тип | Когда | Пример |
|-----|-------|--------|
| `feat` | Новая фича | `feat(backend): add topic creation endpoint` |
| `fix` | Исправление бага | `fix(backend): prevent null in StatusCalculationService` |
| `chore` | Рутина, настройка | `chore(backend): initial spring boot project setup` |
| `docs` | Документация | `docs: add ADR-004 about maven choice` |
| `refactor` | Переписывание без изменения поведения | `refactor(backend): extract StatusCalculator` |
| `test` | Тесты | `test(backend): add EdgeRepository integration tests` |
| `style` | Форматирование | `style(backend): fix indentation in TopicController` |
| `perf` | Производительность | `perf(backend): add composite index on edges` |
| `build` | Сборка, зависимости | `build(backend): bump spring boot to 3.5.1` |
| `ci` | CI/CD | `ci: add github actions backend workflow` |

### Scope в монорепе
- `(backend)` — обязателен для изменений в `backend/`
- `(frontend)` — обязателен для изменений в `frontend/` (когда появится)
- Без scope — для изменений в корне репы или общей документации (`docs/`)

### Когда один коммит, когда несколько
- **Один коммит** — одна логическая единица работы. "Добавил эндпоинт +
  DTO + тест" — один коммит.
- **Несколько коммитов** — разные логические единицы. "Добавил эндпоинт"
  и "Обновил документацию" — два коммита.
- **Правило:** каждый коммит должен компилироваться и проходить тесты.
  Не коммитить "наполовину рабочее".

### Breaking changes
- Добавлять `!` после типа: `feat(backend)!: change graph response format`
- Или в футере: `BREAKING CHANGE: field 'nodes' renamed to 'graphNodes'`

## Ветки (когда появится командная работа)

Пока работаем в `main` напрямую. Когда появится необходимость:
- `main` — стабильная ветка
- `feature/<описание>` — фичи
- `fix/<описание>` — баг-фиксы
- PR обязательно, даже для одного разработчика — как checkpoint

## Теги и версии (после MVP)

Семантическое версионирование: `v1.0.0`, `v1.1.0`, `v2.0.0`.
Автоматизация через `standard-version` или `release-please` —
решить когда дойдём до MVP.
