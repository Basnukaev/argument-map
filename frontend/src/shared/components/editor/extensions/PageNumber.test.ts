/**
 * Schema tests для PageNumber inline-atom. Проверяем что node
 * inline/atom, number attribute с валидацией и insertContent command
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { PageNumber } from './PageNumber';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, PageNumber],
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

describe('PageNumber extension', () => {
  test('регистрирует node pageNumber с group=inline, inline=true, atom=true', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.pageNumber;
    expect(nodeType).toBeDefined();
    expect(nodeType?.spec.group).toBe('inline');
    expect(nodeType?.spec.inline).toBe(true);
    expect(nodeType?.spec.atom).toBe(true);
  });

  test('default number=1', () => {
    const editor = makeEditor();
    const nodeType = editor.schema.nodes.pageNumber;
    const defaults = nodeType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(defaults?.number?.default).toBe(1);
  });

  test('parseHTML распознаёт span[data-type="page-number"] с data-number', () => {
    const html = '<p>текст <span data-type="page-number" data-number="42"></span> ещё</p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ type?: string; attrs?: Record<string, unknown> }> }
      | undefined;
    const pageNumberNode = paragraph?.content?.find((c) => c.type === 'pageNumber');
    expect(pageNumberNode?.attrs?.number).toBe(42);
  });

  test('renderHTML создаёт span с data-type, class, data-number', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'pre ' },
            { type: 'pageNumber', attrs: { number: 7 } },
            { type: 'text', text: ' post' },
          ],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="page-number"');
    expect(html).toContain('class="page-number"');
    expect(html).toContain('data-number="7"');
  });

  test('невалидный number (0) fallback на 1', () => {
    const html = '<p><span data-type="page-number" data-number="0"></span></p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ type?: string; attrs?: Record<string, unknown> }> }
      | undefined;
    const node = paragraph?.content?.find((c) => c.type === 'pageNumber');
    expect(node?.attrs?.number).toBe(1);
  });

  test('setPageNumber вставляет atom в текущую позицию', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'hello' }] }],
    });
    editor.commands.focus('end');
    editor.commands.setPageNumber(15);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as
      | { content?: Array<{ type?: string; attrs?: Record<string, unknown> }> }
      | undefined;
    const pageNumberNode = paragraph?.content?.find((c) => c.type === 'pageNumber');
    expect(pageNumberNode).toBeDefined();
    expect(pageNumberNode?.attrs?.number).toBe(15);
  });
});
