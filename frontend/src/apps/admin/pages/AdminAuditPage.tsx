import { useEffect, useState } from 'react';
import { AlertCircle, Eye, Loader2, RotateCcw, Filter } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import Header from '@/shared/components/layout/Header';
import Modal from '@/shared/components/ui/Modal';
import { apiGetRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT, useFormatDate, type DictKey } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type AuditLog = components['schemas']['AuditLogResponse'];
type PagedAudit = components['schemas']['PagedResponseAuditLogResponse'];

const PAGE_SIZE = 50;

/** Whitelist значений `entity_type` от бэка - используется и для select options,
 * и для разрешённых query-param values. Должен совпадать с `AuditEntityType`
 * enum в backend (ADR-043 Amendment 3) */
const ENTITY_TYPES = [
  'TOPIC',
  'NODE',
  'EDGE',
  'BOOK',
  'QUESTION',
  'ANSWER',
  'TOPIC_MEMBER',
  'BOOK_MEMBER',
  'NODE_SOURCE',
  'QUESTION_SOURCE',
  'ANSWER_SOURCE',
] as const;
type EntityType = (typeof ENTITY_TYPES)[number];

const ACTIONS = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'VISIBILITY_CHANGE',
  'MEMBER_ADD',
  'MEMBER_REMOVE',
  'MEMBER_ROLE_CHANGE',
] as const;
type Action = (typeof ACTIONS)[number];

/** Tailwind токены для action badge. Цвета матчат семантику в design system:
 * - CREATE → emerald (positive, новая сущность)
 * - UPDATE → blue (mutation существующего)
 * - DELETE → red/rose (destructive)
 * - VISIBILITY_CHANGE → purple (permission/governance)
 * - MEMBER_* → amber (membership management) */
const ACTION_BADGE_CLASS: Record<Action, string> = {
  CREATE: 'bg-emerald-100 text-emerald-700 border-emerald-300',
  UPDATE: 'bg-blue-100 text-blue-700 border-blue-300',
  DELETE: 'bg-rose-100 text-rose-700 border-rose-300',
  VISIBILITY_CHANGE: 'bg-purple-100 text-purple-700 border-purple-300',
  MEMBER_ADD: 'bg-amber-100 text-amber-700 border-amber-300',
  MEMBER_REMOVE: 'bg-amber-100 text-amber-700 border-amber-300',
  MEMBER_ROLE_CHANGE: 'bg-amber-100 text-amber-700 border-amber-300',
};

interface AuditAccum {
  items: AuditLog[];
  page: number;
  hasNext: boolean;
  totalElements: number;
}

interface Filters {
  entityType: '' | EntityType;
  action: '' | Action;
  actorId: string;
  dateFrom: string;
  dateTo: string;
}

const EMPTY_FILTERS: Filters = {
  entityType: '',
  action: '',
  actorId: '',
  dateFrom: '',
  dateTo: '',
};

function buildAuditUrl(filters: Filters, page: number): string {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(PAGE_SIZE));
  if (filters.entityType) params.set('entityType', filters.entityType);
  if (filters.actorId.trim()) params.set('actorId', filters.actorId.trim());
  // Backend ожидает ISO-8601 instants. <input type="date"> отдаёт `YYYY-MM-DD` -
  // конвертируем в полночь UTC. dateTo → конец дня (23:59:59.999Z) чтобы
  // фильтр включал весь выбранный день
  if (filters.dateFrom) params.set('dateFrom', `${filters.dateFrom}T00:00:00.000Z`);
  if (filters.dateTo) params.set('dateTo', `${filters.dateTo}T23:59:59.999Z`);
  return `/api/v1/audit/admin?${params.toString()}`;
}

