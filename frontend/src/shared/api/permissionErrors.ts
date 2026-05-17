import { ApiError } from '@/shared/api/client';
import type { DictKey } from '@/shared/i18n';

type TFunction = (key: DictKey) => string;

/**
 * Маппинг ApiError на локализованную строку для permission-related ответов
 * бэка (ADR-043 + Amendment, Этап 22).
 *
 * Topics (22.b):
 * - `forbidden-topic-access` (403) → topic.permission.forbidden_access
 * - `forbidden-topic-write` (403) → topic.permission.forbidden_write
 *
 * Books (22.c / 22.c.f):
 * - `forbidden-book-access` (403) → book.permission.forbidden_access
 * - `forbidden-book-write` (403) → book.permission.forbidden_write
 *
 * Если ошибка не permission-relevant - возвращает null, вызывающий код
 * показывает свой fallback (formatApiError либо domain-специфичный текст)
 */
export function formatPermissionError(
  error: unknown,
  t: TFunction,
): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.is('forbidden-topic-access')) {
    return t('topic.permission.forbidden_access');
  }
  if (error.is('forbidden-topic-write')) {
    return t('topic.permission.forbidden_write');
  }
  if (error.is('forbidden-book-access')) {
    return t('book.permission.forbidden_access');
  }
  if (error.is('forbidden-book-write')) {
    return t('book.permission.forbidden_write');
  }
  return null;
}
