import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config для E2E suite.
 *
 * Workers=1, fullyParallel=false - тесты делят backend БД, гонять
 * параллельно нельзя без изоляции на уровне БД per-worker (TODO backlog
 * "e2e parallel via per-worker DB schema").
 *
 * baseURL - dev-сервер фронта на :5173. Backend на :9090 - тесты ходят
 * туда через Vite proxy /api → :9090, потому ничего hardcode-URL не
 * нужно. Если сервера лежат - тесты падают на первом goto / login.
 *
 * Headless: true (WSL2 без X-сервера). Для локального debug -
 * `npm run e2e:headed` / `npm run e2e:debug`.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'e2e-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'on-first-retry',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