function AdminAuditPage() {
  const t = useT();
  const formatDate = useFormatDate();

  // `applied` - фильтры по которым реально сделан текущий fetch.
  // `draft` - что юзер вводит в form. Apply копирует draft → applied и триггерит refetch
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS);
  const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
  const [state, setState] = useState<AsyncState<AuditAccum>>({ kind: 'loading' });
  const [loadingMore, setLoadingMore] = useState(false);
  const [detailsItem, setDetailsItem] = useState<AuditLog | null>(null);

  // Action filter применяется client-side: бэк не принимает фильтр по action,
  // поэтому фильтруем уже загруженный набор. Это compromise - server-side
  // фильтр потребовал бы расширения endpoint'а. Для текущих объёмов audit-log
  // (≤500 записей видны на странице) - acceptable
  const filteredItems = (() => {
    if (state.kind !== 'success') return [];
    if (!applied.action) return state.data.items;
    return state.data.items.filter((it) => it.action === applied.action);
  })();

  // Все setState в Promise-callbacks - lint react-hooks/set-state-in-effect
  // запрещает синхронный setState в теле эффекта. Initial 'loading' уже задан
  // через useState({ kind: 'loading' }). При apply фильтра state остаётся в
  // предыдущем success/error до завершения fetch'а - старые строки виды как
  // pending visual, swap происходит при .then()
  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<PagedAudit>(buildAuditUrl(applied, 0), { signal: controller.signal })
      .then((paged) => {
        if (controller.signal.aborted) return;
        setState({
          kind: 'success',
          data: {
            items: (paged.items ?? []) as AuditLog[],
            page: paged.page ?? 0,
            hasNext: paged.hasNext ?? false,
            totalElements: paged.totalElements ?? 0,
          },
        });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : t('admin.audit.load_failed');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [applied, t]);

  const handleLoadMore = async () => {
    if (state.kind !== 'success' || !state.data.hasNext || loadingMore) return;
    setLoadingMore(true);
    try {
      const nextPage = state.data.page + 1;
      const resp = await apiGetRaw<PagedAudit>(buildAuditUrl(applied, nextPage));
      const nextItems = (resp.items ?? []) as AuditLog[];
      setState({
        kind: 'success',
        data: {
          items: [...state.data.items, ...nextItems],
          page: resp.page ?? nextPage,
          hasNext: resp.hasNext ?? false,
          totalElements: resp.totalElements ?? state.data.totalElements,
        },
      });
    } catch (e: unknown) {
      toast.error(formatApiError(e, t('admin.audit.load_failed')));
    } finally {
      setLoadingMore(false);
    }
  };

  const handleApply = () => {
    setApplied(draft);
  };

  const handleReset = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
              {t('admin.audit.eyebrow')}
            </div>
            <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
              {t('admin.audit.title')}
            </h1>
            <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
              {t('admin.audit.description')}
              {state.kind === 'success' && (
                <>
                  {' '}·{' '}
                  <span className="font-medium text-ink-700">
                    <bdi dir="ltr">{state.data.totalElements}</bdi>
                  </span>
                </>
              )}
            </p>
          </div>
        </header>

        <FilterBar
          draft={draft}
          onDraftChange={setDraft}
          onApply={handleApply}
          onReset={handleReset}
        />

        {state.kind === 'loading' && (
          <div className="flex items-center justify-center gap-2 py-20 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('admin.audit.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <div className="mb-6 flex items-start gap-3 rounded-lg border border-err-500/40 bg-err-100 px-5 py-4 text-err-700">
            <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
            <div>
              <p className="font-semibold">{t('admin.audit.load_failed')}</p>
              <p className="mt-1 text-sm">{state.message}</p>
            </div>
          </div>
        )}

        {state.kind === 'success' && filteredItems.length === 0 && (
          <div className="rounded-lg border border-border bg-elevated px-6 py-12 text-center">
            <p className="text-sm text-ink-500">{t('admin.audit.empty_state')}</p>
          </div>
        )}

        {state.kind === 'success' && filteredItems.length > 0 && (
          <>
            <AuditTable
              items={filteredItems}
              onViewDetails={setDetailsItem}
              formatDate={formatDate}
            />

            {/* Load More только если бэк говорит hasNext И action-filter не активен.
                Когда action применён client-side - hasNext может вернуть `true`
                но новые page'ы все могут быть отфильтрованы. Подгружаем дальше
                чтобы пополнить filtered set */}
            {state.data.hasNext && (
              <div className="mt-6 flex justify-center">
                <Button
                  variant="ghost"
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  icon={loadingMore ? Loader2 : undefined}
                >
                  {loadingMore ? t('admin.audit.loading') : t('admin.audit.load_more')}
                </Button>
              </div>
            )}
          </>
        )}
      </div>

      {detailsItem && (
        <DetailsModal
          item={detailsItem}
          onClose={() => setDetailsItem(null)}
          formatDate={formatDate}
        />
      )}
    </main>
  );
}

