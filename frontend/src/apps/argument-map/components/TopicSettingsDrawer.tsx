import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  X,
  Users,
  ScrollText,
  AlertTriangle,
  Plus,
  ExternalLink,
  Loader2,
  Save,
  Settings as SettingsIcon,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import IconButton from '@/shared/components/ui/IconButton';
import VisibilityRadioGroup, {
  type TopicVisibility,
} from '@/apps/argument-map/components/VisibilityRadioGroup';
import TopicMembersModal from '@/apps/argument-map/components/TopicMembersModal';
import {
  apiDeleteRaw,
  apiGetRaw,
  apiPatchRaw,
  ApiError,
  formatApiError,
} from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
import { hasArabicScript, useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
import { toast } from '@/shared/stores/toastStore';
import type { components } from '@/shared/api/types';

type TopicResponse = components['schemas']['TopicResponse'];
type TopicMemberResponse = components['schemas']['TopicMemberResponse'];

type StatusAlgorithm = 'MVP' | 'DUNG_GROUNDED';

interface Props {
  open: boolean;
  topic: TopicResponse;
  /** true для owner или ADMIN - показывает все mutating sections */
  canManage: boolean;
  /** true только для ADMIN - audit log section visible */
  isAdmin: boolean;
  onClose: () => void;
  /** вызывается после успешных мутаций (visibility, status algorithm) */
  onChanged: () => void;
}

/**
 * 480px end-side drawer с консолидированными настройками темы:
 * метаданные, visibility, members (для SHARED), status algorithm,
 * audit link для ADMIN, danger zone (delete с typing topic name).
 *
 * Desktop - slide-in drawer 480px шириной над dimmed canvas
 * (own backdrop + `<aside>` как в `SourceDetailPanel`, не нативный
 * `<dialog>` - нужен start/end-side slide а не центр).
 *
 * Mobile - fullscreen overlay через тот же `<aside>` без backdrop'а.
 *
 * Conditional render `{open && <Drawer/>}` - идиома проекта,
 * избегает effect-driven state reset между переоткрытиями
 * (см. frontend/CLAUDE.md).
 */
function TopicSettingsDrawer({
  open,
  topic,
  canManage,
  isAdmin,
  onClose,
  onChanged,
}: Props) {
  const t = useT();
  const isMobile = useIsMobile();
  const navigate = useNavigate();

  const topicId = topic.id ?? '';
  const topicTitle = topic.title ?? '';
  const topicDescription = topic.description ?? '';
  const currentVisibility = (topic.visibility ?? 'PRIVATE') as TopicVisibility;
  const currentAlgorithm = (topic.statusAlgorithm ?? 'MVP') as StatusAlgorithm;

  // visibility - local draft + save handler. Дёргаем сразу onChange, не
  // через explicit save - проще UX (radio сам сигнал намерения)
  const [savingVisibility, setSavingVisibility] = useState(false);
  const [savingAlgorithm, setSavingAlgorithm] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [membersModalOpen, setMembersModalOpen] = useState(false);

  // title/description form - локальный draft, кнопка «Сохранить» активна
  // при изменениях. Backend сторона: backlog tech debt #10 - PATCH
  // /api/v1/topics/{id} с UpdateTopicRequest (partial title+description)
  //
  // Sync с props при refetch onChanged - через key remount в parent
  // (TopicGraphPage передаёт key=`${id}|${title}|...`). Эффект-sync
  // запрещён react-hooks/set-state-in-effect, key-trick - идиома проекта
  const [titleDraft, setTitleDraft] = useState(topicTitle);
  const [descriptionDraft, setDescriptionDraft] = useState(topicDescription);
  const [savingMetadata, setSavingMetadata] = useState(false);
  const trimmedTitle = titleDraft.trim();
  // Дешёвые derived-значения - без useMemo (см. frontend/CLAUDE.md - не
  // превентивно). Validation сообщения локализуются на каждом рендере, t -
  // стабильная функция (см. memory feedback_stable_hooks_for_deps)
  let titleError: string | null = null;
  if (trimmedTitle.length === 0) {
    titleError = t('topic.settings.field.title_required');
  } else if (titleDraft.length > 200) {
    titleError = t('topic.settings.field.title_too_long');
  }
  const descriptionError =
    descriptionDraft.length > 2000
      ? t('topic.settings.field.description_too_long')
      : null;
  const hasChanges =
    titleDraft !== topicTitle || descriptionDraft !== topicDescription;
  const canSaveMetadata =
    hasChanges && !titleError && !descriptionError && !savingMetadata;

  // members preview (compact list) - top-3 members + counter. State =
  // 'idle' для no-fetch (non-SHARED visibility), либо fetched array.
  // 'pending' между fetch-start и resolve. Reset между переоткрытиями
  // drawer'а через conditional render `{open && <Drawer/>}` (см.
  // frontend/CLAUDE.md) - state не дублируется
  type PreviewState =
    | { kind: 'idle' }
    | { kind: 'pending' }
    | { kind: 'loaded'; members: TopicMemberResponse[] };
  const [previewState, setPreviewState] = useState<PreviewState>({
    kind: 'idle',
  });

  // refetch members preview - только при SHARED visibility. setState внутри
  // `.then/.catch` - async после suspension, eslint `set-state-in-effect`
  // не warning'ит. Initial 'pending' тоже async (Promise.resolve().then)
  // чтобы избежать setState-in-effect
  useEffect(() => {
    if (currentVisibility !== 'SHARED' || !topicId) return;
    const controller = new AbortController();
    void Promise.resolve().then(() => {
      if (controller.signal.aborted) return;
      setPreviewState({ kind: 'pending' });
    });
    apiGetRaw<TopicMemberResponse[]>(`/api/v1/topics/${topicId}/members`, {
      signal: controller.signal,
    })
      .then((list) => {
        if (controller.signal.aborted) return;
        setPreviewState({ kind: 'loaded', members: list ?? [] });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        // не блокируем drawer - просто пустой preview
        if (e instanceof ApiError) {
          setPreviewState({ kind: 'loaded', members: [] });
        }
      });
    return () => controller.abort();
  }, [currentVisibility, topicId, membersModalOpen]);

  const membersLoading = previewState.kind === 'pending';
  const membersPreview =
    previewState.kind === 'loaded' ? previewState.members : [];

  async function handleVisibilityChange(next: TopicVisibility) {
    if (!topicId || next === currentVisibility) return;
    setSavingVisibility(true);
    try {
      await apiPatchRaw(`/api/v1/topics/${topicId}/visibility`, {
        visibility: next,
      });
      toast.success(t('topic.visibility.change_success'));
      onChanged();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('topic.visibility.change_failed')),
      );
    } finally {
      setSavingVisibility(false);
    }
  }

  async function handleAlgorithmChange(next: StatusAlgorithm) {
    if (!topicId || next === currentAlgorithm) return;
    setSavingAlgorithm(true);
    try {
      await apiPatchRaw(`/api/v1/topics/${topicId}/status-algorithm`, {
        algorithm: next,
      });
      toast.success(t('topic.settings.status_algorithm.change_success'));
      onChanged();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ??
          formatApiError(err, t('topic.settings.status_algorithm.change_failed')),
      );
    } finally {
      setSavingAlgorithm(false);
    }
  }

  async function handleSaveMetadata() {
    if (!topicId || !canSaveMetadata) return;
    setSavingMetadata(true);
    try {
      // PATCH-семантика: шлём только реально изменившиеся поля -
      // backend пишет audit FieldDiff только по diff'у, нет смысла
      // отправлять unchanged поля и засорять changes-payload
      const body: { title?: string; description?: string } = {};
      if (titleDraft !== topicTitle) body.title = trimmedTitle;
      if (descriptionDraft !== topicDescription)
        body.description = descriptionDraft;
      await apiPatchRaw(`/api/v1/topics/${topicId}`, body);
      toast.success(t('topic.settings.edit.saved'));
      onChanged();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('topic.settings.edit.save_failed')),
      );
    } finally {
      setSavingMetadata(false);
    }
  }

  async function handleDelete() {
    if (!topicId) return;
    try {
      await apiDeleteRaw(`/api/v1/topics/${topicId}`);
      toast.success(t('topic.settings.danger.delete_success'));
      setDeleteConfirmOpen(false);
      onClose();
      navigate('/topics');
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ??
          formatApiError(err, t('topic.settings.danger.delete_error')),
      );
    }
  }

  // Escape - close drawer (только если delete confirm не открыт - тогда
  // сначала он перехватывает Escape сам)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !deleteConfirmOpen && !membersModalOpen) {
        onClose();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose, deleteConfirmOpen, membersModalOpen]);

  if (!open) return null;

  const widthClass = isMobile ? 'w-screen' : 'w-[480px] max-w-[90vw]';

  return (
    <>
      {/* Backdrop - dimmed canvas behind. На mobile тоже рендерим - покрывает
          layer ниже drawer'а (даже если drawer fullscreen, на iOS Safari c
          collapsed address-bar промежуточный gap прикрыт) */}
      <div
        className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]"
        onClick={onClose}
        data-testid="topic-settings-backdrop"
        aria-hidden="true"
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="topic-settings-title"
        data-testid="topic-settings-drawer"
        className={`fixed inset-y-0 end-0 z-50 flex ${widthClass} flex-col border-s border-border bg-bg shadow-sh4`}
      >
        <header className="flex flex-none items-center justify-between gap-3 border-b border-border px-5 py-3">
          <h2
            id="topic-settings-title"
            className="min-w-0 flex-1 truncate text-base font-semibold text-ink-900"
          >
            {t('topic.settings.title')}
          </h2>
          <IconButton
            icon={X}
            label={t('topic.settings.close')}
            size="sm"
            onClick={onClose}
          />
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-5">
          <div className="flex flex-col gap-6">
            {/* Section: Metadata (title + description) */}
            <section aria-labelledby="ts-metadata">
              <h3
                id="ts-metadata"
                className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
              >
                {t('topic.settings.section.metadata')}
              </h3>
              {canManage ? (
                <div className="space-y-3">
                  <Field
                    label={t('topic.settings.field.title')}
                    error={titleError ?? undefined}
                  >
                    <Field.Input
                      type="text"
                      value={titleDraft}
                      onChange={(e) => setTitleDraft(e.target.value)}
                      placeholder={t('topic.settings.field.title_placeholder')}
                      dir="auto"
                      maxLength={200}
                      className={
                        hasArabicScript(titleDraft) ? 'font-arabic' : ''
                      }
                      data-testid="topic-edit-title"
                    />
                  </Field>
                  <Field
                    label={t('topic.settings.field.description')}
                    error={descriptionError ?? undefined}
                  >
                    <Field.Textarea
                      rows={3}
                      value={descriptionDraft}
                      onChange={(e) => setDescriptionDraft(e.target.value)}
                      placeholder={t(
                        'topic.settings.field.description_placeholder',
                      )}
                      dir="auto"
                      maxLength={2000}
                      className={
                        hasArabicScript(descriptionDraft) ? 'font-arabic' : ''
                      }
                      data-testid="topic-edit-description"
                    />
                  </Field>
                  <div className="flex items-center justify-end">
                    <Button
                      type="button"
                      variant="primary"
                      size="sm"
                      icon={Save}
                      disabled={!canSaveMetadata}
                      onClick={() => void handleSaveMetadata()}
                      data-testid="topic-edit-save"
                    >
                      {savingMetadata
                        ? t('topic.settings.edit.saving')
                        : t('topic.settings.edit.save')}
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <Field label={t('topic.settings.field.title')}>
                    <div
                      dir="auto"
                      className={`rounded-sm border border-ink-200 bg-ink-50 px-3 py-2 text-sm text-ink-900 ${
                        hasArabicScript(topicTitle) ? 'font-arabic' : ''
                      }`}
                    >
                      <span className="break-words">{topicTitle || '—'}</span>
                    </div>
                  </Field>
                  {topicDescription && (
                    <Field label={t('topic.settings.field.description')}>
                      <p
                        dir="auto"
                        className={`rounded-sm border border-ink-200 bg-ink-50 px-3 py-2 text-sm text-ink-700 ${
                          hasArabicScript(topicDescription)
                            ? 'font-arabic'
                            : ''
                        }`}
                      >
                        {topicDescription}
                      </p>
                    </Field>
                  )}
                </div>
              )}
            </section>

            {/* Section: Visibility */}
            {canManage && (
              <section aria-labelledby="ts-visibility">
                <h3
                  id="ts-visibility"
                  className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
                >
                  {t('topic.settings.section.visibility')}
                </h3>
                <VisibilityRadioGroup
                  value={currentVisibility}
                  onChange={(v) => void handleVisibilityChange(v)}
                  disabled={savingVisibility}
                />
              </section>
            )}

            {/* Section: Members (only SHARED) */}
            {canManage && currentVisibility === 'SHARED' && (
              <section aria-labelledby="ts-members">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <h3
                    id="ts-members"
                    className="text-xs font-semibold uppercase tracking-wide text-ink-500"
                  >
                    {t('topic.settings.section.members')}
                  </h3>
                  <Button
                    type="button"
                    variant="ghost"
                    size="xs"
                    icon={Plus}
                    onClick={() => setMembersModalOpen(true)}
                  >
                    {t('topic.settings.members.add')}
                  </Button>
                </div>
                {membersLoading && (
                  <div className="flex items-center gap-2 py-2 text-xs text-ink-500">
                    <Loader2
                      size={12}
                      className="animate-spin"
                      aria-hidden
                    />
                    {t('topic.members.loading')}
                  </div>
                )}
                {!membersLoading && membersPreview.length === 0 && (
                  <p className="text-xs text-ink-500">
                    {t('topic.settings.members.empty')}
                  </p>
                )}
                {!membersLoading && membersPreview.length > 0 && (
                  <ul className="divide-y divide-border rounded-md border border-border">
                    {membersPreview.slice(0, 3).map((m) => {
                      const memberId = m.id;
                      const userId = m.userId;
                      if (!memberId || !userId) return null;
                      const role = m.role ?? 'MEMBER';
                      return (
                        <li
                          key={memberId}
                          className="flex items-center gap-3 px-3 py-2"
                        >
                          <Users
                            size={14}
                            className="shrink-0 text-ink-500"
                            aria-hidden
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate font-mono text-xs text-ink-700">
                              <bdi dir="ltr">{userId}</bdi>
                            </p>
                          </div>
                          <span className="rounded-sm border border-border bg-elevated px-1.5 py-0.5 text-xs font-medium text-ink-700">
                            {role === 'EDITOR'
                              ? t('topic.members.role_editor')
                              : t('topic.members.role_member')}
                          </span>
                        </li>
                      );
                    })}
                    {membersPreview.length > 3 && (
                      <li className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => setMembersModalOpen(true)}
                          className="text-xs font-medium text-accent-600 hover:text-accent-700 hover:underline"
                        >
                          {t('topic.settings.members.expand_full')} (
                          {membersPreview.length})
                        </button>
                      </li>
                    )}
                  </ul>
                )}
              </section>
            )}

            {/* Section: Status algorithm */}
            {canManage && (
              <section aria-labelledby="ts-algorithm">
                <h3
                  id="ts-algorithm"
                  className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
                >
                  {t('topic.settings.section.status_algorithm')}
                </h3>
                <div className="grid grid-cols-1 gap-2">
                  <AlgorithmOption
                    value="MVP"
                    selected={currentAlgorithm === 'MVP'}
                    disabled={savingAlgorithm}
                    label={t('topic.settings.status_algorithm.mvp')}
                    hint={t('topic.settings.status_algorithm.mvp_hint')}
                    onSelect={() => void handleAlgorithmChange('MVP')}
                  />
                  <AlgorithmOption
                    value="DUNG_GROUNDED"
                    selected={currentAlgorithm === 'DUNG_GROUNDED'}
                    disabled={savingAlgorithm}
                    label={t('topic.settings.status_algorithm.dung')}
                    hint={t('topic.settings.status_algorithm.dung_hint')}
                    onSelect={() => void handleAlgorithmChange('DUNG_GROUNDED')}
                  />
                </div>
              </section>
            )}

            {/* Section: Audit (ADMIN only) */}
            {isAdmin && (
              <section aria-labelledby="ts-audit">
                <h3
                  id="ts-audit"
                  className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
                >
                  {t('topic.settings.section.audit')}
                </h3>
                <a
                  href={`/admin/audit?entityType=TOPIC&entityId=${topicId}`}
                  className="inline-flex items-center gap-1.5 text-sm font-medium text-accent-600 hover:text-accent-700 hover:underline"
                >
                  <ScrollText size={14} aria-hidden />
                  {t('topic.settings.audit.view_log')}
                  <ExternalLink size={12} aria-hidden />
                </a>
              </section>
            )}

            {/* Section: Danger zone */}
            {canManage && (
              <section
                aria-labelledby="ts-danger"
                className="rounded-md border border-err-500/30 bg-err-100/30 p-4"
              >
                <h3
                  id="ts-danger"
                  className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-err-700"
                >
                  <AlertTriangle size={12} aria-hidden />
                  {t('topic.settings.section.danger')}
                </h3>
                <p className="mb-3 text-xs text-ink-700">
                  {t('topic.settings.danger.delete_confirm_hint')}
                </p>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => setDeleteConfirmOpen(true)}
                >
                  {t('topic.settings.danger.delete_button')}
                </Button>
              </section>
            )}
          </div>
        </div>
      </aside>

      {membersModalOpen && topicId && (
        <TopicMembersModal
          open={membersModalOpen}
          topicId={topicId}
          ownerUserId={topic.createdBy}
          onClose={() => setMembersModalOpen(false)}
        />
      )}

      {deleteConfirmOpen && (
        <DeleteConfirmModal
          topicTitle={topicTitle}
          onCancel={() => setDeleteConfirmOpen(false)}
          onConfirm={() => void handleDelete()}
        />
      )}
    </>
  );
}

