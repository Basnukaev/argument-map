/**
 * Schema tests для Footnote mark. Mark (не Node) inline; проверяем
 * inline=true, content attribute, parseHTML round-trip и commands
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { Footnote } from './Footnote';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, Footnote],
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

describe('Footnote mark', () => {
  test('регистрирует mark footnote в schema', () => {
    const editor = makeEditor();
    const markType = editor.schema.marks.footnote;
    expect(markType).toBeDefined();
    // Mark по ProseMirror inline by definition, spec.inline отсутствует
    // - проверяем что это именно mark (а не node) через schema.marks
    expect(editor.schema.marks.footnote).toBeDefined();
    expect(editor.schema.nodes.footnote).toBeUndefined();
  });

  test('default content=""', () => {
    const editor = makeEditor();
    const markType = editor.schema.marks.footnote;
    const attrs = markType?.spec.attrs as Record<string, { default: unknown }> | undefined;
    expect(attrs?.content?.default).toBe('');
  });

  test('parseHTML распознаёт sup[data-type="footnote"] с content из title', () => {
    const html = '<p>текст<sup data-type="footnote" title="сноска мухаккика">x</sup>после</p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string; attrs?: Record<string, unknown> }> }> } | undefined;
    const footnoteText = paragraph?.content?.find((c) => c.marks?.some((m) => m.type === 'footnote'));
    const footnoteMark = footnoteText?.marks?.find((m) => m.type === 'footnote');
    expect(footnoteMark?.attrs?.content).toBe('сноска мухаккика');
  });

  test('renderHTML создаёт sup с data-type, class, title', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'ref',
              marks: [{ type: 'footnote', attrs: { content: 'tooltip text' } }],
            },
          ],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="footnote"');
    expect(html).toContain('class="footnote-ref"');
    expect(html).toContain('title="tooltip text"');
  });

  test('setFootnote command применяет mark с content', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'hello' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setFootnote('пояснение');
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string; attrs?: Record<string, unknown> }> }> } | undefined;
    const mark = paragraph?.content?.[0]?.marks?.find((m) => m.type === 'footnote');
    expect(mark?.attrs?.content).toBe('пояснение');
  });
});