// ====================================================================
//                          Sub-components
// ====================================================================

interface FilterBarProps {
  draft: Filters;
  onDraftChange: (f: Filters) => void;
  onApply: () => void;
  onReset: () => void;
}

function FilterBar({ draft, onDraftChange, onApply, onReset }: FilterBarProps) {
  const t = useT();
  // Native <select> для form-фильтра: project Select centered и для form
  // полей не подходит. Native = знакомая семантика для admin-tool юзера
  return (
    <section className="mb-6 rounded-lg border border-border bg-elevated p-4">
      <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-500">
        <Filter size={13} aria-hidden />
        {t('admin.audit.filter.entity_type')} / {t('admin.audit.filter.action')} /{' '}
        {t('admin.audit.filter.actor')}
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <Field label={t('admin.audit.filter.entity_type')}>
          <NativeSelect
            value={draft.entityType}
            onChange={(v) => onDraftChange({ ...draft, entityType: v as Filters['entityType'] })}
            options={[
              { value: '', label: t('admin.audit.filter.all') },
              ...ENTITY_TYPES.map((e) => ({ value: e, label: e })),
            ]}
          />
        </Field>
        <Field label={t('admin.audit.filter.action')}>
          <NativeSelect
            value={draft.action}
            onChange={(v) => onDraftChange({ ...draft, action: v as Filters['action'] })}
            options={[
              { value: '', label: t('admin.audit.filter.all') },
              ...ACTIONS.map((a) => ({
                value: a,
                label: t(`admin.audit.action.${a}` as DictKey),
              })),
            ]}
          />
        </Field>
        <Field label={t('admin.audit.filter.actor')}>
          <Field.Input
            type="text"
            value={draft.actorId}
            onChange={(e) => onDraftChange({ ...draft, actorId: e.target.value })}
            placeholder={t('admin.audit.filter.actor_placeholder')}
          />
        </Field>
        <Field label={t('admin.audit.filter.date_from')}>
          <Field.Input
            type="date"
            value={draft.dateFrom}
            onChange={(e) => onDraftChange({ ...draft, dateFrom: e.target.value })}
          />
        </Field>
        <Field label={t('admin.audit.filter.date_to')}>
          <Field.Input
            type="date"
            value={draft.dateTo}
            onChange={(e) => onDraftChange({ ...draft, dateTo: e.target.value })}
          />
        </Field>
      </div>
      <div className="mt-4 flex items-center justify-end gap-2">
        <Button variant="ghost" icon={RotateCcw} onClick={onReset}>
          {t('admin.audit.filter.reset')}
        </Button>
        <Button onClick={onApply}>{t('admin.audit.filter.apply')}</Button>
      </div>
    </section>
  );
}

interface NativeSelectProps {
  value: string;
  onChange: (v: string) => void;
  options: ReadonlyArray<{ value: string; label: string }>;
}

