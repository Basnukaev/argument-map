/**
 * Schema tests для AyahBox extension. Аналогично HadithBox.test - через
 * Tiptap test-helper без полного DOM render. Проверяем что extension
 * правильно собирает node-spec (name / group / attributes / commands /
 * HTML serialization)
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { AyahBox } from './AyahBox';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, AyahBox],
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

describe('AyahBox extension', () => {
  test('регистрирует node ayahBox в schema с group=block и content=block+', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.ayahBox;
    expect(nodeType).toBeDefined();
    expect(nodeType?.spec.group).toBe('block');
    expect(nodeType?.spec.content).toBe('block+');
  });

  test('default attributes surah=1, ayah=1, translation=""', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.ayahBox;
    const defaults = nodeType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(defaults?.surah?.default).toBe(1);
    expect(defaults?.ayah?.default).toBe(1);
    expect(defaults?.translation?.default).toBe('');
  });

  test('parseHTML распознаёт div[data-type="ayah-box"] с числовыми attributes', () => {
    const html =
      '<div data-type="ayah-box" data-surah="2" data-ayah="255" data-translation="Аллах - нет божества кроме Него"><p>الله لا إله إلا هو</p></div>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('ayahBox');
    expect(root?.attrs?.surah).toBe(2);
    expect(root?.attrs?.ayah).toBe(255);
    expect(root?.attrs?.translation).toBe('Аллах - нет божества кроме Него');
  });

  test('setAyahBox command оборачивает selection с attributes', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'بسم الله' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setAyahBox({ surah: 1, ayah: 1 });
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('ayahBox');
    expect(root?.attrs?.surah).toBe(1);
    expect(root?.attrs?.ayah).toBe(1);
  });

  test('renderHTML создаёт div с data-type + class + numeric attributes', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'ayahBox',
          attrs: { surah: 36, ayah: 1, translation: '' },
          content: [{ type: 'paragraph', content: [{ type: 'text', text: 'يس' }] }],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="ayah-box"');
    expect(html).toContain('class="ayah-box"');
    expect(html).toContain('data-surah="36"');
    expect(html).toContain('data-ayah="1"');
    // translation="" - не должен попасть в HTML
    expect(html).not.toContain('data-translation');
  });

  test('surah с невалидным значением fallback на 1', () => {
    const html = '<div data-type="ayah-box" data-surah="999" data-ayah="1"><p>x</p></div>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(root?.attrs?.surah).toBe(1);
  });
});
