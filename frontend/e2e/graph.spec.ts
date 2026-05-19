import { test, expect } from '@playwright/test';
import { clearAuth, loginAsAdmin } from './helpers/auth';
import { createTestTopic } from './helpers/topics';

/**
 * Graph E2E suite - тестируем работу с узлами/рёбрами на React Flow
 * канвасе. Многие interactions со связями требуют drag-and-drop с
 * pixel-точными координатами handles, что в headless mode flaky -
 * фокусируемся на context-menu / keyboard actions которые более
 * стабильны.
 */
test.describe('Graph', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuth(page);
    await loginAsAdmin(page);
    // createTestTopic ведёт на /topics/{id}/graph - id не нужен в самих
    // тестах, навигация делается уже в helper'е
    await createTestTopic(page, {
      title: `E2E Graph ${Date.now()}`,
    });
    // Ждём что граф загрузился - React Flow viewport показывает root QUESTION
    await page.waitForSelector('.react-flow__viewport', { timeout: 10_000 });
  });

  test('add node через right-click context menu', async ({ page }) => {
    // Правый клик по пустой области канваса
    const pane = page.locator('.react-flow__pane').first();
    await pane.click({ button: 'right', position: { x: 400, y: 400 } });

    // Должен открыться context menu с "Создать узел здесь"
    await page
      .getByRole('menuitem', { name: /создать узел здесь|create node here/i })
      .click();

    // AddNodeModal открылся - вводим content
    const dialog = page.locator('dialog[open]');
    await expect(dialog).toBeVisible();
    await dialog.locator('#node-content').fill('E2E новый узел из контекстного меню');
    await dialog.getByRole('button', { name: /создать|create/i }).click();

    // Modal закрылся
    await expect(dialog).not.toBeVisible({ timeout: 5_000 });
    // Узел появился на canvas
    await expect(
      page.locator('text=E2E новый узел из контекстного меню').first(),
    ).toBeVisible({ timeout: 5_000 });
  });

  test('add node через context-menu на существующем узле (related)', async ({
    page,
  }) => {
    // Root QUESTION узел - правый клик показывает related options:
    // "Уточняющий вопрос" / "Тезис-ответ" (см. dictionary related.*).
    // Для context-menu используем .react-flow__node и hover перед click,
    // RF иногда не реагирует на contextmenu без hover-active.
    const rootNode = page.locator('.react-flow__node').first();
    await rootNode.hover();
    await rootNode.click({ button: 'right' });

    // Ждём menu - role=menu появляется. Кликаем "Тезис-ответ" (Claim)
    await page
      .getByRole('menuitem', { name: /тезис-ответ|уточняющий вопрос/i })
      .first()
      .click();

    const dialog = page.locator('dialog[open]');
    await expect(dialog).toBeVisible();
    await dialog.locator('#node-content').fill('Связанный узел E2E');
    await dialog.getByRole('button', { name: /создать|create/i }).click();

    await expect(dialog).not.toBeVisible({ timeout: 5_000 });
    await expect(page.locator('text=Связанный узел E2E').first()).toBeVisible({
      timeout: 5_000,
    });
  });

  test('delete node через context menu', async ({ page }) => {
    // Создаём добавочный узел через related options - чтобы он точно
    // оказался viewport-friendly (рядом с root)
    const rootNode = page.locator('.react-flow__node').first();
    await rootNode.hover();
    await rootNode.click({ button: 'right' });
    await page
      .getByRole('menuitem', { name: /тезис-ответ|уточняющий вопрос/i })
      .first()
      .click();
    const dialog = page.locator('dialog[open]');
    await dialog.locator('#node-content').fill('Узел для удаления E2E');
    await dialog.getByRole('button', { name: /создать/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5_000 });
    await expect(
      page.locator('text=Узел для удаления E2E').first(),
    ).toBeVisible({ timeout: 5_000 });

    // Right-click на новом узле через позицию (RF context menu opens
    // at mouse coords, hover ensures node receives event)
    const targetNode = page.locator('.react-flow__node', {
      hasText: 'Узел для удаления E2E',
    });
    await targetNode.hover();
    await targetNode.click({ button: 'right' });
    await page.getByRole('menuitem', { name: /^удалить$|^delete$/i }).first().click();

    // Узел исчез
    await expect(
      page.locator('.react-flow__node', { hasText: 'Узел для удаления E2E' }),
    ).toHaveCount(0, { timeout: 5_000 });
  });

  test('open node details через double-click', async ({ page }) => {
    const rootNode = page.locator('.react-flow__node').first();
    await rootNode.dblclick();

    // NodeDetailsPanel открывается - aria-label "Детали узла"
    await expect(
      page.locator('[aria-label*="Детали узла"], [aria-label*="Node details"]').first(),
    ).toBeVisible({ timeout: 5_000 });
  });

  test('z-order bring to front через context menu', async ({ page }) => {
    // Создаём добавочный узел через related options + тестируем что Z-order
    // операция вызывается без ошибок (реальный pixel z-comparison
    // выходит за scope - бэкенд получает z-coordinate update)
    const rootNode = page.locator('.react-flow__node').first();
    await rootNode.hover();
    await rootNode.click({ button: 'right' });
    await page
      .getByRole('menuitem', { name: /тезис-ответ|уточняющий вопрос/i })
      .first()
      .click();
    const dialog = page.locator('dialog[open]');
    await dialog.locator('#node-content').fill('Z-Order test E2E');
    await dialog.getByRole('button', { name: /создать/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5_000 });

    const targetNode = page.locator('.react-flow__node', {
      hasText: 'Z-Order test E2E',
    });
    await targetNode.hover();
    await targetNode.click({ button: 'right' });
    await page
      .getByRole('menuitem', { name: /на передний план|bring to front/i })
      .click();
    // Узел остался видим - операция не упала
    await expect(targetNode).toBeVisible();
  });

  test('multi-select Shift+click → FloatingActionBar появляется', async ({
    page,
  }) => {
    // Создаём 2 related узла к root для гарантированной видимости
    for (let i = 0; i < 2; i++) {
      const rootNode = page.locator('.react-flow__node').first();
      await rootNode.hover();
      await rootNode.click({ button: 'right' });
      await page
        .getByRole('menuitem', { name: /тезис-ответ|уточняющий вопрос/i })
        .first()
        .click();
      const dialog = page.locator('dialog[open]');
      await dialog.locator('#node-content').fill(`Multi ${i} E2E`);
      await dialog.getByRole('button', { name: /создать/i }).click();
      await expect(dialog).not.toBeVisible({ timeout: 5_000 });
    }

    // Click + Shift+click на 2 новых узлах
    const nodes = page.locator('.react-flow__node');
    await nodes.nth(1).click();
    await nodes.nth(2).click({ modifiers: ['Shift'] });

    // FloatingActionBar - aria-label содержит "Выбрано {count}..."
    await expect(
      page.locator('[aria-label*="ыбран"], [aria-label*="elected"]').first(),
    ).toBeVisible({ timeout: 5_000 });
  });

  test('search через CommandPalette Alt+K', async ({ page }) => {
    await page.keyboard.press('Alt+K');
    // CommandPalette - div role=dialog с aria-label "Командная палитра"
    const palette = page.getByRole('dialog', {
      name: /командная палитра|command palette/i,
    });
    await expect(palette).toBeVisible({ timeout: 3_000 });
    await palette.locator('input').first().fill('топик');
    // results появляются (либо empty state если ничего не найдено)
    await page.keyboard.press('Escape');
    await expect(palette).not.toBeVisible({ timeout: 3_000 });
  });
});
