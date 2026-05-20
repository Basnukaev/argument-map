import { useCallback, useEffect, useState } from 'react';
import { Users, Search, Loader2, AlertCircle } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, apiPatchRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { ALL_ROLES } from '@/shared/stores/authStore';
import type { AuthRole } from '@/shared/stores/authStore';
import type { AsyncState } from '@/shared/types/async';

// Inline types - backend GET /users + UserResponse не regenerated
// в types.ts ещё (см. docs/api-contract.md 2026-05-20 Phase A.7)
interface UserRow {
  id: string;
  username: string;
  email: string;
  role: AuthRole;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

interface UsersPage {
  items: UserRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

const PAGE_SIZE = 20;

/**
 * Vision 49d Phase A.7 — admin users management page. ADMIN-only.
 * Show table users с role-change UI (inline dropdown).
 */
function AdminUsersPage() {
  const t = useT();
  const [state, setState] = useState<AsyncState<UsersPage>>({ kind: 'loading' });
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<AuthRole | 'ALL'>('ALL');
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setState({ kind: 'loading' });
    try {
      const params = new URLSearchParams();
      params.set('page', '0');
      params.set('size', String(PAGE_SIZE));
      if (search.trim()) params.set('q', search.trim());
      if (roleFilter !== 'ALL') params.set('role', roleFilter);
      const data = await apiGetRaw<UsersPage>(`/api/v1/users?${params.toString()}`);
      setState({ kind: 'success', data });
    } catch (e: unknown) {
      setState({ kind: 'error', message: formatApiError(e, t('common.error')) });
    }
  }, [search, roleFilter, t]);

  useEffect(() => {
    void fetchUsers();
  }, [fetchUsers]);

  const handleRoleChange = useCallback(
    async (userId: string, newRole: AuthRole) => {
      setUpdatingId(userId);
      try {
        await apiPatchRaw(`/api/v1/users/${userId}/role`, { newRole });
        toast.success(t('admin.users.role_changed'));
        await fetchUsers();
      } catch (e) {
        toast.error(formatApiError(e, t('common.error')));
      } finally {
        setUpdatingId(null);
      }
    },
    [t, fetchUsers],
  );

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
            <Users size={12} aria-hidden /> {t('admin.users.title')}
          </div>
          <h1 className="mt-1 text-2xl font-semibold text-ink-900">
            {t('admin.users.title')}
          </h1>
          <p className="mt-1 text-sm text-ink-600">{t('admin.users.subtitle')}</p>
        </header>

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <div className="flex h-9 max-w-md flex-1 items-center rounded-md border border-border-strong bg-elevated focus-within:border-accent-500">
            <Search size={15} className="ms-3 text-ink-400" aria-hidden />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('admin.users.search_placeholder')}
              className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none"
            />
          </div>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value as AuthRole | 'ALL')}
            className="h-9 rounded-md border border-border-strong bg-elevated px-2 text-sm text-ink-900 outline-none focus:border-accent-500"
            aria-label={t('admin.users.role_filter')}
          >
            <option value="ALL">{t('admin.users.role_all')}</option>
            {ALL_ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="text-err-500" />
              <div className="text-sm text-ink-900">{state.message}</div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wider text-ink-500">
                <tr>
                  <th className="px-3 py-2 text-start">{t('admin.users.col.username')}</th>
                  <th className="px-3 py-2 text-start">{t('admin.users.col.email')}</th>
                  <th className="px-3 py-2 text-start">{t('admin.users.col.role')}</th>
                  <th className="px-3 py-2 text-start">{t('admin.users.col.enabled')}</th>
                  <th className="px-3 py-2 text-start">{t('admin.users.col.created')}</th>
                </tr>
              </thead>
              <tbody>
                {state.data.items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-3 py-12 text-center text-ink-500">
                      {t('admin.users.empty')}
                    </td>
                  </tr>
                ) : (
                  state.data.items.map((u) => (
                    <tr key={u.id} className="border-b border-border hover:bg-ink-100/40">
                      <td className="px-3 py-2 font-medium text-ink-900">{u.username}</td>
                      <td className="px-3 py-2 text-ink-600">{u.email}</td>
                      <td className="px-3 py-2">
                        <select
                          value={u.role}
                          onChange={(e) => handleRoleChange(u.id, e.target.value as AuthRole)}
                          disabled={updatingId === u.id}
                          className="h-7 rounded-sm border border-ink-200 bg-elevated px-2 text-xs text-ink-900 outline-none focus:border-accent-500 disabled:opacity-50"
                        >
                          {ALL_ROLES.map((r) => (
                            <option key={r} value={r}>
                              {r}
                            </option>
                          ))}
                        </select>
                        {updatingId === u.id && (
                          <Loader2 size={11} className="ms-2 inline animate-spin text-ink-400" aria-hidden />
                        )}
                      </td>
                      <td className="px-3 py-2 text-ink-600">{u.enabled ? '✓' : '✗'}</td>
                      <td className="px-3 py-2 text-xs text-ink-500">
                        {new Date(u.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            <div className="mt-3 text-xs text-ink-500">
              {t('admin.users.total').replace('{count}', String(state.data.totalElements))}
            </div>
          </div>
        )}
      </div>
    </main>
  );
}

export default AdminUsersPage;
