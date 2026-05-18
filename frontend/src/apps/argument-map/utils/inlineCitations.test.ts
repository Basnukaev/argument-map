import { describe, expect, it } from 'vitest';
import { hasInlineCitations, parseInlineCitations } from './inlineCitations';

describe('parseInlineCitations', () => {
  it('возвращает пустой массив для пустой строки', () => {
    expect(parseInlineCitations('')).toEqual([]);
  });

  it('возвращает один text-сегмент если нет маркеров', () => {
    expect(parseInlineCitations('просто текст без citations')).toEqual([
      { type: 'text', text: 'просто текст без citations' },
    ]);
  });

  it('парсит одиночный маркер в середине текста', () => {
    expect(parseInlineCitations('Доказательство [1] - см.')).toEqual([
      { type: 'text', text: 'Доказательство ' },
      { type: 'citation', text: '[1]', ordinal: 1 },
      { type: 'text', text: ' - см.' },
    ]);
  });

  it('парсит маркер в начале строки', () => {
    expect(parseInlineCitations('[1] начало')).toEqual([
      { type: 'citation', text: '[1]', ordinal: 1 },
      { type: 'text', text: ' начало' },
    ]);
  });

  it('парсит маркер в конце строки', () => {
    expect(parseInlineCitations('конец [3]')).toEqual([
      { type: 'text', text: 'конец ' },
      { type: 'citation', text: '[3]', ordinal: 3 },
    ]);
  });

  it('парсит несколько маркеров подряд', () => {
    expect(parseInlineCitations('пруфы [1][2][3]')).toEqual([
      { type: 'text', text: 'пруфы ' },
      { type: 'citation', text: '[1]', ordinal: 1 },
      { type: 'citation', text: '[2]', ordinal: 2 },
      { type: 'citation', text: '[3]', ordinal: 3 },
    ]);
  });

  it('парсит многозначные ordinals', () => {
    expect(parseInlineCitations('много источников [10] и [42]')).toEqual([
      { type: 'text', text: 'много источников ' },
      { type: 'citation', text: '[10]', ordinal: 10 },
      { type: 'text', text: ' и ' },
      { type: 'citation', text: '[42]', ordinal: 42 },
    ]);
  });

  it('игнорирует не-числовые скобки', () => {
    expect(parseInlineCitations('[abc] не citation, а [12] - да')).toEqual([
      { type: 'text', text: '[abc] не citation, а ' },
      { type: 'citation', text: '[12]', ordinal: 12 },
      { type: 'text', text: ' - да' },
    ]);
  });

  it('сохраняет переносы строк в text сегментах', () => {
    expect(parseInlineCitations('первая строка\nвторая [1]\nтретья')).toEqual([
      { type: 'text', text: 'первая строка\nвторая ' },
      { type: 'citation', text: '[1]', ordinal: 1 },
      { type: 'text', text: '\nтретья' },
    ]);
  });

  it('работает с RTL/арабским текстом', () => {
    expect(parseInlineCitations('قال النبي ﷺ [1] حديث صحيح')).toEqual([
      { type: 'text', text: 'قال النبي ﷺ ' },
      { type: 'citation', text: '[1]', ordinal: 1 },
      { type: 'text', text: ' حديث صحيح' },
    ]);
  });

  it('stateful regex - повторный вызов даёт тот же результат', () => {
    const input = 'a [1] b [2] c';
    const r1 = parseInlineCitations(input);
    const r2 = parseInlineCitations(input);
    expect(r1).toEqual(r2);
  });
});

describe('hasInlineCitations', () => {
  it('false для пустой строки', () => {
    expect(hasInlineCitations('')).toBe(false);
  });

  it('false если нет маркеров', () => {
    expect(hasInlineCitations('plain text')).toBe(false);
  });

  it('true для строки с одним маркером', () => {
    expect(hasInlineCitations('see [1]')).toBe(true);
  });

  it('stateful regex - повторный вызов даёт тот же результат', () => {
    const input = 'see [1]';
    expect(hasInlineCitations(input)).toBe(true);
    expect(hasInlineCitations(input)).toBe(true);
  });
});
