import { useCallback, useMemo, useState } from 'react';
import { Loader2, Search } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import { usePagedSearch } from '@/shared/hooks/usePagedSearch';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import type { components } from '@/shared/api/types';

type HadithItem = components['schemas']['HadithResponse'];
type CollectionItem = components['schemas']['CollectionResponse'];

const PAGE_SIZE = 20;
type StatusFilter = 'CANONICAL' | 'VARIANT' | 'WEAK' | 'FABRICATED' | 'ALL';

/**
 * Цвет статус-бэйджа хадиса. Зеркалит statusClass из HadithListPage —
 * единый визуальный язык для статусов хадиса в списке и в пикере.
 */
function statusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    case 'WEAK':
      return 'bg-amber-100 text-amber-700';
    case 'FABRICATED':
      return 'bg-rose-100 text-rose-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

interface Props {
  open: boolean;
  onClose: () => void;
  /** Вызывается при выборе хадиса. Может быть async (POST в caller'е). */
  onSelect: (hadithId: string) => void | Promise<void>;
}

/**
 * Модалка выбора хадиса из корпуса hd_* для привязки к узлу как опора.
 * Поиск по тексту + фильтр статуса + чипы-сборники, карточки зеркалят
 * стилистику HadithListPage (naskh + RTL previewMatn). По клику на
 * карточку вызывается onSelect(hadithId) и модалка закрывается.
 *
 * Рендерится через `{open && <HadithPickerModal/>}` на call-site —
 * idiom проекта, гарантирует чистый internal state при каждом открытии.
 */
function HadithPickerModal({ open, onClose, onSelect }: Props) {
  const t = useT();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [collectionId, setCollectionId] = useState<string | null>(null);
  const [picking, setPicking] = useState(false);

  const collectionsState = useApiQuery<CollectionItem[]>('/api/v1/hadith/collections');
  const collections = useMemo(
    () => (collectionsState.kind === 'success' ? collectionsState.data : []),
    [collectionsState],
  );
  const collectionName = useMemo(() => {
    const map = new Map<string, string>();
    for (const c of collections) {
      if (c.id) map.set(c.id, c.nameRu || c.nameEn || c.nameAr || c.slug || c.id);
    }
    return map;
  }, [collections]);

  const buildUrl = useCallback(
    (page: number, q: string): string => {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(PAGE_SIZE));
      params.set('sort', 'number');
      if (q) params.set('q', q);
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      if (collectionId) params.set('collectionId', collectionId);
      return `/api/v1/hadith/hadiths?${params.toString()}`;
    },
    [statusFilter, collectionId],
  );

  const { state, searchInput, setSearchInput, loadMore, loadingMore } = usePagedSearch<HadithItem>({
    buildUrl,
    deps: [statusFilter, collectionId],
  });

  async function handlePick(hadithId: string) {
    if (picking) return;
    setPicking(true);
    try {
      await onSelect(hadithId);
      onClose();
    } finally {
      setPicking(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('hadith_picker.title')}
      subtitle={t('hadith_picker.subtitle')}
      maxWidth="max-w-2xl"
    >
      {/* поиск */}
      <div className="mb-3 flex h-9 items-center rounded-md border border-border-strong bg-elevated focus-within:border-accent-500">
        <Search size={15} className="ms-3 text-ink-400" aria-hidden />
        <input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder={t('hadith_picker.search_placeholder')}
          dir="auto"
          className="flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none"
        />
      </div>

      {/* чипы-сборники */}
      <div className="mb-3 flex flex-wrap gap-2">
        <ChipButton active={collectionId === null} onClick={() => setCollectionId(null)}>
          {t('hadith.collections.all')}
        </ChipButton>
        {collections
          .filter((c) => c.id && (c.hadithCount ?? 0) > 0)
          .map((c) => (
            <ChipButton
              key={c.id}
              active={collectionId === c.id}
              onClick={() => setCollectionId(c.id ?? null)}
            >
              {c.nameRu || c.nameEn || c.slug}
              <span className="ms-1.5 text-xs opacity-60">{c.hadithCount}</span>
            </ChipButton>
          ))}
      </div>

      {/* статус */}
      <div className="mb-4">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
          className="h-8 rounded-md border border-border-strong bg-elevated px-2 text-sm text-ink-900 outline-none focus:border-accent-500"
          aria-label={t('hadith.filter.all')}
        >
          <option value="ALL">{t('hadith.filter.all')}</option>
          <option value="CANONICAL">{t('hadith.filter.canonical')}</option>
          <option value="VARIANT">{t('hadith.filter.variant')}</option>
          <option value="WEAK">{t('hadith.filter.weak')}</option>
          <option value="FABRICATED">{t('hadith.filter.fabricated')}</option>
        </select>
      </div>

      {state.kind === 'loading' && (
        <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
          <Loader2 size={16} className="animate-spin" /> {t('common.loading')}
        </div>
      )}

      {state.kind === 'error' && (
        <Card className="border-err-500/40 bg-err-100 p-5">
          <div className="text-sm text-ink-900">{state.message}</div>
        </Card>
      )}

      {state.kind === 'success' && (
        <>
          {state.data.items.length === 0 ? (
            <div className="rounded-md border border-dashed border-border-strong bg-elevated/50 p-8 text-center text-sm text-ink-500">
              {t('hadith.empty')}
            </div>
          ) : (
            <ul className="space-y-3">
              {state.data.items.map((h) => (
                <li key={h.id}>
                  <button
                    type="button"
                    disabled={picking}
                    onClick={() => h.id && handlePick(h.id)}
                    className="block w-full text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 disabled:opacity-60"
                  >
                    <Card interactive className="p-4">
                      <div className="mb-2 flex items-center gap-2 text-xs text-ink-500">
                        {h.collectionId && collectionName.get(h.collectionId) && (
                          <span className="font-medium text-ink-600">
                            {collectionName.get(h.collectionId)}
                          </span>
                        )}
                        {h.primaryNumber != null && (
                          <span className="font-mono">№{h.primaryNumber}</span>
                        )}
                        <span
                          className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${statusClass(h.status)}`}
                        >
                          {h.status}
                        </span>
                      </div>
                      <p
                        className="font-arabic text-lg leading-loose text-ink-900 line-clamp-3"
                        dir="rtl"
                      >
                        {h.previewMatn || h.normalizedMatn || '—'}
                      </p>
                    </Card>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {state.data.hasNext && (
            <div className="mt-4 flex justify-center">
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="inline-flex items-center gap-2 rounded-md border border-border-strong bg-elevated px-4 py-2 text-sm font-medium text-ink-700 hover:bg-ink-100 disabled:opacity-50"
              >
                {loadingMore && <Loader2 size={14} className="animate-spin" />}
                {t('common.load_more')}
              </button>
            </div>
          )}
        </>
      )}
    </Modal>
  );
}

function ChipButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex items-center rounded-full border px-3 py-1 text-sm transition-colors ${
        active
          ? 'border-accent-500 bg-accent-100 font-medium text-accent-700'
          : 'border-border-strong bg-elevated text-ink-700 hover:bg-ink-100'
      }`}
    >
      {children}
    </button>
  );
}

export default HadithPickerModal;
