/**
 * Schema tests для Tashkeel mark. Без attrs - проверяем регистрацию,
 * parseHTML/renderHTML round-trip и toggle поведение через commands
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { Tashkeel } from './Tashkeel';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, Tashkeel],
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

describe('Tashkeel mark', () => {
  test('регистрирует mark tashkeel в schema', () => {
    const editor = makeEditor();
    expect(editor.schema.marks.tashkeel).toBeDefined();
    expect(editor.schema.nodes.tashkeel).toBeUndefined();
  });

  test('parseHTML распознаёт span[data-type="tashkeel"]', () => {
    const html = '<p>обычный <span data-type="tashkeel" class="tashkeel">بِسْمِ</span> текст</p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ marks?: Array<{ type?: string }> }> }
      | undefined;
    const tashkeelText = paragraph?.content?.find((c) =>
      c.marks?.some((m) => m.type === 'tashkeel'),
    );
    expect(tashkeelText).toBeDefined();
  });

  test('renderHTML создаёт span с data-type + class="tashkeel"', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'بِسْمِ',
              marks: [{ type: 'tashkeel' }],
            },
          ],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="tashkeel"');
    expect(html).toContain('class="tashkeel"');
  });

  test('setTashkeel применяет mark к selection', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'بسم' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setTashkeel();
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ marks?: Array<{ type?: string }> }> }
      | undefined;
    const marks = paragraph?.content?.[0]?.marks ?? [];
    expect(marks.find((m) => m.type === 'tashkeel')).toBeDefined();
  });

  test('toggleTashkeel снимает mark если уже применён', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'بِسْمِ',
              marks: [{ type: 'tashkeel' }],
            },
          ],
        },
      ],
    });
    editor.commands.selectAll();
    editor.commands.toggleTashkeel();
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ marks?: Array<{ type?: string }> }> }
      | undefined;
    const marks = paragraph?.content?.[0]?.marks ?? [];
    expect(marks.find((m) => m.type === 'tashkeel')).toBeUndefined();
  });
});
