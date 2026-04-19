# CI Workflows

Пока не настроены. Планируется:

- `backend-ci.yml` — maven build + tests при изменениях в `backend/**`
- `frontend-ci.yml` — npm build + tests при изменениях в `frontend/**` (Этап 7+)
- `docs-lint.yml` — проверка markdown-файлов (опционально)

Триггеры: push в main + pull requests. Каждый workflow реагирует
только на изменения в своей папке через `paths:` фильтр.

Решение о настройке CI — после Этапа 2, когда появятся первые
репозитории и тесты.
