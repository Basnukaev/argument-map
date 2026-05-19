# Auth Integration (Этап 21.b, ADR-040)

Frontend интеграция с JWT auth — Spring Security 6 + jjwt 0.12.x на
backend (см. `backend/docs/auth-security.md` для backend деталей),
`useAuthStore` (Zustand) + `apiClient` interceptor на frontend.

## Текущий user

Всегда через `useAuthStore((s) => s.user)`, никогда не дёргать
`getState()` в render. user может быть `null` внутри ProtectedRoute
если bootstrap ещё идёт — но это редкий race.

## accessToken / Interceptor

НЕ читать `accessToken` напрямую из store, не передавать руками в
fetch. Interceptor в `shared/api/client.ts` сам добавляет
`Authorization: Bearer` для всех `/api/v1/*` запросов кроме
`/api/v1/auth/*`.

## ProtectedRoute

Новый page → обязательно `<ProtectedRoute>` в App.tsx routing. Без
protection — страница будет accessible для не-залогиненных users и
упадёт на первом /api запросе с 401 redirect loop.

## Admin-only pages

`<ProtectedRoute requireRole="ADMIN">`. USER → silent redirect
`/topics` (полный 403 page — Этап 22).

## 401 retry behavior

Не нужно catch + retry в компонентах. apiClient interceptor ловит
сам, делает refresh и retry оригинальный запрос с новым token. Если
refresh тоже 401 — чистит session, `ApiError(401)` пробрасывается в
catch компонента (там обычно показывают toast).

## logout()

Вызывать через `useAuthStore.getState().logout()` или через хук +
`navigate('/login')`. `AvatarMenu` уже это делает — переиспользовать,
не реимплементировать.

## Dev cookies (SameSite=Strict)

SameSite=Strict refresh cookie работает только same-origin. В dev
используется Vite proxy (`/api` → :9090) — не выставлять
`VITE_API_URL=http://localhost:9090` в `.env.local`, иначе
cross-origin сломает auth.

## Тестирование компонентов с authStore

Перед тестом сбрасывать store через
`useAuthStore.setState({ user: ..., initialized: true })` и
`localStorage.removeItem('auth.user')`. Не подключать `authBridge`
в тестах — msw мокает endpoints, interceptor работает в legacy mode.
