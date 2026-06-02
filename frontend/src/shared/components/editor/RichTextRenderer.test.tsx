/**
 * Tests для RichTextRenderer + wrapPlainTextAsDoc utility.
 * RichTextRenderer = read-only render используется в BookReaderPage
 * как fallback когда formatted_content == null.
 */
import { describe, expect, test } from 'vitest';
import { render } from '@testing-library/react';
import RichTextRenderer, { wrapPlainTextAsDoc } from './RichTextRenderer';
import { HadithBox } from './extensions/HadithBox';

describe('wrapPlainTextAsDoc', () => {
  test('пустой текст → пустой paragraph-doc', () => {
    const result = wrapPlainTextAsDoc('');
    expect(result).toEqual({ type: 'doc', content: [{ type: 'paragraph' }] });
  });

  test('null/undefined → пустой paragraph-doc', () => {
    expect(wrapPlainTextAsDoc(null)).toEqual({ type: 'doc', content: [{ type: 'paragraph' }] });
    expect(wrapPlainTextAsDoc(undefined)).toEqual({ type: 'doc', content: [{ type: 'paragraph' }] });
  });

  test('единственный параграф', () => {
    const result = wrapPlainTextAsDoc('hello world') as {
      content: { content: { text: string }[] }[];
    };
    expect(result.content[0]?.content[0]?.text).toBe('hello world');
  });

  test('двойной newline → два параграфа', () => {
    const result = wrapPlainTextAsDoc('first\n\nsecond') as {
      content: { content: { text: string }[] }[];
    };
    expect(result.content).toHaveLength(2);
    expect(result.content[0]?.content[0]?.text).toBe('first');
    expect(result.content[1]?.content[0]?.text).toBe('second');
  });
});

describe('RichTextRenderer', () => {
  test('рендерит plain ProseMirror doc как параграф', () => {
    const content = {
      type: 'doc',
      content: [
        { type: 'paragraph', content: [{ type: 'text', text: 'привет мир' }] },
      ],
    };
    const { container } = render(<RichTextRenderer content={content} />);
    expect(container.textContent).toContain('привет мир');
  });

  test('null content рендерит пустой контейнер без ошибок', () => {
    const { container } = render(<RichTextRenderer content={null} />);
    expect(container.firstChild).toBeDefined();
  });

  test('рендерит HadithBox node с extension', () => {
    const content = {
      type: 'doc',
      content: [
        {
          type: 'hadithBox',
          attrs: { source: 'Бухари', grade: 'sahih' },
          content: [
            { type: 'paragraph', content: [{ type: 'text', text: 'хадис текст' }] },
          ],
        },
      ],
    };
    const { container } = render(
      <RichTextRenderer content={content} extensions={[HadithBox]} />,
    );
    const hadithBox = container.querySelector('.hadith-box');
    expect(hadithBox).toBeInTheDocument();
    expect(hadithBox?.getAttribute('data-source')).toBe('Бухари');
    expect(hadithBox?.getAttribute('data-grade')).toBe('sahih');
    expect(container.textContent).toContain('хадис текст');
  });

  test('dir="rtl" применяется к wrapper для арабского контента', () => {
    const content = wrapPlainTextAsDoc('بسم الله');
    const { container } = render(<RichTextRenderer content={content} dir="rtl" />);
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.getAttribute('dir')).toBe('rtl');
  });
});
