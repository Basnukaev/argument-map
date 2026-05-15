import { useState } from 'react';
import { Pencil, MessageSquareQuote } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import { apiPatchRaw, formatApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  nodeId: string | undefined;
  content: string;
  initialEditing: boolean;
  onSaved: () => void;
}

/**
 * Секция "Содержание" с двумя режимами:
 * - view: render content + кнопка "Редактировать"
 * - edit: textarea + Save/Cancel
 * Хранит свой draft/saving/saveError state - не загрязняет orchestrator.
 */
function NodeContentEditor({ nodeId, content, initialEditing, onSaved }: Props) {
  const t = useT();
  const [editing, setEditing] = useState(initialEditing);
  const [draft, setDraft] = useState(content);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  function startEdit() {
    setDraft(content);
    setSaveError(null);
    setEditing(true);
  }

  function cancelEdit() {
    if (saving) return;
    setEditing(false);
    setSaveError(null);
  }

  async function save() {
    if (!nodeId) return;
    const trimmed = draft.trim();
    if (!trimmed || trimmed === content) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await apiPatchRaw<NodeDto>(`/api/v1/nodes/${nodeId}`, { content: trimmed });
      setEditing(false);
      onSaved();
    } catch (e: unknown) {
      setSaveError(formatApiError(e, 'Не удалось сохранить'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <PanelSection icon={MessageSquareQuote} title={t('node.section.content')} defaultOpen>
      {!editing ? (
        <div>
          {content ? (
            <p className="whitespace-pre-wrap break-words text-[14px] leading-relaxed text-slate-800 text-pretty">
              {content}
            </p>
          ) : (
            <p className="text-[14px] italic text-slate-400">(пусто)</p>
          )}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            icon={Pencil}
            onClick={startEdit}
            className="-ms-2 mt-3"
          >
            {t('common.edit')}
          </Button>
        </div>
      ) : (
        <div className="space-y-2">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={6}
            maxLength={10000}
            disabled={saving}
            aria-label={t('node.content_aria')}
            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          />
          {saveError && (
            <div className="rounded-md border border-red-300 bg-red-50 p-2 text-[12px] text-red-800">
              {saveError}
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={cancelEdit}
              disabled={saving}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={save}
              disabled={saving || !draft.trim()}
            >
              {saving ? t('common.saving') : t('common.save')}
            </Button>
          </div>
        </div>
      )}
    </PanelSection>
  );
}

export default NodeContentEditor;
