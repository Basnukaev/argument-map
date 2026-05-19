/**
 * RichTextRenderer - read-only render ProseMirror JSON для reader view
 * (Этап 17.0, ADR-039).
 *
 * Wrapper над {@link RichTextEditor} с editable=false. Используется в
 * BookReaderPage для рендера {@code page.formattedContent} с custom
 * Tiptap nodes (HadithBox / AyahBox / Marginalia и т.д.).
 *
 * **Fallback:** если formatted JSON отсутствует (legacy Shamela/PDFBox
 * импорт), вызывающий передаёт null - вспомогательная функция
 * {@link wrapPlainTextAsDoc} оборачивает plain text в minimal
 * paragraph-document. Тогда render полностью симметричен с старым
 * "<p>{text_content}</p>" путём, но через единый Tiptap pipeline
 * (можно прикрутить marks / highlights позже).
 */
import { useMemo } from 'react';
import RichTextEditor from '@/shared/components/editor/RichTextEditor';
import type { Extension, Node, Mark } from '@tiptap/react';
import { stripTashkeelFromDoc } from '@/shared/components/editor/utils/stripTashkeel';

type TiptapExtension = Extension | Node | Mark;

interface Props {
  /**
   * ProseMirror JSON. null = пустой документ (для legacy fallback
   * вызывающий передаёт wrapped plain text - см.
   * {@link wrapPlainTextAsDoc})
   */
  content: object | null;
  /**
   * Custom extensions для рендера (HadithBox, AyahBox и т.д.). Должны
   * совпадать с теми что используются в admin editor чтобы render
   * не падал на unknown node types
   */
  extensions?: TiptapExtension[];
  /** Tailwind classes для wrapper'а */
  className?: string;
  /** RTL direction для арабского контента */
  dir?: 'rtl' | 'ltr' | 'auto';
  /**
   * Если true - реально удаляет арабские диакритические знаки
   * (`U+064B`-`U+065F`, `U+0670`) из text-nodes ProseMirror JSON
   * **перед** рендером. Используется в reader при toggle «Без
   * огласовок». В admin editor НЕ применяется (автор должен видеть
   * оригинал). Реализация - {@link stripTashkeelFromDoc} (functional
   * transform JSON без DOM-walk)
   */
  hideTashkeel?: boolean;
}

/**
 * Утилита: обернуть plain text в минимальный ProseMirror document.
 * Используется как fallback для страниц с NULL formatted_content
 * (legacy Shamela/PDFBox импорт - см. ADR-039 «Backward compat»)
 *
 * pure utility co-located с компонентом-потребителем. HMR warning только
 * dev experience, splitting не оправдан
 */
// eslint-disable-next-line react-refresh/only-export-components
export function wrapPlainTextAsDoc(text: string | null | undefined): object {
  const safeText = text ?? '';
  if (!safeText) {
    return { type: 'doc', content: [{ type: 'paragraph' }] };
  }
  // Разделяем по двойным переносам строки - параграф = два \n,
  // одиночный \n = soft break внутри параграфа (как Markdown)
  const paragraphs = safeText.split(/\n\s*\n/).filter((p) => p.trim().length > 0);
  if (paragraphs.length === 0) {
    return { type: 'doc', content: [{ type: 'paragraph' }] };
  }
  return {
    type: 'doc',
    content: paragraphs.map((p) => ({
      type: 'paragraph',
      content: [{ type: 'text', text: p.trim() }],
    })),
  };
}

function RichTextRenderer({
  content,
  extensions = [],
  className,
  dir,
  hideTashkeel = false,
}: Props) {
  // useMemo чтобы не пересчитывать transform на каждом re-render parent'а
  // когда hideTashkeel/content не менялись. JSON.stringify-based check
  // в RichTextEditor.useEffect сравнит результат с current editor state -
  // если equal, setContent не вызывается (не мерцает)
  const processedContent = useMemo(
    () => stripTashkeelFromDoc(content, hideTashkeel),
    [content, hideTashkeel],
  );
  return (
    <div dir={dir}>
      <RichTextEditor
        content={processedContent}
        editable={false}
        extensions={extensions}
        className={className}
      />
    </div>
  );
}

export default RichTextRenderer;
