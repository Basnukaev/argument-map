import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

export interface TopicOptions {
  title?: string;
  description?: string;
  rootQuestion?: string;
  visibility?: 'PRIVATE' | 'PUBLIC' | 'SHARED';
}

/**
 * Создать тему через /topics/new. Возвращает topicId извлечённый из
 * URL после navigation на /topics/:id.
 *
 * Если visibility не PRIVATE - кликает соответствующий radio. На
 * странице 3 radio'а: PRIVATE / SHARED / PUBLIC.
 */
export async function createTestTopic(
  page: Page,
  options: TopicOptions = {},
): Promise<string> {
  const suffix = Math.random().toString(36).slice(2, 8);
  const title = options.title ?? `E2E Topic ${suffix}`;
  const rootQuestion =
    options.rootQuestion ?? `Тестовый корневой вопрос ${suffix}?`;

  await page.goto('/topics/new');
  // Поле "Название" - первый text input
  await page.locator('input[type="text"]').first().fill(title);
  if (options.description) {
    await page.locator('textarea').first().fill(options.description);
  }
  // Корневой вопрос - последний textarea
  const textareas = page.locator('textarea');
  const count = await textareas.count();
  await textareas.nth(count - 1).fill(rootQuestion);

  if (options.visibility && options.visibility !== 'PRIVATE') {
    // VisibilityRadioGroup рендерит radios с sr-only - кликаем через
    // dispatchEvent чтобы обойти overlay-иконку которая перехватывает pointer
    await page
      .locator(`input[type="radio"][value="${options.visibility}"]`)
      .dispatchEvent('click');
  }

  await page.locator('button[type="submit"]').click();
  // После create - navigate на /topics/:id
  await page.waitForURL(/\/topics\/[0-9a-f-]{36}$/, { timeout: 10_000 });
  const url = new URL(page.url());
  const topicId = url.pathname.split('/').pop();
  if (!topicId) {
    throw new Error(`Failed to extract topicId from URL: ${page.url()}`);
  }
  return topicId;
}

/**
 * Удалить тему через TopicSettingsDrawer на странице графа. Если drawer
 * не открыт - открывает его кликом по Settings icon.
 *
 * Delete requires typing the topic name to confirm.
 */
export async function deleteTopic(
  page: Page,
  topicId: string,
  topicTitle: string,
): Promise<void> {
  await page.goto(`/topics/${topicId}`);
  // Жду что граф загрузился (Header crumb виден)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 10_000 });

  // Settings IconButton (gear icon)
  await page.getByRole('button', { name: /настройки темы|topic settings/i }).click();

  // В drawer ищем кнопку Delete (destructive) и подтверждаем через input
  const deleteBtn = page.getByRole('button', { name: /удалить тему|delete topic/i });
  await deleteBtn.click();

  // Confirm input - надо вписать название
  const confirmInput = page.locator('input[type="text"]').last();
  await confirmInput.fill(topicTitle);

  // Финальная кнопка - "Удалить навсегда" / similar
  await page
    .getByRole('button', { name: /удалить навсегда|confirm delete|подтвердить/i })
    .click();

  await page.waitForURL((url) => url.pathname === '/topics', { timeout: 10_000 });
}
