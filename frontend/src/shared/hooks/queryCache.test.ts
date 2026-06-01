import { describe, it, expect, beforeEach } from 'vitest';
import {
  getCached,
  setCached,
  isFresh,
  invalidateCache,
  DEFAULT_TTL_MS,
} from './queryCache';

describe('queryCache', () => {
  beforeEach(() => {
    // Каждый тест стартует с чистого кэша — Map module-scoped, утечёт
    // между тестами иначе.
    invalidateCache();
  });

  it('set/get: возвращает записанные данные с timestamp', () => {
    setCached('/api/v1/topics', [{ id: '1' }]);
    const hit = getCached<Array<{ id: string }>>('/api/v1/topics');
    expect(hit).toBeDefined();
    expect(hit?.data).toEqual([{ id: '1' }]);
    expect(typeof hit?.ts).toBe('number');
  });

  it('get по отсутствующему ключу → undefined', () => {
    expect(getCached('/api/v1/nope')).toBeUndefined();
  });

  it('set перезаписывает существующий ключ', () => {
    setCached('/api/v1/x', 'old');
    setCached('/api/v1/x', 'new');
    expect(getCached<string>('/api/v1/x')?.data).toBe('new');
  });

  it('isFresh: недавняя запись свежая, устаревшая — нет', () => {
    const now = Date.now();
    expect(isFresh(now)).toBe(true);
    expect(isFresh(now - DEFAULT_TTL_MS - 1)).toBe(false);
    // кастомный ttl
    expect(isFresh(now - 100, 50)).toBe(false);
    expect(isFresh(now - 10, 50)).toBe(true);
  });

  it('invalidateCache(predicate): чистит только совпавшие ключи', () => {
    setCached('/api/v1/topics?page=0', 'a');
    setCached('/api/v1/topics/123', 'b');
    setCached('/api/v1/hadiths?page=0', 'c');

    invalidateCache((k) => k.startsWith('/api/v1/topics'));

    expect(getCached('/api/v1/topics?page=0')).toBeUndefined();
    expect(getCached('/api/v1/topics/123')).toBeUndefined();
    // не совпавший ключ остался
    expect(getCached<string>('/api/v1/hadiths?page=0')?.data).toBe('c');
  });

  it('invalidateCache() без predicate: чистит весь кэш', () => {
    setCached('/api/v1/a', 1);
    setCached('/api/v1/b', 2);

    invalidateCache();

    expect(getCached('/api/v1/a')).toBeUndefined();
    expect(getCached('/api/v1/b')).toBeUndefined();
  });
});
