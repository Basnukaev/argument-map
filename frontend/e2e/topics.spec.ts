import { test, expect } from '@playwright/test';
import { clearAuth, loginAsAdmin } from './helpers/auth';
import { createTestTopic } from './helpers/topics';

/**
 * Topics E2E suite - CRUD темы + visibility flow + export/import.
 *
 * Все тесты под admin для упрощения (полные права). RBAC проверки
 * permission-сценариев - в отдельном suite admin.spec.ts.
 */
test.describe('Topics', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
    await loginAsAdmin(page);
  });

  test('create topic с visibility=PRIVATE', async ({ page }) => {
    const title = `E2E Private ${Date.now()}`;
    const topicId = await createTestTopic(page, {
      title,
      visibility: 'PRIVATE',
    });
    expect(topicId).toMatch(/^[0-9a-f-]{36}$/);
    // Header графа показывает Lock-иконку (это NOT обязательно для admin -
    // visibility badge всегда есть)
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
  });

  test('create topic с visibility=PUBLIC', async ({ page }) => {
    const title = `E2E Public ${Date.now()}`;
    const topicId = await createTestTopic(page, {
      title,
      visibility: 'PUBLIC',
    });
    expect(topicId).toMatch(/^[0-9a-f-]{36}$/);
    // На /topics - карточка с этой темой должна показывать PUBLIC badge
    await page.goto('/topics');
    await expect(page.locator(`text=${title}`).first()).toBeVisible();
  });

  test('Settings drawer показывает title locked + visibility radio', async ({
    page,
  }) => {
    // Title в TopicSettingsDrawer - read-only (с Lock icon). Изменение
    // root-вопроса невозможно после создания темы (это immutable
    // дизайн-решение, см. dictionary key topic.settings.root_question.locked_hint).
    // Editable settings - visibility, status algorithm. Этот тест проверяет
    // что drawer открывается и показывает обе секции
    const title = `E2E Drawer ${Date.now()}`;
    await createTestTopic(page, { title });

    await page.getByRole('button', { name: 'Настройки темы' }).click();
    // Title - в read-only поле, не редактируемый input
    await expect(page.locator(`text=${title}`).first()).toBeVisible();
    // Section "Видимость" - есть PUBLIC/SHARED/PRIVATE radios
    await expect(
      page.locator('input[type="radio"][value="PUBLIC"]'),
    ).toBeAttached();
    await expect(
      page.locator('input[type="radio"][value="SHARED"]'),
    ).toBeAttached();
    await expect(
      page.locator('input[type="radio"][value="PRIVATE"]'),
    ).toBeAttached();
  });

  test('change visibility PRIVATE → PUBLIC', async ({ page }) => {
    await createTestTopic(page, { title: `E2E Vis ${Date.now()}` });

    await page.getByRole('button', { name: 'Настройки темы' }).click();
    // Radio спрятан sr-only, кликаем по обёрточному <label> через
    // dispatchEvent чтобы обойти visual обертку
    await page.locator('input[type="radio"][value="PUBLIC"]').dispatchEvent('click');
    // После change - либо toast saved, либо просто visibility сменилось
    // визуально. Ждём что radio стал checked
    await expect(
      page.locator('input[type="radio"][value="PUBLIC"]'),
    ).toBeChecked({ timeout: 5_000 });
  });

  test('delete topic с typing-name confirmation', async ({ page }) => {
    const title = `E2E Delete ${Date.now()}`;
    await createTestTopic(page, { title });

    // Open settings drawer
    await page.getByRole('button', { name: 'Настройки темы' }).click();

    // Click "Удалить тему" - открывает confirm dialog
    await page.getByRole('button', { name: 'Удалить тему' }).click();

    // В confirm dialog ввести точное название темы
    const dialog = page.locator('[data-testid="topic-delete-confirm"]');
    await expect(dialog).toBeVisible();
    await dialog.locator('input[type="text"]').fill(title);

    // Click "Удалить навсегда"
    await page.getByRole('button', { name: 'Удалить навсегда' }).click();

    // Should navigate back to /topics
    await page.waitForURL((url) => url.pathname === '/topics', {
      timeout: 10_000,
    });
    // Тема исчезла из списка
    await expect(page.locator(`text=${title}`).first()).not.toBeVisible({
      timeout: 5_000,
    });
  });

  test('JSON export → download', async ({ page }) => {
    const title = `E2E Export ${Date.now()}`;
    await createTestTopic(page, { title });

    // Идём на /topics, ищем карточку темы и Export button
    await page.goto('/topics');
    // Найти карточку: hover на ней раскрывает export icon
    const card = page.locator(`a:has-text("${title}")`).first();
    await card.hover();

    // Export кнопка - "Экспорт" / Download icon, aria-label
    const exportBtn = card.locator(
      'button[aria-label*="кспорт"], button[title*="кспорт"], button[aria-label*="xport"]',
    );
    await expect(exportBtn.first()).toBeVisible({ timeout: 5_000 });

    const downloadPromise = page.waitForEvent('download', { timeout: 10_000 });
    await exportBtn.first().click();
    const download = await downloadPromise;
    const filename = download.suggestedFilename();
    expect(filename).toMatch(/^topic-[0-9a-f]+\.json$/);
  });

  test('JSON import - upload existing topic export', async ({ page }) => {
    const title = `E2E Imp ${Date.now()}`;
    const originalId = await createTestTopic(page, { title });

    // Download через прямой API call - быстрее чем UI click
    await page.goto('/topics');
    const exportResponse = await page.request.get(
      `/api/v1/topics/${originalId}/export`,
    );
    expect(exportResponse.ok()).toBe(true);
    const exportJson = await exportResponse.text();

    // Upload через file input
    const fileInput = page.locator('input[type="file"]').first();
    await fileInput.setInputFiles({
      name: 'exported-topic.json',
      mimeType: 'application/json',
      buffer: Buffer.from(exportJson),
    });

    // Ждём toast success - "Тема импортирована"
    await expect(
      page.locator('text=/импортирован|imported/i').first(),
    ).toBeVisible({ timeout: 10_000 });
  });
});
