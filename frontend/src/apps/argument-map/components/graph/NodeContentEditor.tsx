import { useState } from 'react';
import { Pencil, MessageSquareQuote } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import InlineCitationBody from '@/apps/argument-map/components/citation/InlineCitationBody';
import { apiPatchRaw, formatApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type InlineCitationRef = components['schemas']['InlineCitationRef'];

interface Props {
  nodeId: string | undefined;
  content: string;
  initialEditing: boolean;
  onSaved: () => void;
  /** inline citation refs из node.inlineCitations - для рендера [N]-маркеров
   *  в view-режиме. В edit-режиме textarea показывает raw `[1]` (без рендера) */
  inlineCitations?: InlineCitationRef[];
  /** FB-2: гость/не-EDITOR видит контент read-only (без кнопки «Редактировать»). */
  canWrite?: boolean;
}

/**
 * Секция "Содержание" с двумя режимами:
 * - view: render content + кнопка "Редактировать"
 * - edit: textarea + Save/Cancel
 * Хранит свой draft/saving/saveError state - не загрязняет orchestrator.
 */
function NodeContentEditor({ nodeId, content, initialEditing, onSaved, inlineCitations, canWrite = true }: Props) {
  const t = useT();
  // Гость (canWrite=false) не входит в edit даже при initialEditing.
  const [editing, setEditing] = useState(canWrite ? initialEditing : false);
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
      setSaveError(formatApiError(e, t('common.error')));
    } finally {
      setSaving(false);
    }
  }

  return (
    <PanelSection icon={MessageSquareQuote} title={t('node.section.content')} defaultOpen>
      {!editing ? (
        <div>
          {content ? (
            <InlineCitationBody
              body={content}
              citations={inlineCitations}
              dir="auto"
              className="block break-words text-sm leading-relaxed text-ink-800 text-pretty"
            />
          ) : (
            <p className="text-sm italic text-ink-400">{t('node.empty_content_short')}</p>
          )}
          {canWrite && (
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
          )}
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
            className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
          />
          {saveError && (
            <div className="rounded-md border border-err-500/40 bg-err-100 p-2 text-xs text-err-700">
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
