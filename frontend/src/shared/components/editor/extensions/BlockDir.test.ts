/**
 * Tests для BlockDir extension - bidi-фикс через dir="auto" на блочных
 * узлах. Проверяем что атрибут попадает в HTML, но НЕ раздувает JSON
 * (round-trip остаётся чистым).
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { BlockDir } from './BlockDir';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, BlockDir],
    content: initialContent ?? { type: 'doc', content: [{ type: 'paragraph' }] },
  });
  activeEditors.push(editor);
  return editor;
}

afterEach(() => {
  while (activeEditors.length > 0) {
    const e = activeEditors.pop();
    e?.destroy();
  }
});

describe('BlockDir extension', () => {
  test('renderHTML добавляет dir="auto" на paragraph', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'مرحبا.' }] }],
    });
    expect(editor.getHTML()).toContain('dir="auto"');
  });

  test('dir="auto" на heading и blockquote', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'باب' }] },
        { type: 'blockquote', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'قال' }] }] },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('<h2 dir="auto"');
    expect(html).toContain('<blockquote dir="auto"');
  });

  test('dir НЕ попадает в getJSON() - сериализация остаётся чистой', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'بسم الله' }] }],
    });
    const json = editor.getJSON();
    const para = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(para?.type).toBe('paragraph');
    // attrs либо отсутствует, либо не содержит dir (он = default null)
    expect(para?.attrs?.dir ?? undefined).toBeUndefined();
  });

  test('сохранённый dir в HTML не читается обратно в модель (всегда auto-detect)', () => {
    const editor = makeEditor();
    // имитируем legacy HTML с захардкоженным dir
    editor.commands.setContent('<p dir="ltr">العربية</p>');
    const json = editor.getJSON();
    const para = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(para?.attrs?.dir ?? undefined).toBeUndefined();
    // на выходе снова auto
    expect(editor.getHTML()).toContain('dir="auto"');
  });
});
