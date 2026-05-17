/**
 * AdminPageEditorPage - Tiptap editor для admin'а (Этап 17.0, ADR-039).
 *
 * Маршрут /admin/library/pages/:pageId/edit. Загружает страницу через
 * GET /api/v1/library/pages/{id}, рендерит {@link RichTextEditor} с
 * StarterKit + HadithBox extension. Toolbar над editor'ом даёт кнопки
 * Bold / Italic / H1-3 / Blockquote / HadithBox.
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
} from 'lucide-react';
import type { Editor } from '@tiptap/react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Modal from '@/shared/components/ui/Modal';
import Header from '@/shared/components/layout/Header';
import RichTextEditor from '@/shared/components/editor/RichTextEditor';
import { wrapPlainTextAsDoc } from '@/shared/components/editor/RichTextRenderer';
import { HadithBox, type HadithGrade } from '@/shared/components/editor/extensions/HadithBox';
import { apiGetRaw, apiPatchRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type PageResponse = components['schemas']['PageResponse'] & {
  // formattedContent ещё не в openapi types (требует regenerate-api после
  // backend deploy). Intersection даёт безопасный доступ
  formattedContent?: object | null;
};

type State =
  | { kind: 'loading' }
  | { kind: 'success'; page: PageResponse; content: object | null }
  | { kind: 'error'; message: string };

const EDITOR_EXTENSIONS = [HadithBox];

function AdminPageEditorPage() {
  const { pageId } = useParams<{ pageId: string }>();
  const navigate = useNavigate();
  const t = useT();

  const [state, setState] = useState<State>({ kind: 'loading' });
  const [editor, setEditor] = useState<Editor | null>(null);
  const [currentJson, setCurrentJson] = useState<object | null>(null);
  const [saving, setSaving] = useState(false);
  const [hadithModalOpen, setHadithModalOpen] = useState(false);
  const [hadithSource, setHadithSource] = useState('');
  const [hadithGrade, setHadithGrade] = useState<HadithGrade>('sahih');

  useEffect(() => {
    if (!pageId) return;
    const controller = new AbortController();
    apiGetRaw<PageResponse>(`/api/v1/library/pages/${pageId}`, {
      signal: controller.signal,
    })
      .then((page) => {
        // Если formatted_content есть - используем напрямую, иначе
        // оборачиваем text_content в minimal paragraph-doc (ADR-039
        // backward compat для Shamela/PDFBox страниц)
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

  // Toolbar handlers - вызываются если editor готов
  const isActive = (mark: string, attrs?: Record<string, unknown>): boolean => {
    return editor?.isActive(mark, attrs) ?? false;
  };

  const cmdBold = () => editor?.chain().focus().toggleBold().run();
  const cmdItalic = () => editor?.chain().focus().toggleItalic().run();
  const cmdH1 = () => editor?.chain().focus().toggleHeading({ level: 1 }).run();
  const cmdH2 = () => editor?.chain().focus().toggleHeading({ level: 2 }).run();
  const cmdH3 = () => editor?.chain().focus().toggleHeading({ level: 3 }).run();
  const cmdBlockquote = () => editor?.chain().focus().toggleBlockquote().run();

  const openHadithDialog = () => {
    // Pre-fill из текущего node если уже HadithBox - editor.getAttributes
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
      // Update attributes existing HadithBox (вместо wrap-in)
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
        </Card>

        {/* Editor area */}
        <Card className="p-5">
          <RichTextEditor
            content={state.content}
            onChange={setCurrentJson}
            editable
            extensions={EDITOR_EXTENSIONS}
            onEditorReady={setEditor}
            className="prose prose-sm max-w-none min-h-[400px] focus:outline-none"
          />
        </Card>
      </div>

      {/* HadithBox attributes dialog */}
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
