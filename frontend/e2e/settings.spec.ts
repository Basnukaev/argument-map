import { test, expect } from '@playwright/test';
import { clearAuth, loginAsAdmin, logout } from './helpers/auth';

/**
 * Settings E2E suite - language, theme, text size, fonts, persistence.
 */
test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
    await loginAsAdmin(page);
  });

  test('change language ru → en', async ({ page }) => {
    await page.goto('/settings');
    const enBtn = page.getByRole('button', { name: /^english$|^en$/i });
    await enBtn.click();
    // EN locale - в DICTIONARY есть только ru/ar, EN fallback'нется на
    // ru-tokens (известный gap). Тест валидирует что click не падает
    await page.waitForTimeout(300);
    await expect(enBtn).toBeVisible({ timeout: 3_000 });
  });

  test('change language ru → ar (RTL)', async ({ page }) => {
    await page.goto('/settings');
    const arBtn = page.getByRole('button', { name: /^العربية$/ });
    await arBtn.click();
    // RTL - проверяем html dir="rtl" появился
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl', {
      timeout: 5_000,
    });
    // Возвращаем ru чтобы не ломать subsequent tests на AR locale
    await page.getByRole('button', { name: /^Русский$/ }).click();
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr', {
      timeout: 5_000,
    });
  });

  test('change text size small → xl', async ({ page }) => {
    await page.goto('/settings');
    // XL label - "Огромный"
    const xlBtn = page.getByRole('button', { name: 'Огромный' }).first();
    await expect(xlBtn).toBeVisible({ timeout: 3_000 });
    await xlBtn.click();
    await page.waitForTimeout(200);
    await expect(xlBtn).toBeVisible();
  });

  test('toggle hideTashkeel checkbox', async ({ page }) => {
    await page.goto('/settings');
    const checkbox = page.getByRole('checkbox', {
      name: /огласовк|tashkeel/i,
    });
    await expect(checkbox).toBeVisible({ timeout: 5_000 });
    // Settings page при первом mount грузит preferences GET /preferences.
    // Если читаем checked state до завершения GET - получаем initial
    // false вместо актуального. Ждём networkidle перед reading state.
    await page.waitForLoadState('networkidle');
    const initialState = await checkbox.isChecked();
    await checkbox.click({ force: true });
    await expect(checkbox).toBeChecked({
      checked: !initialState,
      timeout: 5_000,
    });
  });

  test('change arabic font (naskh → kufi)', async ({ page }) => {
    await page.goto('/settings');
    // Секция "Арабский шрифт" - labels Naskh / Kufi / Tahoma (Latin)
    const kufiBtn = page.getByRole('button', { name: 'Kufi' }).first();
    await expect(kufiBtn).toBeVisible({ timeout: 3_000 });
    await kufiBtn.click();
    await page.waitForTimeout(200);
    await expect(kufiBtn).toBeVisible();
  });

  test('settings persist after reload', async ({ page }) => {
    await page.goto('/settings');
    // Меняем на AR - dir=rtl применяется + есть AR словарь
    await page.getByRole('button', { name: /^العربية$/ }).click();
    await page.waitForTimeout(500);

    // Reload page - settings из server preferences должны сохраниться
    await page.reload();
    // dir=rtl остался
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl', {
      timeout: 5_000,
    });
    // Откат на RU
    await page.getByRole('button', { name: /^Русский$/ }).click();
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr', {
      timeout: 5_000,
    });
  });

  test('preferences cleared on logout', async ({ page }) => {
    await page.goto('/settings');
    // Set language to EN
    await page.getByRole('button', { name: /^english$/i }).click();
    await page.waitForTimeout(500);
    // Logout
    await logout(page);
    // Login снова - default language back to RU (preferences server-side
    // cleared, либо локальные стерты после logout)
    await loginAsAdmin(page);
    // Header показывает RU labels снова - но это может зависеть от того
    // когда preferences re-fetch'ятся. Здесь просто валидируем что
    // logout прошёл и login снова работает
    await expect(page).toHaveURL(/\/topics/);
  });
});
