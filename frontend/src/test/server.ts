import { setupServer } from 'msw/node';

/**
 * MSW-сервер для перехвата fetch в тестах. Handlers задаются per-test
 * через `server.use(...)`. По умолчанию список пуст - тесты явно
 * декларируют все ожидаемые запросы. Любой неожиданный запрос - провал
 * теста через `onUnhandledRequest: 'error'` в setup.
 */
export const server = setupServer();
