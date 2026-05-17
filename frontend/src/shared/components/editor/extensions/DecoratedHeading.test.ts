/**
 * Schema tests для DecoratedHeading node. Проверяем level whitelist
 * (1-4), ornament whitelist (4 значения), parseHTML round-trip и
 * commands
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import {
  DecoratedHeading,
  HEADING_LEVELS,
  HEADING_ORNAMENTS,
} from './DecoratedHeading';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, DecoratedHeading],
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

describe('DecoratedHeading extension', () => {
  test('регистрирует node decoratedHeading с group=block, content=inline*', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.decoratedHeading;
    expect(nodeType).toBeDefined();
    expect(nodeType?.spec.group).toBe('block');
    expect(nodeType?.spec.content).toBe('inline*');
  });

  test('default level=2 и ornament="diamond"', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.decoratedHeading;
    const defaults = nodeType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(defaults?.level?.default).toBe(2);
    expect(defaults?.ornament?.default).toBe('diamond');
  });

  test('whitelist 4 levels и 4 ornaments', () => {
    expect(HEADING_LEVELS).toEqual([1, 2, 3, 4]);
    expect(HEADING_ORNAMENTS).toEqual(['diamond', 'flower', 'star', 'crescent']);
  });

  test('parseHTML распознаёт h3[data-type="decorated-heading"] с ornament=flower', () => {
    const html = '<h3 data-type="decorated-heading" data-level="3" data-ornament="flower">Глава</h3>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as
      | { type?: string; attrs?: Record<string, unknown> }
      | undefined;
    expect(root?.type).toBe('decoratedHeading');
    expect(root?.attrs?.level).toBe(3);
    expect(root?.attrs?.ornament).toBe('flower');
  });

  test('renderHTML создаёт h<level> с data-type, class, data-ornament', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'decoratedHeading',
          attrs: { level: 2, ornament: 'star' },
          content: [{ type: 'text', text: 'Подзаголовок' }],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('<h2');
    expect(html).toContain('data-type="decorated-heading"');
    expect(html).toContain('class="decorated-heading"');
    expect(html).toContain('data-ornament="star"');
  });

  test('невалидный ornament fallback на "diamond"', () => {
    const html = '<h2 data-type="decorated-heading" data-ornament="banana">x</h2>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(root?.attrs?.ornament).toBe('diamond');
  });

  test('невалидный level (5) fallback на 2', () => {
    const html = '<h2 data-type="decorated-heading" data-level="5">x</h2>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const root = json.content?.[0] as { attrs?: Record<string, unknown> } | undefined;
    expect(root?.attrs?.level).toBe(2);
  });

  test('setDecoratedHeading command конвертит paragraph в decoratedHeading', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'заголовок' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setDecoratedHeading({ level: 1, ornament: 'crescent' });
    const json = editor.getJSON();
    const root = json.content?.[0] as
      | { type?: string; attrs?: Record<string, unknown> }
      | undefined;
    expect(root?.type).toBe('decoratedHeading');
    expect(root?.attrs?.level).toBe(1);
    expect(root?.attrs?.ornament).toBe('crescent');
  });
});
