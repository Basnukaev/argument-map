/**
 * AdminPageEditorPage - Tiptap editor для admin'а (Этап 17.0, ADR-039).
 *
 * Маршрут /admin/library/pages/:pageId/edit. Загружает страницу через
 * GET /api/v1/library/pages/{id}, рендерит {@link RichTextEditor} с
 * StarterKit + 5 custom extensions (HadithBox / AyahBox / Marginalia /
 * Footnote / ColorHighlight). Toolbar над editor'ом даёт кнопки
 * Bold / Italic / H1-3 / Blockquote / HadithBox / AyahBox / Marginalia /
 * Footnote / ColorHighlight palette.
 *
 * Save flow: PATCH /api/v1/library/pages/{id}/formatted-content с
 * ProseMirror JSON (editor.getJSON()). Backend хранит в jsonb колонке
 * (миграция 33, ADR-039). После save - toast + остаёмся на странице.
 *
 * Если у страницы formatted_content == null (legacy Shamela/PDFBox
 * импорт), wrapPlainTextAsDoc оборачивает text_content в paragraph-doc
 * - editor получает базовый текст для дальнейшей разметки.
 */
import { useEffect, useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  ArrowLeft,
  Bold,
  Italic,
  Heading1,
  Heading2,
  Heading3,
  Quote,
  Loader2,
  AlertCircle,
  Save,
  BookOpen,
  Book,
  StickyNote,
  Asterisk,
  Palette,
  Sparkles,
  Hash,
  Wand2,
} from 'lucide-react';
import type { Editor } from '@tiptap/react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Modal from '@/shared/components/ui/Modal';
import Header from '@/shared/components/layout/Header';
import RichTextEditor from '@/shared/components/editor/RichTextEditor';
import { wrapPlainTextAsDoc } from '@/shared/components/editor/RichTextRenderer';
import { HadithBox, type HadithGrade } from '@/shared/components/editor/extensions/HadithBox';
import { AyahBox } from '@/shared/components/editor/extensions/AyahBox';
import { Marginalia, type MarginaliaSide } from '@/shared/components/editor/extensions/Marginalia';
import { Footnote } from '@/shared/components/editor/extensions/Footnote';
import {
  ColorHighlight,
  HIGHLIGHT_COLORS,
  type HighlightColor,
} from '@/shared/components/editor/extensions/ColorHighlight';
import {
  DecoratedHeading,
  HEADING_LEVELS,
  HEADING_ORNAMENTS,
  type DecoratedHeadingLevel,
  type DecoratedHeadingOrnament,
} from '@/shared/components/editor/extensions/DecoratedHeading';
import { PageNumber } from '@/shared/components/editor/extensions/PageNumber';
import { BlockDir } from '@/shared/components/editor/extensions/BlockDir';
import { apiGetRaw, apiPatchRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { useAiEdit } from '@/shared/hooks/useAiEdit';
import type { components } from '@/shared/api/types';

// formattedContent в openapi маппится как JsonNode (Record<string, never>),
// но реально это произвольный ProseMirror JSON. Перекрываем чтобы тип
// был совместим с editor.getJSON() (`object`) и с useAiEdit callback
type PageResponse = Omit<components['schemas']['PageResponse'], 'formattedContent'> & {
  formattedContent?: object | null;
};

type State =
  | { kind: 'loading' }
  | { kind: 'success'; page: PageResponse; content: object | null }
  | { kind: 'error'; message: string };

const EDITOR_EXTENSIONS = [
  HadithBox,
  AyahBox,
  Marginalia,
  Footnote,
  ColorHighlight,
  DecoratedHeading,
  PageNumber,
  // bidi: dir="auto" на StarterKit-блоках (paragraph/heading/blockquote/
  // listItem) - арабский контент рендерится верно при любой локали UI
  BlockDir,
];

// Glyph preview для DecoratedHeading ornament selector в модалке
const ORNAMENT_GLYPHS: Record<DecoratedHeadingOrnament, string> = {
  diamond: '◆',
  flower: '❀',
  star: '❖',
  crescent: '❉',
};

// Swatch цвета для highlight dropdown - синхронизированы с CSS
// в `tiptap.css` (color-highlight-{color})
const HIGHLIGHT_SWATCHES: Record<HighlightColor, string> = {
  red: '#b91c1c',
  blue: '#1d4ed8',
  green: '#15803d',
  yellow: '#a16207',
  purple: '#7e22ce',
};

function AdminPageEditorPage() {
  const { pageId } = useParams<{ pageId: string }>();
  const navigate = useNavigate();
  const t = useT();

  const [state, setState] = useState<State>({ kind: 'loading' });
  const [editor, setEditor] = useState<Editor | null>(null);
  const [currentJson, setCurrentJson] = useState<object | null>(null);
  const [saving, setSaving] = useState(false);

  // HadithBox modal
  const [hadithModalOpen, setHadithModalOpen] = useState(false);
  const [hadithSource, setHadithSource] = useState('');
  const [hadithGrade, setHadithGrade] = useState<HadithGrade>('sahih');

  // AyahBox modal
  const [ayahModalOpen, setAyahModalOpen] = useState(false);
  const [ayahSurah, setAyahSurah] = useState(1);
  const [ayahAyah, setAyahAyah] = useState(1);
  const [ayahTranslation, setAyahTranslation] = useState('');

  // Marginalia modal
  const [marginaliaModalOpen, setMarginaliaModalOpen] = useState(false);
  const [marginaliaSide, setMarginaliaSide] = useState<MarginaliaSide>('start');

  // Footnote modal
  const [footnoteModalOpen, setFootnoteModalOpen] = useState(false);
  const [footnoteContent, setFootnoteContent] = useState('');
  const [footnoteEmptySelection, setFootnoteEmptySelection] = useState(false);

  // ColorHighlight palette dropdown
  const [highlightPaletteOpen, setHighlightPaletteOpen] = useState(false);

  // DecoratedHeading modal
  const [decoratedHeadingModalOpen, setDecoratedHeadingModalOpen] = useState(false);
  const [decoratedHeadingLevel, setDecoratedHeadingLevel] = useState<DecoratedHeadingLevel>(2);
  const [decoratedHeadingOrnament, setDecoratedHeadingOrnament] =
    useState<DecoratedHeadingOrnament>('diamond');

  // PageNumber modal
  const [pageNumberModalOpen, setPageNumberModalOpen] = useState(false);
  const [pageNumberValue, setPageNumberValue] = useState(1);

  // AI edit hook (Этап 17.e.f, ADR-042). Callback применяет результат
  // в editor (setContent) и в локальный currentJson чтобы Save flow
  // подхватил уже обработанную версию
  const handleAiContentReady = useCallback(
    (formattedContent: object) => {
      if (editor) {
        editor.commands.setContent(formattedContent, { emitUpdate: false });
      }
      setCurrentJson(formattedContent);
      // Обновляем page.formattedContent в state - иначе isFallback hint
      // продолжит светиться после успешного AI edit
      setState((prev) =>
        prev.kind === 'success'
          ? {
              ...prev,
              page: { ...prev.page, formattedContent },
              content: formattedContent,
            }
          : prev,
      );
      toast.success(t('admin.page_editor.ai.success_toast'));
    },
    [editor, t],
  );
  const aiEdit = useAiEdit(handleAiContentReady);
  const aiBusy = aiEdit.status === 'pending' || aiEdit.status === 'processing';

  useEffect(() => {
    if (!pageId) return;
    const controller = new AbortController();
    apiGetRaw<PageResponse>(`/api/v1/library/pages/${pageId}`, {
      signal: controller.signal,
    })
      .then((page) => {
        const initialContent = page.formattedContent ?? wrapPlainTextAsDoc(page.textContent);
        setState({ kind: 'success', page, content: initialContent });
        setCurrentJson(initialContent);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? (e.problem.detail ?? e.problem.title)
            : e instanceof Error
              ? e.message
              : t('admin.page_editor.load_failed');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [pageId, t]);

  const handleSave = useCallback(async () => {
    if (!pageId || !currentJson) return;
    setSaving(true);
    try {
      await apiPatchRaw<PageResponse>(
        `/api/v1/library/pages/${pageId}/formatted-content`,
        { formattedContent: currentJson },
      );
      toast.success(t('admin.page_editor.save_success'));
    } catch (e) {
      const message =
        e instanceof ApiError
          ? (e.problem.detail ?? e.problem.title)
          : t('admin.page_editor.save_failed');
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }, [pageId, currentJson, t]);

  const isActive = (mark: string, attrs?: Record<string, unknown>): boolean => {
    return editor?.isActive(mark, attrs) ?? false;
  };

  const cmdBold = () => editor?.chain().focus().toggleBold().run();
  const cmdItalic = () => editor?.chain().focus().toggleItalic().run();
  const cmdH1 = () => editor?.chain().focus().toggleHeading({ level: 1 }).run();
  const cmdH2 = () => editor?.chain().focus().toggleHeading({ level: 2 }).run();
  const cmdH3 = () => editor?.chain().focus().toggleHeading({ level: 3 }).run();
  const cmdBlockquote = () => editor?.chain().focus().toggleBlockquote().run();

  // HadithBox handlers
  const openHadithDialog = () => {
    if (editor?.isActive('hadithBox')) {
      const attrs = editor.getAttributes('hadithBox') as { source?: string; grade?: HadithGrade };
      setHadithSource(attrs.source ?? '');
      setHadithGrade(attrs.grade ?? 'sahih');
    } else {
      setHadithSource('');
      setHadithGrade('sahih');
    }
    setHadithModalOpen(true);
  };

  const confirmHadith = () => {
    if (!editor) return;
    if (editor.isActive('hadithBox')) {
      editor.chain().focus().updateAttributes('hadithBox', {
        source: hadithSource,
        grade: hadithGrade,
      }).run();
    } else {
      editor.chain().focus().setHadithBox({
        source: hadithSource,
        grade: hadithGrade,
      }).run();
    }
    setHadithModalOpen(false);
  };

  const removeHadith = () => {
    editor?.chain().focus().unsetHadithBox().run();
  };

  // AyahBox handlers
  const openAyahDialog = () => {
    if (editor?.isActive('ayahBox')) {
      const attrs = editor.getAttributes('ayahBox') as {
        surah?: number;
        ayah?: number;
        translation?: string;
      };
      setAyahSurah(attrs.surah ?? 1);
      setAyahAyah(attrs.ayah ?? 1);
      setAyahTranslation(attrs.translation ?? '');
    } else {
      setAyahSurah(1);
      setAyahAyah(1);
      setAyahTranslation('');
    }
    setAyahModalOpen(true);
  };

  const confirmAyah = () => {
    if (!editor) return;
    const attrs = { surah: ayahSurah, ayah: ayahAyah, translation: ayahTranslation };
    if (editor.isActive('ayahBox')) {
      editor.chain().focus().updateAttributes('ayahBox', attrs).run();
    } else {
      editor.chain().focus().setAyahBox(attrs).run();
    }
    setAyahModalOpen(false);
  };

  const removeAyah = () => {
    editor?.chain().focus().unsetAyahBox().run();
  };

  // Marginalia handlers
  const openMarginaliaDialog = () => {
    if (editor?.isActive('marginalia')) {
      const attrs = editor.getAttributes('marginalia') as { side?: MarginaliaSide };
      setMarginaliaSide(attrs.side ?? 'start');
    } else {
      setMarginaliaSide('start');
    }
    setMarginaliaModalOpen(true);
  };

  const confirmMarginalia = () => {
    if (!editor) return;
    if (editor.isActive('marginalia')) {
      editor.chain().focus().updateAttributes('marginalia', { side: marginaliaSide }).run();
    } else {
      editor.chain().focus().setMarginalia({ side: marginaliaSide }).run();
    }
    setMarginaliaModalOpen(false);
  };

  const removeMarginalia = () => {
    editor?.chain().focus().unsetMarginalia().run();
  };

  // Footnote handlers
  const openFootnoteDialog = () => {
    if (!editor) return;
    // Footnote это mark - требует non-empty selection.
    // Если уже на footnote-mark - pre-fill, иначе требуем выделение
    if (editor.isActive('footnote')) {
      const attrs = editor.getAttributes('footnote') as { content?: string };
      setFootnoteContent(attrs.content ?? '');
      setFootnoteEmptySelection(false);
    } else {
      const { from, to } = editor.state.selection;
      if (from === to) {
        setFootnoteEmptySelection(true);
        setFootnoteContent('');
      } else {
        setFootnoteEmptySelection(false);
        setFootnoteContent('');
      }
    }
    setFootnoteModalOpen(true);
  };

  const confirmFootnote = () => {
    if (!editor) return;
    editor.chain().focus().setFootnote(footnoteContent).run();
    setFootnoteModalOpen(false);
  };

  const removeFootnote = () => {
    editor?.chain().focus().unsetFootnote().run();
  };

  // ColorHighlight handlers
  const applyHighlight = (color: HighlightColor) => {
    editor?.chain().focus().setColorHighlight(color).run();
    setHighlightPaletteOpen(false);
  };

  const removeHighlight = () => {
    editor?.chain().focus().unsetColorHighlight().run();
  };

  // DecoratedHeading handlers
  const openDecoratedHeadingDialog = () => {
    if (editor?.isActive('decoratedHeading')) {
      const attrs = editor.getAttributes('decoratedHeading') as {
        level?: DecoratedHeadingLevel;
        ornament?: DecoratedHeadingOrnament;
      };
      setDecoratedHeadingLevel(attrs.level ?? 2);
      setDecoratedHeadingOrnament(attrs.ornament ?? 'diamond');
    } else {
      setDecoratedHeadingLevel(2);
      setDecoratedHeadingOrnament('diamond');
    }
    setDecoratedHeadingModalOpen(true);
  };

  const confirmDecoratedHeading = () => {
    if (!editor) return;
    editor
      .chain()
      .focus()
      .setDecoratedHeading({
        level: decoratedHeadingLevel,
        ornament: decoratedHeadingOrnament,
      })
      .run();
    setDecoratedHeadingModalOpen(false);
  };

  const removeDecoratedHeading = () => {
    editor?.chain().focus().unsetDecoratedHeading().run();
  };

  // PageNumber handlers
  const openPageNumberDialog = () => {
    // pre-fill из текущего page.pageNumber если есть, иначе 1
    const fallback = state.kind === 'success' ? (state.page.pageNumber ?? 1) : 1;
    setPageNumberValue(fallback);
    setPageNumberModalOpen(true);
  };

  const confirmPageNumber = () => {
    if (!editor) return;
    editor.chain().focus().setPageNumber(pageNumberValue).run();
    setPageNumberModalOpen(false);
  };

  // AI edit handler (Этап 17.e.f). Pre-flight checks: text_content
  // должен быть (OCR пройден) + не дублируем если polling уже идёт.
  const handleAiEditClick = async () => {
    if (state.kind !== 'success' || !pageId) return;
    const hasText = state.page.textContent != null && state.page.textContent.trim().length > 0;
    if (!hasText) {
      toast.warning(t('admin.page_editor.ai.no_text_warning'));
      return;
    }
    if (aiBusy) {
      toast.info(t('admin.page_editor.ai.already_processing_info'));
      return;
    }
    try {
      // toast.info до start - чтобы пользователь сразу видел сигнал.
      // start сам поставит status в pending/processing, что render'ит overlay
      toast.info(t('admin.page_editor.ai.started_toast'));
      await aiEdit.start(pageId);
      // status === 'failed' значит polling завершился неуспехом
      // (timeout либо 404 на странице) - покажем error toast
      if (aiEdit.status === 'failed') {
        toast.error(t('admin.page_editor.ai.failed_toast'));
      }
    } catch (e) {
      if (e instanceof ApiError && e.is('ai-edit-not-configured')) {
        toast.error(t('admin.page_editor.ai.not_configured_toast'));
        return;
      }
      // generic fallback
      const msg = e instanceof ApiError
        ? (e.problem.detail ?? e.problem.title)
        : t('admin.page_editor.ai.failed_toast');
      toast.error(msg);
    }
  };

  if (state.kind === 'loading') {
    return (
      <main className="min-h-screen bg-bg">
        <Header />
        <div className="flex items-center justify-center gap-2 py-20 text-sm text-ink-500">
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          {t('admin.page_editor.loading')}
        </div>
      </main>
    );
  }
  if (state.kind === 'error') {
    return (
      <main className="min-h-screen bg-bg">
        <Header />
        <div className="mx-auto max-w-3xl px-4 py-6">
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={20} className="mt-0.5 shrink-0 text-err-700" aria-hidden="true" />
              <div>
                <p className="font-semibold text-err-700">{t('admin.page_editor.load_failed')}</p>
                <p className="mt-1 text-sm text-err-700">{state.message}</p>
              </div>
            </div>
          </Card>
        </div>
      </main>
    );
  }

  const { page } = state;
  const isFallback = page.formattedContent == null;

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-4xl px-4 py-6">
        {/* Page header */}
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold text-ink-900">
              {t('admin.page_editor.title')}
            </h1>
            <p className="mt-1 text-sm text-ink-600">{t('admin.page_editor.subtitle')}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              icon={ArrowLeft}
              onClick={() => navigate(-1)}
            >
              {t('admin.page_editor.cancel')}
            </Button>
            <Button
              variant="primary"
              size="sm"
              icon={saving ? Loader2 : Save}
              onClick={handleSave}
              disabled={saving || !currentJson}
            >
              {saving ? t('admin.page_editor.saving') : t('admin.page_editor.save')}
            </Button>
          </div>
        </div>

        {isFallback && (
          <div className="mb-4 flex items-start gap-2 rounded-md border border-warn-500/40 bg-warn-100/50 px-3 py-2 text-xs text-warn-700">
            <BookOpen size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{t('admin.page_editor.fallback_hint')}</span>
          </div>
        )}

        {/* Toolbar */}
        <Card className="mb-3 flex flex-wrap items-center gap-1 p-2">
          <ToolbarButton
            active={isActive('bold')}
            onClick={cmdBold}
            icon={<Bold size={14} />}
            label={t('admin.page_editor.toolbar.bold')}
          />
          <ToolbarButton
            active={isActive('italic')}
            onClick={cmdItalic}
            icon={<Italic size={14} />}
            label={t('admin.page_editor.toolbar.italic')}
          />
          <ToolbarDivider />
          <ToolbarButton
            active={isActive('heading', { level: 1 })}
            onClick={cmdH1}
            icon={<Heading1 size={14} />}
            label={t('admin.page_editor.toolbar.heading1')}
          />
          <ToolbarButton
            active={isActive('heading', { level: 2 })}
            onClick={cmdH2}
            icon={<Heading2 size={14} />}
            label={t('admin.page_editor.toolbar.heading2')}
          />
          <ToolbarButton
            active={isActive('heading', { level: 3 })}
            onClick={cmdH3}
            icon={<Heading3 size={14} />}
            label={t('admin.page_editor.toolbar.heading3')}
          />
          <ToolbarDivider />
          <ToolbarButton
            active={isActive('blockquote')}
            onClick={cmdBlockquote}
            icon={<Quote size={14} />}
            label={t('admin.page_editor.toolbar.blockquote')}
          />
          <ToolbarDivider />
          {/* HadithBox */}
          <ToolbarButton
            active={isActive('hadithBox')}
            onClick={openHadithDialog}
            icon={<span className="text-xs font-bold">حديث</span>}
            label={t('admin.page_editor.toolbar.hadith')}
          />
          {isActive('hadithBox') && (
            <ToolbarButton
              active={false}
              onClick={removeHadith}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.hadith_remove')}
            />
          )}
          {/* AyahBox */}
          <ToolbarButton
            active={isActive('ayahBox')}
            onClick={openAyahDialog}
            icon={<Book size={14} />}
            label={t('admin.page_editor.toolbar.ayah')}
          />
          {isActive('ayahBox') && (
            <ToolbarButton
              active={false}
              onClick={removeAyah}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.ayah_remove')}
            />
          )}
          {/* Marginalia */}
          <ToolbarButton
            active={isActive('marginalia')}
            onClick={openMarginaliaDialog}
            icon={<StickyNote size={14} />}
            label={t('admin.page_editor.toolbar.marginalia')}
          />
          {isActive('marginalia') && (
            <ToolbarButton
              active={false}
              onClick={removeMarginalia}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.marginalia_remove')}
            />
          )}
          {/* Footnote */}
          <ToolbarButton
            active={isActive('footnote')}
            onClick={openFootnoteDialog}
            icon={<Asterisk size={14} />}
            label={t('admin.page_editor.toolbar.footnote')}
          />
          {isActive('footnote') && (
            <ToolbarButton
              active={false}
              onClick={removeFootnote}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.footnote_remove')}
            />
          )}
          {/* ColorHighlight palette */}
          <div className="relative">
            <ToolbarButton
              active={isActive('colorHighlight')}
              onClick={() => setHighlightPaletteOpen((v) => !v)}
              icon={<Palette size={14} />}
              label={t('admin.page_editor.toolbar.highlight')}
            />
            {highlightPaletteOpen && (
              <div
                className="absolute top-full mt-1 z-20 flex gap-1 rounded-md border border-border bg-bg p-1.5 shadow-md"
                role="menu"
                aria-label={t('admin.page_editor.highlight.label')}
              >
                {HIGHLIGHT_COLORS.map((color) => (
                  <button
                    key={color}
                    type="button"
                    onClick={() => applyHighlight(color)}
                    title={t(`admin.page_editor.highlight.${color}`)}
                    aria-label={t(`admin.page_editor.highlight.${color}`)}
                    className="h-6 w-6 rounded border border-border transition-transform hover:scale-110"
                    style={{ backgroundColor: HIGHLIGHT_SWATCHES[color] }}
                  />
                ))}
              </div>
            )}
          </div>
          {isActive('colorHighlight') && (
            <ToolbarButton
              active={false}
              onClick={removeHighlight}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.highlight_remove')}
            />
          )}
          <ToolbarDivider />
          {/* DecoratedHeading */}
          <ToolbarButton
            active={isActive('decoratedHeading')}
            onClick={openDecoratedHeadingDialog}
            icon={<Sparkles size={14} />}
            label={t('admin.page_editor.toolbar.decorated_heading')}
          />
          {isActive('decoratedHeading') && (
            <ToolbarButton
              active={false}
              onClick={removeDecoratedHeading}
              icon={<span className="text-xs">×</span>}
              label={t('admin.page_editor.toolbar.decorated_heading_remove')}
            />
          )}
          {/* PageNumber */}
          <ToolbarButton
            active={false}
            onClick={openPageNumberDialog}
            icon={<Hash size={14} />}
            label={t('admin.page_editor.toolbar.page_number')}
          />
          <ToolbarDivider />
          {/* AI editing pass (Этап 17.e.f, ADR-042). Indigo accent
              чтобы визуально отличался от обычных format-кнопок -
              это не markup-action, а вызов внешнего LLM */}
          <button
            type="button"
            onClick={handleAiEditClick}
            disabled={aiBusy}
            title={t('admin.page_editor.ai.button_tooltip')}
            aria-label={t('admin.page_editor.ai.button_label')}
            className={
              'inline-flex h-7 items-center gap-1 rounded px-2 text-xs transition-colors ' +
              (aiBusy
                ? 'cursor-not-allowed bg-indigo-100 text-indigo-400 dark:bg-indigo-900/30 dark:text-indigo-500'
                : 'bg-indigo-50 text-indigo-700 hover:bg-indigo-100 dark:bg-indigo-900/40 dark:text-indigo-300 dark:hover:bg-indigo-900/60')
            }
          >
            {aiBusy ? (
              <Loader2 size={14} className="animate-spin" aria-hidden="true" />
            ) : (
              <Wand2 size={14} aria-hidden="true" />
            )}
            <span>{t('admin.page_editor.ai.button_label')}</span>
          </button>
        </Card>

        {/* Editor area */}
        <Card className="relative p-5">
          <RichTextEditor
            content={state.content}
            onChange={setCurrentJson}
            editable={!aiBusy}
            extensions={EDITOR_EXTENSIONS}
            onEditorReady={setEditor}
            className="prose prose-sm max-w-none min-h-[400px] focus:outline-none"
          />
          {aiBusy && (
            <div
              className="absolute inset-0 flex flex-col items-center justify-center gap-3 rounded bg-bg/85 backdrop-blur-sm"
              role="status"
              aria-live="polite"
            >
              <div className="flex items-center gap-2 text-sm font-medium text-indigo-700 dark:text-indigo-300">
                <Loader2 size={18} className="animate-spin" aria-hidden="true" />
                <span>
                  {t('admin.page_editor.ai.processing_overlay').replace(
                    '{seconds}',
                    String(aiEdit.elapsedSeconds),
                  )}
                </span>
              </div>
              <button
                type="button"
                onClick={aiEdit.cancel}
                className="rounded-md border border-border bg-bg px-3 py-1 text-xs text-ink-700 hover:bg-ink-100"
              >
                {t('admin.page_editor.ai.cancel_polling')}
              </button>
            </div>
          )}
        </Card>
      </div>

      {/* HadithBox dialog */}
      {hadithModalOpen && (
        <Modal
          open
          onClose={() => setHadithModalOpen(false)}
          title={t('admin.page_editor.hadith.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-700">{t('admin.page_editor.hadith.source_label')}</span>
              <input
                type="text"
                value={hadithSource}
                onChange={(e) => setHadithSource(e.target.value)}
                placeholder={t('admin.page_editor.hadith.source_placeholder')}
                className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-700">{t('admin.page_editor.hadith.grade_label')}</span>
              <select
                value={hadithGrade}
                onChange={(e) => setHadithGrade(e.target.value as HadithGrade)}
                className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
              >
                <option value="sahih">{t('admin.page_editor.hadith.grade.sahih')}</option>
                <option value="hasan">{t('admin.page_editor.hadith.grade.hasan')}</option>
                <option value="daif">{t('admin.page_editor.hadith.grade.daif')}</option>
              </select>
            </label>
            <div className="mt-2 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setHadithModalOpen(false)}>
                {t('admin.page_editor.hadith.cancel')}
              </Button>
              <Button variant="primary" size="sm" onClick={confirmHadith}>
                {t('admin.page_editor.hadith.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* AyahBox dialog */}
      {ayahModalOpen && (
        <Modal
          open
          onClose={() => setAyahModalOpen(false)}
          title={t('admin.page_editor.ayah.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-3">
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-ink-700">{t('admin.page_editor.ayah.surah_label')}</span>
                <input
                  type="number"
                  min={1}
                  max={114}
                  value={ayahSurah}
                  onChange={(e) => setAyahSurah(Math.max(1, Math.min(114, Number(e.target.value) || 1)))}
                  className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
                />
              </label>
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-ink-700">{t('admin.page_editor.ayah.ayah_label')}</span>
                <input
                  type="number"
                  min={1}
                  value={ayahAyah}
                  onChange={(e) => setAyahAyah(Math.max(1, Number(e.target.value) || 1))}
                  className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
                />
              </label>
            </div>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-700">{t('admin.page_editor.ayah.translation_label')}</span>
              <textarea
                value={ayahTranslation}
                onChange={(e) => setAyahTranslation(e.target.value)}
                placeholder={t('admin.page_editor.ayah.translation_placeholder')}
                rows={3}
                className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
              />
            </label>
            <div className="mt-2 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setAyahModalOpen(false)}>
                {t('admin.page_editor.ayah.cancel')}
              </Button>
              <Button variant="primary" size="sm" onClick={confirmAyah}>
                {t('admin.page_editor.ayah.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Marginalia dialog */}
      {marginaliaModalOpen && (
        <Modal
          open
          onClose={() => setMarginaliaModalOpen(false)}
          title={t('admin.page_editor.marginalia.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            <fieldset className="flex flex-col gap-2 text-sm">
              <legend className="text-ink-700">{t('admin.page_editor.marginalia.side_label')}</legend>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="marginalia-side"
                  value="start"
                  checked={marginaliaSide === 'start'}
                  onChange={() => setMarginaliaSide('start')}
                />
                <span>{t('admin.page_editor.marginalia.side_start')}</span>
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="marginalia-side"
                  value="end"
                  checked={marginaliaSide === 'end'}
                  onChange={() => setMarginaliaSide('end')}
                />
                <span>{t('admin.page_editor.marginalia.side_end')}</span>
              </label>
            </fieldset>
            <div className="mt-2 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setMarginaliaModalOpen(false)}>
                {t('admin.page_editor.marginalia.cancel')}
              </Button>
              <Button variant="primary" size="sm" onClick={confirmMarginalia}>
                {t('admin.page_editor.marginalia.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* DecoratedHeading dialog */}
      {decoratedHeadingModalOpen && (
        <Modal
          open
          onClose={() => setDecoratedHeadingModalOpen(false)}
          title={t('admin.page_editor.decorated_heading.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            <fieldset className="flex flex-col gap-2 text-sm">
              <legend className="text-ink-700">
                {t('admin.page_editor.decorated_heading.level_label')}
              </legend>
              <div className="flex flex-wrap gap-2">
                {HEADING_LEVELS.map((lvl) => (
                  <label key={lvl} className="inline-flex items-center gap-1.5">
                    <input
                      type="radio"
                      name="decorated-heading-level"
                      value={lvl}
                      checked={decoratedHeadingLevel === lvl}
                      onChange={() => setDecoratedHeadingLevel(lvl)}
                    />
                    <span>H{lvl}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <fieldset className="flex flex-col gap-2 text-sm">
              <legend className="text-ink-700">
                {t('admin.page_editor.decorated_heading.ornament_label')}
              </legend>
              <div className="flex flex-wrap gap-3">
                {HEADING_ORNAMENTS.map((orn) => (
                  <label key={orn} className="inline-flex items-center gap-1.5">
                    <input
                      type="radio"
                      name="decorated-heading-ornament"
                      value={orn}
                      checked={decoratedHeadingOrnament === orn}
                      onChange={() => setDecoratedHeadingOrnament(orn)}
                    />
                    <span aria-hidden="true" className="text-base">
                      {ORNAMENT_GLYPHS[orn]}
                    </span>
                    <span>{t(`admin.page_editor.decorated_heading.ornament.${orn}`)}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="mt-2 flex justify-end gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setDecoratedHeadingModalOpen(false)}
              >
                {t('admin.page_editor.decorated_heading.cancel')}
              </Button>
              <Button variant="primary" size="sm" onClick={confirmDecoratedHeading}>
                {t('admin.page_editor.decorated_heading.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* PageNumber dialog */}
      {pageNumberModalOpen && (
        <Modal
          open
          onClose={() => setPageNumberModalOpen(false)}
          title={t('admin.page_editor.page_number.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-ink-700">
                {t('admin.page_editor.page_number.number_label')}
              </span>
              <input
                type="number"
                min={1}
                value={pageNumberValue}
                onChange={(e) =>
                  setPageNumberValue(Math.max(1, Number(e.target.value) || 1))
                }
                className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
              />
            </label>
            <p className="text-xs text-ink-500">
              {t('admin.page_editor.page_number.hint')}
            </p>
            <div className="mt-2 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setPageNumberModalOpen(false)}>
                {t('admin.page_editor.page_number.cancel')}
              </Button>
              <Button variant="primary" size="sm" onClick={confirmPageNumber}>
                {t('admin.page_editor.page_number.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Footnote dialog */}
      {footnoteModalOpen && (
        <Modal
          open
          onClose={() => setFootnoteModalOpen(false)}
          title={t('admin.page_editor.footnote.dialog_title')}
        >
          <div className="flex flex-col gap-3">
            {footnoteEmptySelection ? (
              <p className="text-sm text-warn-700">
                {t('admin.page_editor.footnote.empty_selection_hint')}
              </p>
            ) : (
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-ink-700">{t('admin.page_editor.footnote.content_label')}</span>
                <textarea
                  value={footnoteContent}
                  onChange={(e) => setFootnoteContent(e.target.value)}
                  placeholder={t('admin.page_editor.footnote.content_placeholder')}
                  rows={4}
                  className="rounded-md border border-border bg-bg px-3 py-1.5 text-sm focus:border-accent-500 focus:outline-none"
                />
              </label>
            )}
            <div className="mt-2 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={() => setFootnoteModalOpen(false)}>
                {t('admin.page_editor.footnote.cancel')}
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={confirmFootnote}
                disabled={footnoteEmptySelection || !footnoteContent}
              >
                {t('admin.page_editor.footnote.confirm')}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </main>
  );
}

interface ToolbarButtonProps {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}

function ToolbarButton({ active, onClick, icon, label }: ToolbarButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={
        'inline-flex h-7 items-center gap-1 rounded px-2 text-xs transition-colors ' +
        (active
          ? 'bg-accent-100 text-accent-700'
          : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900')
      }
    >
      {icon}
    </button>
  );
}

function ToolbarDivider() {
  return <span className="mx-1 h-5 w-px bg-border" />;
}

export default AdminPageEditorPage;
