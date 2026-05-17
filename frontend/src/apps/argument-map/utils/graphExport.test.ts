/**
 * Тесты для graphExport - filename slugify, фильтр исключаемых элементов,
 * download-trigger через мок `html-to-image`. Реальный render canvas не
 * тестируем - это responsibility html-to-image (внешняя либа) + Playwright
 * smoke в TopicGraphPage
 */
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('html-to-image', () => ({
  toPng: vi.fn(),
  toSvg: vi.fn(),
}));

import { toPng, toSvg } from 'html-to-image';
import {
  buildExportFilename,
  exportGraphAsPng,
  exportGraphAsSvg,
  isExcludedFromExport,
  slugifyForFilename,
  todayDateStamp,
} from './graphExport';

describe('slugifyForFilename', () => {
  test('латиница → нижний регистр + дефисы', () => {
    expect(slugifyForFilename('Hello World')).toBe('hello-world');
  });

  test('кириллица убирается (без транслитерации) - fallback topic', () => {
    // «Мавлид» → empty → 'topic' default. Простота важнее транслитерации,
    // пользователь идентифицирует файл по timestamp в filename
    expect(slugifyForFilename('Мавлид')).toBe('topic');
  });

  test('арабский убирается → fallback topic', () => {
    expect(slugifyForFilename('المولد')).toBe('topic');
  });

  test('смешанный - оставляет только латинскую часть', () => {
    expect(slugifyForFilename('Argument Map - Тест')).toBe('argument-map');
  });

  test('обрезает на 60 символах', () => {
    const long = 'a'.repeat(100);
    expect(slugifyForFilename(long).length).toBe(60);
  });

  test('null / undefined / пустая строка → topic', () => {
    expect(slugifyForFilename(null)).toBe('topic');
    expect(slugifyForFilename(undefined)).toBe('topic');
    expect(slugifyForFilename('')).toBe('topic');
  });

  test('убирает крайние дефисы и пробелы', () => {
    expect(slugifyForFilename('  hello  ')).toBe('hello');
    expect(slugifyForFilename('---test---')).toBe('test');
  });
});

describe('todayDateStamp', () => {
  test('формат YYYY-MM-DD с zero-padding', () => {
    const date = new Date('2026-03-05T14:30:00Z');
    // зависит от TZ - проверяем формат, не конкретное число
    const stamp = todayDateStamp(date);
    expect(stamp).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('buildExportFilename', () => {
  test('собирает topic-{slug}-{date}.{ext}', () => {
    const date = new Date('2026-05-17T12:00:00');
    const filename = buildExportFilename('My Topic', 'png', date);
    expect(filename).toMatch(/^topic-my-topic-\d{4}-\d{2}-\d{2}\.png$/);
  });

  test('арабский title → topic-topic-{date}.svg fallback', () => {
    const date = new Date('2026-05-17T12:00:00');
    const filename = buildExportFilename('المولد', 'svg', date);
    expect(filename).toMatch(/^topic-topic-\d{4}-\d{2}-\d{2}\.svg$/);
  });
});

describe('isExcludedFromExport', () => {
  function el(className: string): Element {
    const div = document.createElement('div');
    div.className = className;
    return div;
  }

  test('react-flow__controls → excluded', () => {
    expect(isExcludedFromExport(el('react-flow__controls'))).toBe(true);
  });

  test('react-flow__minimap → excluded', () => {
    expect(isExcludedFromExport(el('react-flow__minimap'))).toBe(true);
  });

  test('react-flow__attribution → excluded', () => {
    expect(isExcludedFromExport(el('react-flow__attribution'))).toBe(true);
  });

  test('react-flow__panel (toolbar/hotkey-hint) → excluded', () => {
    expect(isExcludedFromExport(el('react-flow__panel'))).toBe(true);
  });

  test('react-flow__node / react-flow__edge / другие → included', () => {
    expect(isExcludedFromExport(el('react-flow__node'))).toBe(false);
    expect(isExcludedFromExport(el('react-flow__edge'))).toBe(false);
    expect(isExcludedFromExport(el('react-flow__viewport'))).toBe(false);
    expect(isExcludedFromExport(el('some-custom-class'))).toBe(false);
  });
});

describe('exportGraphAsPng / Svg', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.mocked(toPng).mockResolvedValue('data:image/png;base64,FAKE');
    vi.mocked(toSvg).mockResolvedValue('data:image/svg+xml;base64,FAKE');
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.clearAllMocks();
    clickSpy.mockRestore();
  });

  test('PNG: вызывает toPng с pixelRatio + filter и триггерит download', async () => {
    const el = document.createElement('div');
    await exportGraphAsPng(el, 'test.png');

    expect(toPng).toHaveBeenCalledOnce();
    const opts = vi.mocked(toPng).mock.calls[0]![1]!;
    expect(opts.pixelRatio).toBe(2);
    expect(opts.backgroundColor).toBe('#ffffff');
    expect(typeof opts.filter).toBe('function');
    expect(clickSpy).toHaveBeenCalledOnce();
  });

  test('SVG: вызывает toSvg и триггерит download', async () => {
    const el = document.createElement('div');
    await exportGraphAsSvg(el, 'test.svg');

    expect(toSvg).toHaveBeenCalledOnce();
    expect(clickSpy).toHaveBeenCalledOnce();
  });

  test('кастомный pixelRatio пробрасывается в toPng', async () => {
    const el = document.createElement('div');
    await exportGraphAsPng(el, 'test.png', { pixelRatio: 4 });

    const opts = vi.mocked(toPng).mock.calls[0]![1]!;
    expect(opts.pixelRatio).toBe(4);
  });

  test('фильтр опций исключает react-flow controls', async () => {
    const el = document.createElement('div');
    await exportGraphAsPng(el, 'test.png');

    const opts = vi.mocked(toPng).mock.calls[0]![1]!;
    const controls = document.createElement('div');
    controls.className = 'react-flow__controls';
    const node = document.createElement('div');
    node.className = 'react-flow__node';

    expect(opts.filter!(controls)).toBe(false);
    expect(opts.filter!(node)).toBe(true);
  });
});
