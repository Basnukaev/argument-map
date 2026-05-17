/**
 * Schema tests для Marginalia extension. Проверяем что node spec
 * правильный (block + inline content), side attribute c whitelist
 * start/end, parseHTML/renderHTML round-trip и commands работают
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { Marginalia } from './Marginalia';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, Marginalia],
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

describe('Marginalia extension', () => {
  test('регистрирует node marginalia с group=block, content=block+', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.marginalia;
    expect(nodeType).toBeDefined();
    expect(nodeType?.spec.group).toBe('block');
    expect(nodeType?.spec.content).toBe('block+');
  });

  test('default side="start"', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.marginalia;
    const defaults = nodeType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(defaults?.side?.default).toBe('start');
  });

  test('parseHTML распознаёт aside[data-type="marginalia"] с side="end"', () => {
    const html = '<aside data-type="marginalia" data-side="end"><p>комментарий</p></aside>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('marginalia');
    expect(root?.attrs?.side).toBe('end');
  });

  test('renderHTML создаёт aside с data-type + class + side', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'marginalia',
          attrs: { side: 'start' },
          content: [{ type: 'paragraph', content: [{ type: 'text', text: 'примечание мухаккика' }] }],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('<aside');
    expect(html).toContain('data-type="marginalia"');
    expect(html).toContain('class="marginalia"');
    expect(html).toContain('data-side="start"');
  });

  test('side с невалидным значением fallback на "start"', () => {
    const html = '<aside data-type="marginalia" data-side="middle"><p>x</p></aside>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(root?.attrs?.side).toBe('start');
  });

  test('setMarginalia command оборачивает selection', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'note text' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setMarginalia({ side: 'end' });
    const json = editor.getJSON();
    const root = json.content?.[0] as { type?: string; attrs?: Record<string, unknown> } | undefined;
    expect(root?.type).toBe('marginalia');
    expect(root?.attrs?.side).toBe('end');
  });
});
