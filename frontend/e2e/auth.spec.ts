import { test, expect } from '@playwright/test';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  clearAuth,
  expectLoggedIn,
  expectLoggedOut,
  login,
  loginAsAdmin,
  logout,
  registerNewUser,
} from './helpers/auth';

/**
 * Auth E2E suite - login / register / logout / protected routes /
 * session refresh.
 *
 * Каждый тест начинается с clearAuth для изоляции.
 */
test.describe('Auth', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
  });

  test('login - admin valid creds → /topics', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[type="email"]').fill(ADMIN_EMAIL);
    await page.locator('input[type="password"]').fill(ADMIN_PASSWORD);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/topics(?!.*\/login)/, { timeout: 10_000 });
    await expect(page).toHaveURL(/\/topics$/);
  });

  test('login - invalid creds shows error', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[type="email"]').fill('wrong@example.com');
    await page.locator('input[type="password"]').fill('wrongpassword');
    await page.locator('button[type="submit"]').click();
    // Должен остаться на /login и показать alert с ошибкой
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 5_000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test('register - new user успешно', async ({ page }) => {
    const creds = await registerNewUser(page);
    // После регистрации - на /topics
    await expect(page).toHaveURL(/\/topics$/);
    expect(creds.email).toContain('@e2e.test');
  });

  test('register - duplicate email shows error', async ({ page }) => {
    // Первая регистрация
    const creds = await registerNewUser(page, 'dup');
    // Logout, потом попытка зарегаться с теми же creds
    await clearAuth(page);
    await page.goto('/register');
    await page.locator('input[type="email"]').fill(creds.email);
    await page.locator('input[autocomplete="username"]').fill(creds.username + 'x');
    await page.locator('input[autocomplete="new-password"]').first().fill(creds.password);
    await page.locator('input[autocomplete="new-password"]').last().fill(creds.password);
    await page.locator('button[type="submit"]').click();
    // Должна показаться ошибка про email - под полем или общая
    await expect(page.locator('text=/уже|занят|exists|taken/i').first()).toBeVisible({
      timeout: 5_000,
    });
    await expect(page).toHaveURL(/\/register/);
  });

  test('protected route → redirect /login', async ({ page }) => {
    await expectLoggedOut(page);
    await page.goto('/topics/new');
    await expect(page).toHaveURL(/\/login/);
    // Должен быть redirect query param чтобы вернуть после login
    await expect(page).toHaveURL(/redirect=/);
  });

  test('logout - cleanup session', async ({ page }) => {
    await loginAsAdmin(page);
    await expectLoggedIn(page);
    await logout(page);
    // После logout - попытка зайти на protected редиректит
    await expectLoggedOut(page);
    // localStorage очищен
    const userInLs = await page.evaluate(() =>
      window.localStorage.getItem('auth.user'),
    );
    expect(userInLs).toBeNull();
  });

  test('refresh on 401 - apiClient interceptor возобновляет session', async ({
    page,
  }) => {
    // Login и убеждаемся что user в store. Если убить access token,
    // следующий /api request должен пройти через refresh + retry.
    // Чистого способа expire'нуть только access из теста нет - access
    // в memory (не httpOnly cookie). Тест проверяет что после reload
    // страницы (когда user в localStorage есть, но access восстанавливается
    // через refresh cookie) - возвращаемся на /topics без перехода на login
    await loginAsAdmin(page);
    await expectLoggedIn(page);
    // Hard reload - access token из memory теряется, должен сработать
    // refresh flow на первом /api/v1/me запросе
    await page.reload();
    await expect(page).toHaveURL(/\/topics$/);
  });
});
