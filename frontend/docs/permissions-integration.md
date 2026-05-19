# Permissions Integration (Этап 22.b, ADR-043)

Per-entity authorization для тем (backend — ADR-043). Frontend
показывает visibility/permission UI и hides write actions, но
**backend — источник истины**: всё что не должно меняться —
ассертится service-слоем и возвращает 403.

## topic.visibility

Всегда читать из `topic.visibility` поля TopicResponse
(`'PRIVATE' | 'SHARED' | 'PUBLIC'`). Default `'PRIVATE'` если бэк
отдаст null (defensive). Не путать с `auth.role` — там
user-уровень `'USER' | 'ADMIN'`.

## isOwner check

`currentUser.id === topic.createdBy`. ADMIN — отдельный bypass
(`currentUser.role === 'ADMIN'`).

## canWrite optimistic

На фронте `isOwner || isAdmin || visibility !== 'PRIVATE'` это rough
estimate, не точная семантика. Точная (EDITOR membership) — GET
/members и проверка. Frontend показывает кнопки если **возможно**,
бэк ассертит при запросе.

## Hiding write actions

`GraphCanvas`/`GraphPanels` принимают `canWrite` prop, при false
скрывают Add/Delete/Edit и mutating context menu items. Read-only
items (z-order, open details panel) оставляем.

## VisibilityBadge и VisibilityRadioGroup

Переиспользуемые компоненты в `apps/argument-map/components/`. Badge
компактный (`compact` prop = только иконка), Radio — в Field стиле с
подсказками.

## TopicMembersModal

Управление членами SHARED-тем. Open только для owner/admin. MVP без
user search — input UUID + client-side regex validation. Full search
(autocomplete по email) — backlog.

## Permission errors

Всегда форматировать через `formatPermissionError(err, t)` из
`shared/api/permissionErrors.ts`. Маппит `forbidden-topic-access` →
«У вас нет доступа к этой теме», `forbidden-topic-write` → «У вас
нет прав на изменение этой темы». Если не permission — вернёт null,
fallback через `formatApiError`.

## Тестирование modals

`HTMLDialogElement` в jsdom не реализует `showModal`/`close`, ставим
polyfill в `beforeEach`. `window.confirm` для DELETE подтверждения —
stub через `vi.stubGlobal('confirm', () => true)`.
