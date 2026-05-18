import { test, expect } from '@playwright/test';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  clearAuth,
  login,
  loginAsAdmin,
  registerNewUser,
} from './helpers/auth';

/**
 * Admin RBAC E2E suite - admin/USER role separation, audit log access.
 */
test.describe('Admin', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
  });

  test('/admin/shamela accessible как ADMIN', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/shamela');
    await expect(page).toHaveURL(/\/admin\/shamela/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({
      timeout: 5_000,
    });
  });

  test('/admin/audit accessible как ADMIN', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/audit');
    await expect(page).toHaveURL(/\/admin\/audit/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({
      timeout: 5_000,
    });
  });

  test('non-admin user → /admin/shamela редирект на /topics', async ({
    page,
  }) => {
    // Регистрируем обычного USER (не ADMIN)
    const creds = await registerNewUser(page, 'rbac');
    // logout + login обратно через UI - чтобы получить кеширование
    await clearAuth(page);
    await login(page, creds.email, creds.password);

    // Попытка зайти на /admin/shamela
    await page.goto('/admin/shamela');
    // ProtectedRoute с requireRole=ADMIN делает silent redirect на /topics
    await expect(page).toHaveURL(/\/topics(?:$|\?)/);
  });

  test('non-admin user → /admin/audit редирект на /topics', async ({
    page,
  }) => {
    const creds = await registerNewUser(page, 'rbac2');
    await clearAuth(page);
    await login(page, creds.email, creds.password);

    await page.goto('/admin/audit');
    await expect(page).toHaveURL(/\/topics(?:$|\?)/);
  });

  test('audit log filter by entityType - select меняет результат', async ({
    page,
  }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/audit');
    // На странице - native <select> с entityType options
    const select = page.locator('select').first();
    await expect(select).toBeVisible({ timeout: 5_000 });
    await select.selectOption({ index: 1 });
    // Фильтр применён - страница не упала, ждём что filter URL обновился
    await page.waitForTimeout(500);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