interface AlgorithmOptionProps {
  value: StatusAlgorithm;
  selected: boolean;
  disabled: boolean;
  label: string;
  hint: string;
  onSelect: () => void;
}

function AlgorithmOption({
  value,
  selected,
  disabled,
  label,
  hint,
  onSelect,
}: AlgorithmOptionProps) {
  return (
    <label
      className={`flex cursor-pointer flex-col gap-1 rounded-md border p-3 transition-colors ${
        selected
          ? 'border-accent-500 bg-accent-50'
          : 'border-border bg-elevated hover:border-border-strong'
      } ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
    >
      <div className="flex items-center gap-2">
        <input
          type="radio"
          name="topic-status-algorithm"
          value={value}
          checked={selected}
          disabled={disabled}
          onChange={onSelect}
          className="sr-only"
        />
        <span
          className={`text-sm font-semibold ${
            selected ? 'text-accent-700' : 'text-ink-900'
          }`}
        >
          {label}
        </span>
      </div>
      <p className="text-xs leading-relaxed text-ink-500">{hint}</p>
    </label>
  );
}

interface DeleteConfirmModalProps {
  topicTitle: string;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Confirmation modal с typing topic name - паттерн GitHub/GitLab. Защита от
 * случайного удаления для destructive action. Кнопка disabled пока typed
 * exact match (case-sensitive, trim'аем оба для tolerant input).
 *
 * Используем own backdrop + absolute positioning (не `<dialog>` из Modal
 * чтобы поверх drawer'а лечь без z-index конфликтов).
 */
function DeleteConfirmModal({
  topicTitle,
  onCancel,
  onConfirm,
}: DeleteConfirmModalProps) {
  const t = useT();
  const [typed, setTyped] = useState('');

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  const expected = topicTitle.trim();
  const isMatch = expected.length > 0 && typed.trim() === expected;

  return (
    <>
      <div
        className="fixed inset-0 z-[60] bg-black/60"
        onClick={onCancel}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="ts-delete-title"
        data-testid="topic-delete-confirm"
        className="fixed inset-0 z-[70] flex items-center justify-center p-4"
      >
        <div className="w-full max-w-md rounded-lg border border-err-500/40 bg-elevated p-5 shadow-sh4">
          <div className="mb-3 flex items-start gap-2">
            <AlertTriangle
              size={18}
              className="mt-0.5 shrink-0 text-err-700"
              aria-hidden
            />
            <h3
              id="ts-delete-title"
              className="text-base font-semibold text-err-700"
            >
              {t('topic.settings.danger.delete_confirm_title')}
            </h3>
          </div>
          <p className="mb-4 text-sm text-ink-700">
            {t('topic.settings.danger.delete_confirm_hint')}
          </p>
          <Field
            label={t('topic.settings.danger.delete_confirm_type_name')}
            hint={topicTitle}
          >
            <Field.Input
              type="text"
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoFocus
              spellCheck={false}
            />
          </Field>
          <div className="mt-4 flex items-center justify-end gap-2">
            <Button variant="ghost" onClick={onCancel}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              onClick={onConfirm}
              disabled={!isMatch}
            >
              {t('topic.settings.danger.delete_confirm_action')}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}

/**
 * Re-export для удобства - SettingsIcon (gear) используется в TopicGraphPage
 * для toolbar button открывающей drawer
 */
export { SettingsIcon as TopicSettingsIcon };

export default TopicSettingsDrawer;
