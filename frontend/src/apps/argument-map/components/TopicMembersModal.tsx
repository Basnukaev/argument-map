import { useCallback, useEffect, useState } from 'react';
import { Trash2, Loader2, Crown, UserPlus } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import IconButton from '@/shared/components/ui/IconButton';
import Select from '@/shared/components/ui/Select';
import {
  apiGetRaw,
  apiPostRaw,
  apiPatchRaw,
  apiDeleteRaw,
  ApiError,
  formatApiError,
} from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
import { useT, useFormatDate } from '@/shared/i18n';
import { toast } from '@/shared/stores/toastStore';
import { askConfirm } from '@/shared/stores/confirmStore';
import type { components } from '@/shared/api/types';

type TopicMemberResponse = components['schemas']['TopicMemberResponse'];

type MemberRole = 'MEMBER' | 'EDITOR';

interface Props {
  open: boolean;
  topicId: string;
  /** UUID создателя темы - чтобы показать его как owner в списке (badge "Владелец") */
  ownerUserId?: string | null;
  onClose: () => void;
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Модалка управления членами SHARED-темы (ADR-043, Этап 22.b).
 *
 * Open only when needed - conditional render `{open && <Modal/>}` идиома
 * проекта чтобы effect-driven state не накапливался между переоткрытиями
 * (см. frontend/CLAUDE.md "Conditional render для одноразовых модалок")
 */
function TopicMembersModal({ open, topicId, ownerUserId, onClose }: Props) {
  const t = useT();
  const formatDate = useFormatDate();

  const [members, setMembers] = useState<TopicMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // add-member form
  const [newUserId, setNewUserId] = useState('');
  const [newRole, setNewRole] = useState<MemberRole>('MEMBER');
  const [adding, setAdding] = useState(false);

  // pending action per member (UI lock на конкретной row)
  const [pendingMemberId, setPendingMemberId] = useState<string | null>(null);

  const refetch = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setLoadError(null);
      try {
        const list = await apiGetRaw<TopicMemberResponse[]>(
          `/api/v1/topics/${topicId}/members`,
          { signal },
        );
        setMembers(list ?? []);
      } catch (err: unknown) {
        // AbortError при unmount - не показывать как ошибку
        if (err instanceof DOMException && err.name === 'AbortError') return;
        const permMsg = formatPermissionError(err, t);
        setLoadError(permMsg ?? formatApiError(err, t('topic.members.load_failed')));
      } finally {
        setLoading(false);
      }
    },
    [topicId, t],
  );

  // refetch на mount + при смене topicId. Real AbortController отменяет
  // in-flight запрос при unmount (а не только discards response как
  // флаг cancelled). Async IIFE с await - setState вызывается после
  // suspension (await), поэтому react-hooks/set-state-in-effect не
  // триггерится (см. frontend/CLAUDE.md "Conditional render для
  // одноразовых модалок")
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      await refetch(controller.signal);
    })();
    return () => {
      controller.abort();
    };
  }, [refetch]);

  async function handleAdd() {
    const trimmed = newUserId.trim();
    if (!UUID_RE.test(trimmed)) {
      toast.error(t('topic.members.error_invalid_uuid'));
      return;
    }
    setAdding(true);
    try {
      await apiPostRaw<TopicMemberResponse>(
        `/api/v1/topics/${topicId}/members`,
        { userId: trimmed, role: newRole },
      );
      toast.success(t('topic.members.add_success'));
      setNewUserId('');
      setNewRole('MEMBER');
      await refetch();
    } catch (err: unknown) {
      // Бэк бросает IllegalArgumentException на duplicate - 400 без spec'ного type
      const isAlready =
        err instanceof ApiError &&
        (err.problem.detail ?? '').toLowerCase().includes('уже является');
      if (isAlready) {
        toast.error(t('topic.members.error_already_member'));
      } else {
        const permMsg =
          err instanceof ApiError ? formatPermissionError(err, t) : null;
        toast.error(permMsg ?? formatApiError(err, t('topic.members.add_failed')));
      }
    } finally {
      setAdding(false);
    }
  }

  async function handleRoleChange(memberId: string, role: MemberRole) {
    setPendingMemberId(memberId);
    try {
      await apiPatchRaw<TopicMemberResponse>(
        `/api/v1/topics/${topicId}/members/${memberId}`,
        { role },
      );
      toast.success(t('topic.members.role_change_success'));
      await refetch();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('topic.members.role_change_failed')),
      );
    } finally {
      setPendingMemberId(null);
    }
  }

  async function handleRemove(memberId: string) {
    if (!(await askConfirm({ message: t('topic.members.remove_confirm'), danger: true }))) return;
    setPendingMemberId(memberId);
    try {
      await apiDeleteRaw(`/api/v1/topics/${topicId}/members/${memberId}`);
      toast.success(t('topic.members.remove_success'));
      await refetch();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('topic.members.remove_failed')),
      );
    } finally {
      setPendingMemberId(null);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('topic.members.title')}
      subtitle={t('topic.members.subtitle')}
      maxWidth="max-w-2xl"
    >
      <div className="flex flex-col gap-5">
        {/* Список членов */}
        <section>
          {loading && (
            <div className="flex items-center justify-center gap-2 py-8 text-sm text-ink-500">
              <Loader2 size={14} className="animate-spin" aria-hidden />
              {t('topic.members.loading')}
            </div>
          )}
          {!loading && loadError && (
            <div className="rounded-sm border border-err-500/40 bg-err-100 p-3 text-sm text-err-700">
              {loadError}
            </div>
          )}
          {!loading && !loadError && members.length === 0 && !ownerUserId && (
            <p className="py-4 text-center text-sm text-ink-500">
              {t('topic.members.empty_state')}
            </p>
          )}
          {!loading && !loadError && (members.length > 0 || ownerUserId) && (
            <ul className="divide-y divide-border rounded-md border border-border">
              {ownerUserId && (
                <li className="flex items-center gap-3 p-3">
                  <Crown
                    size={16}
                    className="shrink-0 text-accent-600"
                    aria-hidden
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-mono text-xs text-ink-700">
                      <bdi dir="ltr">{ownerUserId}</bdi>
                    </p>
                    <p className="text-xs text-ink-500">
                      {t('topic.members.role_owner')}
                    </p>
                  </div>
                </li>
              )}
              {members.map((m) => {
                const memberId = m.id;
                const userId = m.userId;
                const role = (m.role ?? 'MEMBER') as MemberRole;
                const pending = pendingMemberId === memberId;
                if (!memberId || !userId) return null;
                return (
                  <li key={memberId} className="flex items-center gap-3 p-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-mono text-xs text-ink-700">
                        <bdi dir="ltr">{userId}</bdi>
                      </p>
                      <p className="text-xs text-ink-500">
                        {m.addedAt
                          ? `${t('topic.members.added_at')}: ${formatDate(m.addedAt, 'short')}`
                          : ''}
                      </p>
                    </div>
                    <Select
                      value={role}
                      onChange={(v) =>
                        void handleRoleChange(memberId, v as MemberRole)
                      }
                      options={[
                        {
                          value: 'MEMBER',
                          label: t('topic.members.role_member'),
                        },
                        {
                          value: 'EDITOR',
                          label: t('topic.members.role_editor'),
                        },
                      ]}
                      size="sm"
                      ariaLabel={t('topic.members.role_member')}
                      className="w-32"
                    />
                    <IconButton
                      icon={Trash2}
                      label={t('topic.members.remove_action')}
                      size="sm"
                      disabled={pending}
                      onClick={() => void handleRemove(memberId)}
                      className="!text-err-700 hover:!bg-err-100"
                    />
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {/* Добавить участника */}
        <section className="border-t border-border pt-4">
          <h3 className="mb-3 text-sm font-semibold text-ink-900">
            {t('topic.members.add_section_title')}
          </h3>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <Field label="UUID">
                <Field.Input
                  type="text"
                  value={newUserId}
                  onChange={(e) => setNewUserId(e.target.value)}
                  placeholder={t('topic.members.placeholder_user_id')}
                  disabled={adding}
                  spellCheck={false}
                  className="font-mono"
                />
              </Field>
            </div>
            <div className="sm:w-40">
              <Field label={t('topic.members.role_member')}>
                <Select
                  value={newRole}
                  onChange={(v) => setNewRole(v as MemberRole)}
                  options={[
                    {
                      value: 'MEMBER',
                      label: t('topic.members.role_member'),
                    },
                    {
                      value: 'EDITOR',
                      label: t('topic.members.role_editor'),
                    },
                  ]}
                  ariaLabel={t('topic.members.role_member')}
                  className="w-full"
                />
              </Field>
            </div>
            <Button
              icon={UserPlus}
              onClick={() => void handleAdd()}
              disabled={adding || newUserId.trim().length === 0}
            >
              {t('topic.members.add_button')}
            </Button>
          </div>
        </section>
      </div>
    </Modal>
  );
}

export default TopicMembersModal;
