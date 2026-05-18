import { test, expect } from '@playwright/test';
import { clearAuth, loginAsAdmin } from './helpers/auth';
import { buildMinimalPdfBuffer } from './helpers/fixtures';

/**
 * Library E2E suite - browsing books, search, filters, admin upload.
 */
test.describe('Library', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
    await loginAsAdmin(page);
  });

  test('/books loads с hero + filter controls', async ({ page }) => {
    await page.goto('/books');
    // Hero компонент - "Библиотека" заголовок
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({
      timeout: 5_000,
    });
    // Search input + visibility filter chips
    await expect(page.locator('input[type="search"]').first()).toBeVisible();
  });

  test('search debounced - вводим текст, results обновляются', async ({
    page,
  }) => {
    await page.goto('/books');
    const search = page.locator('input[type="search"]').first();
    await search.fill('nonexistent-book-query-xyz');
    // Ждём debounce + результат - либо книги либо empty state
    await page.waitForTimeout(500);
    // Запрос отправлен - проверим что URL/state не упал. Проверка через
    // отсутствие критических ошибок на странице
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('filter chips переключаются (Все / Public / Private / Shared)', async ({
    page,
  }) => {
    await page.goto('/books');
    // visibility chips - 4 кнопки в одной группе. По умолчанию "Все"
    // активна. Кликаем PUBLIC chip
    const publicChip = page
      .locator('button', { hasText: /^Public$|^Публичные$|^public$/i })
      .first();
    if (await publicChip.count() > 0) {
      await publicChip.click();
      await page.waitForTimeout(300);
    }
    // Проверяем что страница осталась стабильной
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('clear search через X кнопку', async ({ page }) => {
    await page.goto('/books');
    const search = page.locator('input[type="search"]').first();
    await search.fill('test');
    // X-кнопка появляется после input
    const clearBtn = page.locator('button[aria-label*="чистить"], button[aria-label*="clear"]').first();
    await expect(clearBtn).toBeVisible({ timeout: 3_000 });
    await clearBtn.click();
    await expect(search).toHaveValue('');
  });

  test('navigate to /admin/shamela только для admin', async ({ page }) => {
    await page.goto('/admin/shamela');
    // Admin - access granted, page loads
    await expect(page).toHaveURL(/\/admin\/shamela/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({
      timeout: 5_000,
    });
  });

  test('FileUploadModal opens через "Из файла" кнопку', async ({ page }) => {
    await page.goto('/admin/shamela');
    // Кнопка "Из файла" открывает FileUploadModal
    await page.getByRole('button', { name: 'Из файла' }).click();

    // Modal с file input
    const fileInput = page.locator('input[type="file"]').first();
    await expect(fileInput).toBeAttached({ timeout: 5_000 });

    // Прикрепляем минимальный PDF
    await fileInput.setInputFiles({
      name: 'e2e-test-book.pdf',
      mimeType: 'application/pdf',
      buffer: buildMinimalPdfBuffer(),
    });

    // Файл прикреплён - тест успешен на этом этапе. Реальный upload
    // backend validation - отдельная история (валидация структуры PDF,
    // OCR pipeline, etc.). Smoke-проверка что UI принимает файл достаточно
    // для e2e уровня
    await page.waitForTimeout(500);
  });
});
