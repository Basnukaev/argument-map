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

type BookMemberResponse = components['schemas']['BookMemberResponse'];

type MemberRole = 'MEMBER' | 'EDITOR';

interface Props {
  open: boolean;
  bookId: string;
  /**
   * UUID создателя книги. Может быть null для shamela imports (book.createdBy
   * технически NOT NULL в БД, но если когда-нибудь будет null - просто не
   * показываем owner-строку). Если есть - рисуем `Crown` строкой первой
   * в списке (badge "Владелец")
   */
  ownerUserId?: string | null;
  onClose: () => void;
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Модалка управления членами SHARED-книги (ADR-043 Amendment, Этап 22.c.f).
 * Зеркало `TopicMembersModal` (22.b) с заменой endpoint'ов на
 * `/api/v1/library/books/{id}/members` и i18n ключей на `book.members.*`
 *
 * Open only when needed - conditional render `{open && <Modal/>}` идиома
 * проекта чтобы effect-driven state не накапливался между переоткрытиями
 * (см. frontend/CLAUDE.md "Conditional render для одноразовых модалок")
 */
function BookMembersModal({ open, bookId, ownerUserId, onClose }: Props) {
  const t = useT();
  const formatDate = useFormatDate();

  const [members, setMembers] = useState<BookMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // add-member form
  const [newUserId, setNewUserId] = useState('');
  const [newRole, setNewRole] = useState<MemberRole>('MEMBER');
  const [adding, setAdding] = useState(false);

  // pending action per member (UI lock на конкретной row)
  const [pendingMemberId, setPendingMemberId] = useState<string | null>(null);

  const refetch = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const list = await apiGetRaw<BookMemberResponse[]>(
        `/api/v1/library/books/${bookId}/members`,
      );
      setMembers(list ?? []);
    } catch (err: unknown) {
      const permMsg = formatPermissionError(err, t);
      setLoadError(permMsg ?? formatApiError(err, t('book.members.load_failed')));
    } finally {
      setLoading(false);
    }
  }, [bookId, t]);

  // refetch на mount + при смене bookId. setLoading внутри refetch -
  // первый render show loader, последующие через action handlers - тоже
  // через refetch. set-state-in-effect здесь намеренный paradigm для
  // initial data load (см. frontend/CLAUDE.md "Conditional render для
  // одноразовых модалок")
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (cancelled) return;
      await refetch();
    })();
    return () => {
      cancelled = true;
    };
  }, [refetch]);

  async function handleAdd() {
    const trimmed = newUserId.trim();
    if (!UUID_RE.test(trimmed)) {
      toast.error(t('book.members.error_invalid_uuid'));
      return;
    }
    setAdding(true);
    try {
      await apiPostRaw<BookMemberResponse>(
        `/api/v1/library/books/${bookId}/members`,
        { userId: trimmed, role: newRole },
      );
      toast.success(t('book.members.add_success'));
      setNewUserId('');
      setNewRole('MEMBER');
      await refetch();
    } catch (err: unknown) {
      // Бэк бросает IllegalArgumentException на duplicate - 400 без spec'ного type
      const isAlready =
        err instanceof ApiError &&
        (err.problem.detail ?? '').toLowerCase().includes('уже является');
      if (isAlready) {
        toast.error(t('book.members.error_already_member'));
      } else {
        const permMsg =
          err instanceof ApiError ? formatPermissionError(err, t) : null;
        toast.error(permMsg ?? formatApiError(err, t('book.members.add_failed')));
      }
    } finally {
      setAdding(false);
    }
  }

  async function handleRoleChange(memberId: string, role: MemberRole) {
    setPendingMemberId(memberId);
    try {
      await apiPatchRaw<BookMemberResponse>(
        `/api/v1/library/books/${bookId}/members/${memberId}`,
        { role },
      );
      toast.success(t('book.members.role_change_success'));
      await refetch();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('book.members.role_change_failed')),
      );
    } finally {
      setPendingMemberId(null);
    }
  }

  async function handleRemove(memberId: string) {
    if (!(await askConfirm({ message: t('book.members.remove_confirm'), danger: true }))) return;
    setPendingMemberId(memberId);
    try {
      await apiDeleteRaw(`/api/v1/library/books/${bookId}/members/${memberId}`);
      toast.success(t('book.members.remove_success'));
      await refetch();
    } catch (err: unknown) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        permMsg ?? formatApiError(err, t('book.members.remove_failed')),
      );
    } finally {
      setPendingMemberId(null);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('book.members.title')}
      subtitle={t('book.members.subtitle')}
      maxWidth="max-w-2xl"
    >
      <div className="flex flex-col gap-5">
        {/* Список членов */}
        <section>
          {loading && (
            <div className="flex items-center justify-center gap-2 py-8 text-sm text-ink-500">
              <Loader2 size={14} className="animate-spin" aria-hidden />
              {t('book.members.loading')}
            </div>
          )}
          {!loading && loadError && (
            <div className="rounded-sm border border-err-500/40 bg-err-100 p-3 text-sm text-err-700">
              {loadError}
            </div>
          )}
          {!loading && !loadError && members.length === 0 && !ownerUserId && (
            <p className="py-4 text-center text-sm text-ink-500">
              {t('book.members.empty_state')}
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
                      {t('book.members.role_owner')}
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
                          ? `${t('book.members.added_at')}: ${formatDate(m.addedAt, 'short')}`
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
                          label: t('book.members.role_member'),
                        },
                        {
                          value: 'EDITOR',
                          label: t('book.members.role_editor'),
                        },
                      ]}
                      size="sm"
                      ariaLabel={t('book.members.role_member')}
                      className="w-32"
                    />
                    <IconButton
                      icon={Trash2}
                      label={t('book.members.remove_action')}
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
            {t('book.members.add_section_title')}
          </h3>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <Field label="UUID">
                <Field.Input
                  type="text"
                  value={newUserId}
                  onChange={(e) => setNewUserId(e.target.value)}
                  placeholder={t('book.members.placeholder_user_id')}
                  disabled={adding}
                  spellCheck={false}
                  className="font-mono"
                />
              </Field>
            </div>
            <div className="sm:w-40">
              <Field label={t('book.members.role_member')}>
                <Select
                  value={newRole}
                  onChange={(v) => setNewRole(v as MemberRole)}
                  options={[
                    {
                      value: 'MEMBER',
                      label: t('book.members.role_member'),
                    },
                    {
                      value: 'EDITOR',
                      label: t('book.members.role_editor'),
                    },
                  ]}
                  ariaLabel={t('book.members.role_member')}
                  className="w-full"
                />
              </Field>
            </div>
            <Button
              icon={UserPlus}
              onClick={() => void handleAdd()}
              disabled={adding || newUserId.trim().length === 0}
            >
              {t('book.members.add_button')}
            </Button>
          </div>
        </section>
      </div>
    </Modal>
  );
}

export default BookMembersModal;
