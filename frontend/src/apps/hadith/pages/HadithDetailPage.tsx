import { useMemo } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2, Network } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import SanadGraph from '@/apps/hadith/components/SanadGraph';
import HadithGradesList from '@/apps/hadith/components/HadithGradesList';
import MatnVariations from '@/apps/hadith/components/MatnVariations';
import HadithSectionNav, {
  type SectionNavItem,
} from '@/apps/hadith/components/HadithSectionNav';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import { useT, type DictKey } from '@/shared/i18n';
import type { HadithGrade, MatnDto } from '@/apps/hadith/types';
import type { components } from '@/shared/api/types';

interface HadithDetail {
  id: string;
  collectionId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: string;
  sourceId: string | null;
  createdAt: string;
  matns: MatnDto[];
  grades: HadithGrade[];
}

type CollectionItem = components['schemas']['CollectionResponse'];

/** Цвет статус-бейджа — синхронизирован с карточками списка хадисов. */
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

/** Ключ пояснения статуса (есть для 4 известных статусов). */
const STATUS_EXPLAIN: Record<string, DictKey> = {
  CANONICAL: 'hadith.detail.status.CANONICAL',
  VARIANT: 'hadith.detail.status.VARIANT',
  WEAK: 'hadith.detail.status.WEAK',
  FABRICATED: 'hadith.detail.status.FABRICATED',
};

const SECTIONS: ReadonlyArray<SectionNavItem> = [
  { id: 'text', labelKey: 'hadith.detail.nav.text' },
  { id: 'sanad', labelKey: 'hadith.detail.nav.sanad' },
  { id: 'grades', labelKey: 'hadith.detail.nav.grades' },
  { id: 'variations', labelKey: 'hadith.detail.nav.variations' },
];

// Якоря резервируют отступ под две прилипшие полосы (Header h-12 + section
// nav ≈ h-12) + воздух, иначе заголовок секции уезжает под навигацию.
const SECTION_ANCHOR = 'scroll-mt-28';

/**
 * Hadith Explorer — страница одного хадиса, выстроенная в чёткие секции
 * (а не одну «кашу» со скроллом): текст-герой → полноэкранный граф иснада
 * → оценки учёных → вариации matn'а. Сверху — прилипающая якорная
 * навигация по секциям. Detail грузится через useApiQuery (SWR-кэш →
 * мгновенный возврат по back). Граф (SanadGraph) тянет свой endpoint сам.
 */
function HadithDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();

  const state = useApiQuery<HadithDetail>(
    id ? `/api/v1/hadith/hadiths/${id}/detail` : null,
  );
  const collectionsState = useApiQuery<CollectionItem[]>('/api/v1/hadith/collections');

  const collectionName = useMemo(() => {
    if (collectionsState.kind !== 'success') return null;
    const cid = state.kind === 'success' ? state.data.collectionId : null;
    if (!cid) return null;
    const c = collectionsState.data.find((x) => x.id === cid);
    return c ? c.nameRu || c.nameEn || c.nameAr || c.slug || null : null;
  }, [collectionsState, state]);

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[1380px] px-3 py-6 sm:px-6 sm:py-8">
        <div className="mb-4">
          <Link to="/hadith/hadiths" className="text-sm text-ink-500 hover:text-accent-700">
            <span className="inline-flex items-center gap-1">
              <ArrowLeft size={14} aria-hidden /> {t('nav.hadith')}
            </span>
          </Link>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{state.message}</div>
          </Card>
        )}

        {state.kind === 'success' && (
          <>
            <HadithSectionNav items={SECTIONS} />

            <article className="space-y-12">
              {/* 1. Текст-герой */}
              <section id="text" className={SECTION_ANCHOR}>
                <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
                  <BookOpen size={12} aria-hidden /> {t('hadith.detail.text_heading')}
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-600">
                  {collectionName && (
                    <span className="font-medium text-ink-700" dir="auto">
                      {collectionName}
                    </span>
                  )}
                  {state.data.primaryNumber != null && (
                    <span className="font-mono text-ink-500">№{state.data.primaryNumber}</span>
                  )}
                  <span
                    className={`rounded-sm px-2 py-0.5 text-xs font-semibold ${statusClass(state.data.status)}`}
                  >
                    {state.data.status}
                  </span>
                </div>
                <h1
                  className="mt-4 font-arabic text-2xl leading-loose text-ink-900 sm:text-3xl"
                  dir="rtl"
                >
                  {state.data.normalizedMatn}
                </h1>
                {STATUS_EXPLAIN[state.data.status] && (
                  <p className="mt-3 max-w-2xl text-xs leading-snug text-ink-500">
                    {t(STATUS_EXPLAIN[state.data.status] as DictKey)}
                  </p>
                )}
              </section>

              {/* 2. Граф иснада — полноэкранный */}
              <section id="sanad" className={SECTION_ANCHOR}>
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-500">
                    <Network size={14} aria-hidden /> {t('hadith.detail.graph')}
                  </h2>
                  <span className="text-xs text-ink-400">{t('hadith.detail.tap_hint')}</span>
                </div>
                <div className="h-[60vh] w-full overflow-hidden rounded-lg border border-border-strong bg-sunken md:h-[70vh]">
                  {id && <SanadGraph hadithId={id} />}
                </div>
              </section>

              {/* 3. Оценки учёных */}
              <section id="grades" className={SECTION_ANCHOR}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                  {t('hadith.detail.grades')}
                  {state.data.grades.length > 0 && ` · ${state.data.grades.length}`}
                </h2>
                <HadithGradesList grades={state.data.grades ?? []} />
              </section>

              {/* 4. Вариации matn'а */}
              <section id="variations" className={SECTION_ANCHOR}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                  {t('hadith.detail.matns')} · {state.data.matns.length}
                </h2>
                <MatnVariations matns={state.data.matns} />
              </section>
            </article>
          </>
        )}
      </div>
    </main>
  );
}

export default HadithDetailPage;