function NativeSelect({ value, onChange, options }: NativeSelectProps) {
  return (
    <div className="flex h-9 items-center rounded-sm border border-ink-200 bg-elevated px-3 focus-within:border-accent-500">
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full bg-transparent text-sm text-ink-900 outline-none"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

interface AuditTableProps {
  items: AuditLog[];
  onViewDetails: (item: AuditLog) => void;
  formatDate: (iso: string | undefined, style?: 'full' | 'short') => string;
}

function AuditTable({ items, onViewDetails, formatDate }: AuditTableProps) {
  const t = useT();
  // grid template - timestamp / entity / id (mono) / action / actor / parent / details button
  const gridCols = '160px 130px 90px 150px 1fr 1fr 100px';
  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-elevated">
      <div className="min-w-[920px]">
        <div
          className="sticky top-0 z-[1] grid items-center gap-3 border-b border-border bg-sunken px-4 py-2 text-[10px] font-semibold uppercase tracking-[0.08em] text-ink-500"
          style={{ gridTemplateColumns: gridCols }}
        >
          <span>{t('admin.audit.column.timestamp')}</span>
          <span>{t('admin.audit.column.entity')}</span>
          <span>{t('admin.audit.column.entity_id')}</span>
          <span>{t('admin.audit.column.action')}</span>
          <span>{t('admin.audit.column.actor')}</span>
          <span>{t('admin.audit.column.parent')}</span>
          <span className="text-end">{t('admin.audit.column.details')}</span>
        </div>
        <ul className="divide-y divide-border">
          {items
            .filter((it): it is AuditLog & { id: string } => Boolean(it.id))
            .map((it) => (
              <li key={it.id}>
                <AuditRow
                  item={it}
                  onViewDetails={onViewDetails}
                  formatDate={formatDate}
                  gridCols={gridCols}
                />
              </li>
            ))}
        </ul>
      </div>
    </div>
  );
}

interface AuditRowProps {
  item: AuditLog;
  onViewDetails: (item: AuditLog) => void;
  formatDate: (iso: string | undefined, style?: 'full' | 'short') => string;
  gridCols: string;
}

function AuditRow({ item, onViewDetails, formatDate, gridCols }: AuditRowProps) {
  const t = useT();
  const actionKey = item.action ?? '';
  const isKnownAction = (ACTIONS as readonly string[]).includes(actionKey);
  const badgeClass = isKnownAction
    ? ACTION_BADGE_CLASS[actionKey as Action]
    : 'bg-ink-100 text-ink-600 border-ink-300';

  const entityShort = item.entityId ? item.entityId.slice(0, 8) : '—';
  const parentShort =
    item.parentEntityId && item.parentEntityType
      ? `${item.parentEntityType} / ${item.parentEntityId.slice(0, 8)}`
      : '—';

  return (
    <div
      className="grid items-center gap-3 px-4 py-2.5 transition-colors hover:bg-sunken/60"
      style={{ gridTemplateColumns: gridCols }}
    >
      <div className="font-mono text-xs text-ink-600 tabular-nums">
        <bdi dir="ltr">{formatDate(item.createdAt, 'full')}</bdi>
      </div>
      <div className="text-xs font-medium text-ink-900">{item.entityType ?? '—'}</div>
      <div
        className="font-mono text-xs text-ink-500 tabular-nums"
        title={item.entityId ?? ''}
      >
        <bdi dir="ltr">{entityShort}</bdi>
      </div>
      <div>
        <span
          className={`inline-flex h-5 items-center rounded-sm border px-1.5 text-[10px] font-semibold uppercase tracking-wider ${badgeClass}`}
        >
          {actionKey}
        </span>
      </div>
      <div className="min-w-0 truncate text-xs text-ink-700" title={item.actorUserId ?? ''}>
        {item.actorUsername ?? t('admin.audit.actor_unknown')}
      </div>
      <div className="min-w-0 truncate font-mono text-xs text-ink-500" title={parentShort}>
        <bdi dir="ltr">{parentShort}</bdi>
      </div>
      <div className="flex justify-end">
        <Button
          variant="ghost"
          size="xs"
          icon={Eye}
          onClick={() => onViewDetails(item)}
          aria-label={t('admin.audit.column.view_details')}
        >
          {t('admin.audit.column.view_details')}
        </Button>
      </div>
    </div>
  );
}

interface DetailsModalProps {
  item: AuditLog;
  onClose: () => void;
  formatDate: (iso: string | undefined, style?: 'full' | 'short') => string;
}

function DetailsModal({ item, onClose, formatDate }: DetailsModalProps) {
  const t = useT();
  // Backend отдаёт `changes` как JSON-string (raw jsonb). Парсим для
  // pretty-print. Если парсинг ломается - показываем raw string + error hint
  const prettyChanges = (() => {
    if (!item.changes || item.changes.trim() === '' || item.changes === '{}') {
      return { kind: 'empty' as const };
    }
    try {
      const parsed: unknown = JSON.parse(item.changes);
      return { kind: 'ok' as const, text: JSON.stringify(parsed, null, 2) };
    } catch {
      return { kind: 'error' as const, raw: item.changes };
    }
  })();

  return (
    <Modal open onClose={onClose} title={t('admin.audit.details.title')} maxWidth="max-w-2xl">
      <div className="flex flex-col gap-4">
        <DetailsRow label={t('admin.audit.details.timestamp_label')} value={formatDate(item.createdAt, 'full')} />
        <DetailsRow label={t('admin.audit.details.action_label')} value={item.action ?? '—'} />
        <DetailsRow
          label={t('admin.audit.details.entity_label')}
          value={item.entityType ?? '—'}
        />
        <DetailsRow
          label={t('admin.audit.details.entity_id_label')}
          value={item.entityId ?? '—'}
          mono
        />
        {item.parentEntityId && (
          <DetailsRow
            label={t('admin.audit.details.parent_label')}
            value={`${item.parentEntityType ?? ''} / ${item.parentEntityId}`}
            mono
          />
        )}
        <DetailsRow
          label={t('admin.audit.details.actor_label')}
          value={`${item.actorUsername ?? '—'}${item.actorUserId ? ` (${item.actorUserId})` : ''}`}
        />

        <div>
          <div className="mb-1.5 text-xs font-semibold text-ink-500">
            {t('admin.audit.details.changes_label')}
          </div>
          {prettyChanges.kind === 'empty' && (
            <p className="text-xs text-ink-400">{t('admin.audit.details.no_changes')}</p>
          )}
          {prettyChanges.kind === 'ok' && (
            <pre className="overflow-x-auto rounded-md border border-border bg-sunken px-3 py-2 font-mono text-xs leading-relaxed text-ink-800">
              <bdi dir="ltr">{prettyChanges.text}</bdi>
            </pre>
          )}
          {prettyChanges.kind === 'error' && (
            <div>
              <p className="mb-1 text-xs text-err-500">
                {t('admin.audit.details.changes_parse_error')}
              </p>
              <pre className="overflow-x-auto rounded-md border border-border bg-sunken px-3 py-2 font-mono text-xs leading-relaxed text-ink-800">
                <bdi dir="ltr">{prettyChanges.raw}</bdi>
              </pre>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

interface DetailsRowProps {
  label: string;
  value: string;
  mono?: boolean;
}

function DetailsRow({ label, value, mono = false }: DetailsRowProps) {
  return (
    <div className="grid grid-cols-[140px_1fr] items-baseline gap-3">
      <div className="text-xs font-semibold text-ink-500">{label}</div>
      <div
        className={`min-w-0 break-all text-sm text-ink-900 ${mono ? 'font-mono tabular-nums' : ''}`}
      >
        <bdi dir="ltr">{value}</bdi>
      </div>
    </div>
  );
}

export default AdminAuditPage;
