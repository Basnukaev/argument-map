/**
 * Tests для stripTashkeel utility. Проверяем regex correctness на
 * классических Quran/hadith фразах, идемпотентность, рекурсивный
 * обход ProseMirror tree и opt-out при strip=false
 */
import { describe, expect, test } from 'vitest';
import { stripTashkeelText, stripTashkeelFromDoc } from './stripTashkeel';

describe('stripTashkeelText', () => {
  test('удаляет fatha/kasra/damma/sukun из basmala', () => {
    expect(stripTashkeelText('بِسْمِ اللَّهِ')).toBe('بسم الله');
  });

  test('удаляет shadda + tanwin', () => {
    expect(stripTashkeelText('الْحَمْدُ لِلَّهِ')).toBe('الحمد لله');
  });

  test('удаляет superscript alef (U+0670)', () => {
    // ٰ - khanjariyya, частая в Коране (الرَّحْمٰن)
    expect(stripTashkeelText('الرَّحْمٰن')).toBe('الرحمن');
  });

  test('текст без огласовок возвращает as-is', () => {
    expect(stripTashkeelText('بسم الله الرحمن الرحيم')).toBe(
      'بسم الله الرحمن الرحيم',
    );
  });

  test('пустая строка возвращает пустую строку', () => {
    expect(stripTashkeelText('')).toBe('');
  });

  test('латинский текст не трогается', () => {
    expect(stripTashkeelText('Hello world')).toBe('Hello world');
  });

  test('идемпотентность - повторный вызов даёт тот же результат', () => {
    const once = stripTashkeelText('بِسْمِ اللَّهِ');
    const twice = stripTashkeelText(once);
    expect(twice).toBe(once);
  });

  test('tatweel (U+0640) НЕ удаляется - это не диакритик', () => {
    // ـ - tatweel, горизонтальное растяжение буквы для каллиграфии
    expect(stripTashkeelText('محـمـد')).toBe('محـمـد');
  });
});

describe('stripTashkeelFromDoc', () => {
  test('strip=false возвращает input как есть', () => {
    const doc = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'بِسْمِ' }] }],
    };
    expect(stripTashkeelFromDoc(doc, false)).toBe(doc);
  });

  test('strip=true удаляет огласовки из text-node параграфа', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'بِسْمِ اللَّهِ' }],
        },
      ],
    };
    const result = stripTashkeelFromDoc(doc, true) as {
      content: { content: { text: string }[] }[];
    };
    expect(result.content[0]?.content[0]?.text).toBe('بسم الله');
  });

  test('рекурсия в nested структуру (HadithBox с параграфами внутри)', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'hadithBox',
          attrs: { source: 'Бухари' },
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: 'الْحَمْدُ' }],
            },
          ],
        },
      ],
    };
    const result = stripTashkeelFromDoc(doc, true) as {
      content: { content: { content: { text: string }[] }[] }[];
    };
    expect(result.content[0]?.content[0]?.content[0]?.text).toBe('الحمد');
  });

  test('сохраняет marks при transform', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'بِسْمِ',
              marks: [{ type: 'tashkeel' }, { type: 'bold' }],
            },
          ],
        },
      ],
    };
    const result = stripTashkeelFromDoc(doc, true) as {
      content: { content: { text: string; marks: { type: string }[] }[] }[];
    };
    const textNode = result.content[0]?.content[0];
    expect(textNode?.text).toBe('بسم');
    expect(textNode?.marks).toEqual([{ type: 'tashkeel' }, { type: 'bold' }]);
  });

  test('сохраняет attrs у блочных нод', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'hadithBox',
          attrs: { source: 'Бухари', grade: 'sahih' },
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: 'الْحَمْدُ' }],
            },
          ],
        },
      ],
    };
    const result = stripTashkeelFromDoc(doc, true) as {
      content: { attrs: { source: string; grade: string } }[];
    };
    expect(result.content[0]?.attrs).toEqual({ source: 'Бухари', grade: 'sahih' });
  });

  test('не мутирует исходный объект', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'بِسْمِ' }],
        },
      ],
    };
    stripTashkeelFromDoc(doc, true);
    expect(doc.content[0]?.content[0]?.text).toBe('بِسْمِ');
  });

  test('null/undefined doc - возвращает as-is', () => {
    expect(stripTashkeelFromDoc(null, true)).toBe(null);
    expect(stripTashkeelFromDoc(undefined, true)).toBe(undefined);
  });
});
