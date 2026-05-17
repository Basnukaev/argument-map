/**
 * Schema tests для HadithBox extension. Через Tiptap test-helper
 * вместо полного DOM render - проверяем что extension правильно
 * собирает node-spec (name / group / content / attributes /
 * commands)
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { HadithBox } from './HadithBox';

// Tracking active editors для cleanup - иначе ProseMirror DOMObserver
// timer fires после teardown теста и валит uncaught exception на jsdom
const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, HadithBox],
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

describe('HadithBox extension', () => {
  test('регистрирует node hadithBox в schema с group=block и content=block+', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.hadithBox;
    expect(nodeType).toBeDefined();
    expect(nodeType?.spec.group).toBe('block');
    expect(nodeType?.spec.content).toBe('block+');
  });

  test('default attributes source="" и grade="sahih"', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.hadithBox;
    const defaults = nodeType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(defaults?.source?.default).toBe('');
    expect(defaults?.grade?.default).toBe('sahih');
  });

  test('parseHTML распознаёт div[data-type="hadith-box"] с attributes', () => {
    const html = '<div data-type="hadith-box" data-source="Бухари 1" data-grade="hasan"><p>текст</p></div>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('hadithBox');
    expect(root?.attrs?.source).toBe('Бухари 1');
    expect(root?.attrs?.grade).toBe('hasan');
  });

  test('renderHTML создаёт div с data-type + class="hadith-box"', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'hadithBox',
          attrs: { source: 'Муслим 1', grade: 'sahih' },
          content: [{ type: 'paragraph', content: [{ type: 'text', text: 'x' }] }],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="hadith-box"');
    expect(html).toContain('class="hadith-box"');
    expect(html).toContain('data-source="Муслим 1"');
    expect(html).toContain('data-grade="sahih"');
  });

  test('setHadithBox command оборачивает selection в hadithBox', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'hello' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setHadithBox({ source: 'test', grade: 'sahih' });
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('hadithBox');
    expect(root?.attrs?.source).toBe('test');
  });

  test('grade с невалидным значением fallback на "sahih"', () => {
    const html = '<div data-type="hadith-box" data-grade="invalid"><p>x</p></div>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(root?.attrs?.grade).toBe('sahih');
  });
});
