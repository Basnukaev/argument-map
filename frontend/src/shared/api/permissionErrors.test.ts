import { describe, it, expect } from 'vitest';
import { ApiError } from '@/shared/api/client';
import type { DictKey } from '@/shared/i18n';
import { formatPermissionError } from './permissionErrors';

/**
 * Stub i18n - возвращает сам key чтобы можно было проверить какой ключ
 * был запрошен. Реальный hook useT перевёл бы в человекочитаемую строку
 */
const tStub = (key: DictKey): string => key;

function apiError(typeSuffix: string, status = 403): ApiError {
  return new ApiError(status, {
    type: `https://argument-map.example.com/problems/${typeSuffix}`,
    title: typeSuffix,
    status,
  });
}

describe('formatPermissionError', () => {
  it('forbidden-topic-access → topic.permission.forbidden_access', () => {
    const err = apiError('forbidden-topic-access');
    expect(formatPermissionError(err, tStub)).toBe(
      'topic.permission.forbidden_access',
    );
  });

  it('forbidden-topic-write → topic.permission.forbidden_write', () => {
    const err = apiError('forbidden-topic-write');
    expect(formatPermissionError(err, tStub)).toBe(
      'topic.permission.forbidden_write',
    );
  });

  it('forbidden-book-access → book.permission.forbidden_access', () => {
    const err = apiError('forbidden-book-access');
    expect(formatPermissionError(err, tStub)).toBe(
      'book.permission.forbidden_access',
    );
  });

  it('forbidden-book-write → book.permission.forbidden_write', () => {
    const err = apiError('forbidden-book-write');
    expect(formatPermissionError(err, tStub)).toBe(
      'book.permission.forbidden_write',
    );
  });

  it('non-permission ApiError → null (fallback caller сам обрабатывает)', () => {
    const err = apiError('node-not-found', 404);
    expect(formatPermissionError(err, tStub)).toBeNull();
  });

  it('не-ApiError → null', () => {
    expect(formatPermissionError(new Error('boom'), tStub)).toBeNull();
    expect(formatPermissionError('string', tStub)).toBeNull();
    expect(formatPermissionError(null, tStub)).toBeNull();
    expect(formatPermissionError(undefined, tStub)).toBeNull();
  });
});
