/**
 * RichTextEditor - shared Tiptap wrapper (Этап 17.0, ADR-039).
 *
 * Headless rich text editor поверх {@link useEditor} + EditorContent.
 * Не диктует UI: вызывающий компонент (AdminPageEditorPage и т.д.)
 * сам рисует toolbar и передаёт ref на editor instance через колбэк
 * {@code onEditorReady} - так toolbar может вызывать editor.chain()...
 *
 * **Default extensions:** StarterKit (Document, Paragraph, Text, Bold,
 * Italic, Heading, Lists, Blockquote, HardBreak, History для undo/redo).
 * Дополнительные custom extensions передаются через {@code extensions}
 * prop - первая custom это {@link HadithBox}, далее AyahBox / Marginalia
 * / Footnote / ColorHighlight / Tashkeel / DecoratedHeading / PageNumber
 * по мере implementation.
 *
 * **Read-only mode:** {@code editable={false}} даёт static render
 * без курсора и toolbar - используется в {@link RichTextRenderer}
 * для reader view.
 *
 * **RTL/арабский:** Tiptap наследует {@code dir} attribute с
 * container'а, мы выставляем его в Render-родителе - см.
 * RichTextRenderer.
 */
import { useEditor, EditorContent, type Editor, type Extension, type Node, type Mark } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect } from 'react';

type TiptapExtension = Extension | Node | Mark;

interface Props {
  /**
   * ProseMirror JSON. Null = пустой документ. Tiptap инициализируется
   * с этого значения и при изменении content (через props) НЕ
   * пересоздаётся - вместо этого вызывается editor.commands.setContent
   */
  content: object | null;
  /**
   * Callback при каждом edit. Передаётся ProseMirror JSON для сохранения
   * на бэк. Throttle/debounce - ответственность вызывающего компонента
   */
  onChange?: (json: object) => void;
  /**
   * false = read-only render (для reader view).
   * true = full editor с курсором и input handling (для admin editor)
   */
  editable: boolean;
  /**
   * Дополнительные Tiptap extensions поверх StarterKit. Например
   * {@link HadithBox} для admin editor. Reader тоже использует тот же
   * список - схема должна быть единой чтобы render работал
   */
  extensions?: TiptapExtension[];
  /**
   * Callback с {@link Editor} instance когда initialized. Используется
   * AdminPageEditorPage чтобы toolbar мог вызывать editor.chain().focus()
   * .setHadithBox(...) и т.д.
   */
  onEditorReady?: (editor: Editor) => void;
  /**
   * Tailwind classes для wrapper'а вокруг EditorContent. По умолчанию
   * базовый prose styling - вызывающий может переопределить
   */
  className?: string;
}

function RichTextEditor({
  content,
  onChange,
  editable,
  extensions = [],
  onEditorReady,
  className = 'prose prose-sm max-w-none focus:outline-none',
}: Props) {
  const editor = useEditor({
    extensions: [StarterKit, ...extensions],
    content: content ?? { type: 'doc', content: [{ type: 'paragraph' }] },
    editable,
    onUpdate: ({ editor: e }) => {
      if (onChange) onChange(e.getJSON());
    },
  });

  // Externally controlled content - если props.content меняется
  // (например после save → re-fetch), синхронизируем editor
  useEffect(() => {
    if (!editor) return;
    const current = editor.getJSON();
    const incoming = content ?? { type: 'doc', content: [{ type: 'paragraph' }] };
    // Сравнение через JSON.stringify - ProseMirror JSON неглубокий,
    // дешевле чем deep-equal библиотека ради единичного check
    if (JSON.stringify(current) !== JSON.stringify(incoming)) {
      editor.commands.setContent(incoming, { emitUpdate: false });
    }
  }, [editor, content]);

  // editable может меняться (например toggle preview mode) -
  // Tiptap не реагирует на props изменения автоматически
  useEffect(() => {
    if (!editor) return;
    editor.setEditable(editable);
  }, [editor, editable]);

  useEffect(() => {
    if (editor && onEditorReady) onEditorReady(editor);
  }, [editor, onEditorReady]);

  if (!editor) {
    return <div className={className} />;
  }
  return <EditorContent editor={editor} className={className} />;
}

export default RichTextEditor;
