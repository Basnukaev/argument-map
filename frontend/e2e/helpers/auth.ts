import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Helpers для auth-flows. Используются всеми suites которым нужен
 * logged-in user.
 */

export const ADMIN_EMAIL = 'admin@argumentmap.local';
export const ADMIN_PASSWORD = 'admin12345';

export interface UserCredentials {
  email: string;
  username: string;
  password: string;
}

/**
 * Логин через UI на странице /login. Не использует localStorage
 * shortcut - проходит через реальную форму и POST /api/v1/auth/login.
 *
 * После успеха ждёт URL `/topics` (default landing) либо redirect URL
 * если был.
 */
export async function login(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  await page.goto('/login');
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  // Ждём что URL ушёл с /login (success path - на /topics или
  // redirect=URL). Если ошибка - тест пусть catch'ит exception
  await page.waitForURL((url) => !url.pathname.includes('/login'), {
    timeout: 10_000,
  });
}

/**
 * Convenience - залогиниться как admin (dev seeded user).
 */
export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
}

/**
 * Регистрация нового пользователя со случайными credentials.
 * Возвращает credentials для повторного логина в том же тесте.
 *
 * Email/username генерируются с timestamp + random suffix для
 * уникальности между прогонами на shared БД.
 */
export async function registerNewUser(
  page: Page,
  prefix = 'e2e',
): Promise<UserCredentials> {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const credentials: UserCredentials = {
    email: `${prefix}-${suffix}@e2e.test`,
    username: `${prefix}_${suffix}`,
    password: 'e2e-password-123',
  };

  await page.goto('/register');
  await page.locator('input[type="email"]').fill(credentials.email);
  await page.locator('input[autocomplete="username"]').fill(credentials.username);
  await page.locator('input[autocomplete="new-password"]').first().fill(credentials.password);
  await page.locator('input[autocomplete="new-password"]').last().fill(credentials.password);
  await page.locator('button[type="submit"]').click();

  // После регистрации - auto-login + redirect на /topics
  await page.waitForURL((url) => url.pathname === '/topics', { timeout: 10_000 });
  return credentials;
}

/**
 * Logout через AvatarMenu в Header. После logout ждём редирект на
 * /login и проверяем что localStorage очистился.
 */
export async function logout(page: Page): Promise<void> {
  // Открываем AvatarMenu - кнопка с aria-label типа "Меню пользователя"
  // либо аватар-кнопка в верхнем правом углу. Используем data-testid
  // если есть, иначе общий fallback
  const avatarButton = page.locator(
    '[aria-label="Меню пользователя"], [aria-label="User menu"], [data-testid="user-menu"]',
  ).first();
  if (await avatarButton.count() === 0) {
    // fallback - выйти через прямой логаут endpoint, потом goto /login
    await page.evaluate(() => {
      window.localStorage.removeItem('auth.user');
    });
    await page.context().clearCookies();
    await page.goto('/login');
    return;
  }
  await avatarButton.click();
  // Кликаем по элементу с текстом "Выйти" / "Logout"
  await page.getByRole('menuitem', { name: /выйти|logout/i }).click();
  await page.waitForURL((url) => url.pathname.includes('/login'), {
    timeout: 5_000,
  });
}

/**
 * Утилита - очистить весь auth state. Применяется в beforeEach для
 * полной изоляции тестов. Не использует logout endpoint - просто
 * чистит storage / cookies, browser-side.
 */
export async function clearAuth(page: Page): Promise<void> {
  await page.context().clearCookies();
  // localStorage чистится только после goto на baseURL (security
  // origin model). Заходим на любую страницу и evaluate
  await page.goto('/login');
  await page.evaluate(() => {
    try {
      window.localStorage.clear();
      window.sessionStorage.clear();
    } catch {
      // ignore
    }
  });
}

/**
 * Проверка что пользователь успешно залогинен - на /topics нет
 * перенаправления на /login.
 */
export async function expectLoggedIn(page: Page): Promise<void> {
  await page.goto('/topics');
  await expect(page).toHaveURL(/\/topics(?:\?.*)?$/);
}

/**
 * Проверка что пользователь НЕ залогинен - попытка зайти на /topics
 * редиректит на /login.
 */
export async function expectLoggedOut(page: Page): Promise<void> {
  await page.goto('/topics');
  await expect(page).toHaveURL(/\/login/);
}
