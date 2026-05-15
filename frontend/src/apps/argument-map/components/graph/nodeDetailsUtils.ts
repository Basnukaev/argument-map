/**
 * Форматирование дат - через useFormatDate из @/shared/i18n (локаль-aware).
 * Этот файл оставлен для shortId и других чистых утилит узлов
 */

export function shortId(id?: string): string {
  if (!id) return '—';
  return id.slice(0, 8);
}
