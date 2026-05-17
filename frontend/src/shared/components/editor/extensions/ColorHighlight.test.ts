/**
 * Schema tests для ColorHighlight mark. Проверяем whitelist, parseHTML
 * round-trip (через class имя), commands и toggle поведение
 */
import { afterEach, describe, expect, test } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { ColorHighlight, HIGHLIGHT_COLORS } from './ColorHighlight';

const activeEditors: Editor[] = [];

function makeEditor(initialContent?: object) {
  const editor = new Editor({
    extensions: [StarterKit, ColorHighlight],
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

describe('ColorHighlight mark', () => {
  test('регистрирует mark colorHighlight в schema', () => {
    const editor = makeEditor();
    expect(editor.schema.marks.colorHighlight).toBeDefined();
    expect(editor.schema.nodes.colorHighlight).toBeUndefined();
  });

  test('whitelist содержит 5 цветов', () => {
    expect(HIGHLIGHT_COLORS).toEqual(['red', 'blue', 'green', 'yellow', 'purple']);
  });

  test('parseHTML читает color из class="color-highlight-blue"', () => {
    const html =
      '<p>x<span data-type="color-highlight" class="color-highlight color-highlight-blue">текст</span>y</p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string; attrs?: Record<string, unknown> }> }> } | undefined;
    const colored = paragraph?.content?.find((c) => c.marks?.some((m) => m.type === 'colorHighlight'));
    const mark = colored?.marks?.find((m) => m.type === 'colorHighlight');
    expect(mark?.attrs?.color).toBe('blue');
  });

  test('renderHTML создаёт span с data-type + color class', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'highlighted',
              marks: [{ type: 'colorHighlight', attrs: { color: 'green' } }],
            },
          ],
        },
      ],
    });
    const html = editor.getHTML();
    expect(html).toContain('data-type="color-highlight"');
    expect(html).toContain('color-highlight-green');
  });

  test('setColorHighlight применяет mark с указанным цветом', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'word' }] }],
    });
    editor.commands.selectAll();
    editor.commands.setColorHighlight('purple');
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string; attrs?: Record<string, unknown> }> }> } | undefined;
    const mark = paragraph?.content?.[0]?.marks?.find((m) => m.type === 'colorHighlight');
    expect(mark?.attrs?.color).toBe('purple');
  });

  test('setColorHighlight с уже-активным цветом снимает mark (toggle)', () => {
    const editor = makeEditor({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'word',
              marks: [{ type: 'colorHighlight', attrs: { color: 'red' } }],
            },
          ],
        },
      ],
    });
    editor.commands.selectAll();
    editor.commands.setColorHighlight('red');
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string }> }> } | undefined;
    const marks = paragraph?.content?.[0]?.marks ?? [];
    expect(marks.find((m) => m.type === 'colorHighlight')).toBeUndefined();
  });

  test('невалидный color в parseHTML fallback на "red"', () => {
    const html = '<p><span data-type="color-highlight" class="color-highlight color-highlight-magenta">x</span></p>';
    const editor = makeEditor();
    editor.commands.setContent(html);
    const json = editor.getJSON();
    const paragraph = json.content?.[0] as { content?: Array<{ marks?: Array<{ type?: string; attrs?: Record<string, unknown> }> }> } | undefined;
    const mark = paragraph?.content?.[0]?.marks?.find((m) => m.type === 'colorHighlight');
    expect(mark?.attrs?.color).toBe('red');
  });
});
