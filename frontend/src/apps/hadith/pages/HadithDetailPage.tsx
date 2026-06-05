import { useMemo, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2, Network } from 'lucide-react';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import SanadGraph from '@/apps/hadith/components/SanadGraph';
import NarratorPanel from '@/apps/hadith/components/NarratorPanel';
import IsnadText from '@/apps/hadith/components/IsnadText';
import RulingsList from '@/apps/hadith/components/RulingsList';
import ExplanationsList from '@/apps/hadith/components/ExplanationsList';
import CrossrefsList from '@/apps/hadith/components/CrossrefsList';
import EditionsList from '@/apps/hadith/components/EditionsList';
import HadithGradesList from '@/apps/hadith/components/HadithGradesList';
import MatnVariations from '@/apps/hadith/components/MatnVariations';
import MatnTranslateControls from '@/apps/hadith/components/MatnTranslateControls';
import HadithSectionNav, {
  type SectionNavItem,
} from '@/apps/hadith/components/HadithSectionNav';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import { useT, type DictKey, hasArabicScript } from '@/shared/i18n';
import type {
  HadithDetailDto,
  NarratorData,
  SanadFlowNodeData,
  SanadGraphResponse,
  TransmitterRole,
} from '@/apps/hadith/types';
import type { components } from '@/shared/api/types';

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

// Якоря резервируют отступ под две прилипшие полосы (Header h-12 + section
// nav ≈ h-12) + воздух, иначе заголовок секции уезжает под навигацию.
const SECTION_ANCHOR = 'scroll-mt-28';

/** Заголовок секции — единый стиль для всех новых секций. */
function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">{children}</h2>
  );
}

/**
 * Hadith Explorer — страница одного хадиса в чётких секциях. Detail грузится
 * через useApiQuery (SWR-кэш). Граф иснада (sanad-graph) поднят сюда (lifted
 * fetch, фикс C2): страница строит Map<externalId, NarratorData> из узлов
 * графа и владеет ЕДИНЫМ selected-state — клик по графу И клик по рави в
 * тексте открывают одну NarratorPanel (без второй конкурирующей панели).
 * Клик из текста резолвится из уже загруженного графа, без доп. фетча.
 */
function HadithDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();

  const state = useApiQuery<HadithDetailDto>(
    id ? `/api/v1/hadith/hadiths/${id}/detail` : null,
  );
  const collectionsState = useApiQuery<CollectionItem[]>('/api/v1/hadith/collections');
  // Lifted sanad-graph fetch: страница владеет графом (для клик-резолва иснада).
  const graphState = useApiQuery<SanadGraphResponse>(
    id ? `/api/v1/hadith/hadiths/${id}/sanad-graph` : null,
  );

  // Единая панель передатчика: и граф-клики, и текст-клики ставят сюда.
  const [selectedNarrator, setSelectedNarrator] = useState<SanadFlowNodeData | null>(null);
  // Форма имени из текста иснада (клик из IsnadText) — undefined при клике из графа.
  const [selectedTextForm, setSelectedTextForm] = useState<string | undefined>(undefined);

  // Тогл «Основная цепь | Все пути»: turuq-graph грузится лениво (раз) при
  // первом переключении, кэшируется в state.
  const [viewMode, setViewMode] = useState<'main' | 'turuq'>('main');
  const [turuqGraph, setTuruqGraph] = useState<SanadGraphResponse | null>(null);
  const [turuqLoading, setTuruqLoading] = useState(false);

  const graph = graphState.kind === 'success' ? graphState.data : null;

  // Карта externalId → NarratorData из узлов графа. Пока граф грузится — null
  // (рави в тексте не-кликабельны). resolve клика идёт ОТСЮДА, без доп. фетча.
  const narratorByExternalId = useMemo(() => {
    if (!graph) return null;
    const map = new Map<string, NarratorData>();
    for (const node of graph.nodes) {
      // Version-узлы (data===null) пропускаем — карта только для рави-резолва.
      if (!node.data) continue;
      const ext = node.data.externalId;
      if (ext != null) map.set(ext, node.data);
    }
    return map;
  }, [graph]);

  const collectionName = useMemo(() => {
    if (collectionsState.kind !== 'success') return null;
    const cid = state.kind === 'success' ? state.data.collectionId : null;
    if (!cid) return null;
    const c = collectionsState.data.find((x) => x.id === cid);
    return c ? c.nameRu || c.nameEn || c.nameAr || c.slug || null : null;
  }, [collectionsState, state]);

  const detail = state.kind === 'success' ? state.data : null;
  // Primary-матн (рендерится в hero) — для on-demand AI-перевода под текстом.
  const primaryMatn = detail?.matns.find((m) => m.isPrimary) ?? detail?.matns[0] ?? null;

  // Число импортированных параллельных передач (resolved crossrefs) — тогл
  // «Все пути» виден только при наличии хотя бы одной.
  const resolvedTuruqCount = detail?.crossrefs?.filter((c) => c.relatedHadithId).length ?? 0;

  // Переключение на «Все пути»: ленивый фетч turuq-graph (раз, кэш в state).
  const handleShowTuruq = () => {
    setViewMode('turuq');
    if (turuqGraph || turuqLoading || !id) return;
    setTuruqLoading(true);
    apiGetRaw<SanadGraphResponse>(`/api/v1/hadith/hadiths/${id}/turuq-graph`)
      .then((g) => setTuruqGraph(g))
      .catch((e: unknown) => {
        // Тихий фолбэк на основную цепь: показываем её, не роняем секцию.
        setViewMode('main');
        if (!(e instanceof ApiError)) return;
      })
      .finally(() => setTuruqLoading(false));
  };

  // Граф для рендера: основная цепь либо объединённый turuq (controlled).
  const displayGraph = viewMode === 'turuq' ? turuqGraph : graph;

  // Секции навигации зависят от наличия данных (graceful hide пустых).
  const sections = useMemo<SectionNavItem[]>(() => {
    const items: SectionNavItem[] = [{ id: 'text', labelKey: 'hadith.detail.nav.text' }];
    if (detail?.fullTextAr) items.push({ id: 'isnad-text', labelKey: 'hadith.detail.nav.isnad_text' });
    items.push({ id: 'sanad', labelKey: 'hadith.detail.nav.sanad' });
    if ((detail?.rulings?.length ?? 0) > 0) items.push({ id: 'rulings', labelKey: 'hadith.detail.nav.rulings' });
    if ((detail?.explanations?.length ?? 0) > 0) items.push({ id: 'explanations', labelKey: 'hadith.detail.nav.explanations' });
    if ((detail?.crossrefs?.length ?? 0) > 0) items.push({ id: 'crossrefs', labelKey: 'hadith.detail.nav.crossrefs' });
    if ((detail?.editions?.length ?? 0) > 0) items.push({ id: 'editions', labelKey: 'hadith.detail.nav.editions' });
    // Оценки учёных — РУЧНЫЕ оценки платформы (не дубль вердиктов): секцию
    // и пункт навигации показываем только при непустом списке.
    if ((detail?.grades?.length ?? 0) > 0) items.push({ id: 'grades', labelKey: 'hadith.detail.nav.grades' });
    // Вариации — у alminasa 1 запись = 1 матн; секция нужна только при >1.
    if ((detail?.matns.length ?? 0) > 1) items.push({ id: 'variations', labelKey: 'hadith.detail.nav.variations' });
    return items;
  }, [detail]);

  // Клик по рави в тексте: NarratorData из графа → добавляем role для панели,
  // сохраняем форму имени из текста (textForm) для подписи «في الإسناد».
  const handleTextNarratorClick = (data: NarratorData, textForm: string) => {
    const node = graph?.nodes.find((n) => n.data?.externalId === data.externalId);
    // Рави-узлы всегда передатчики (у version-узлов нет externalId в data);
    // VERSION сюда не попадёт — фолбэк на NARRATOR для типобезопасности.
    const role: TransmitterRole =
      node && node.role !== 'VERSION' ? node.role : 'NARRATOR';
    setSelectedNarrator({ ...data, role });
    setSelectedTextForm(textForm);
  };

  // Клик из графа: открываем панель без textForm (форма имени из узла = каноническая).
  const handleGraphNarratorSelect = (data: SanadFlowNodeData) => {
    setSelectedNarrator(data);
    setSelectedTextForm(undefined);
  };

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

        {detail && (
          <>
            <HadithSectionNav items={sections} />

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
                  {detail.primaryNumber != null && (
                    <span className="font-mono text-ink-500">№{detail.primaryNumber}</span>
                  )}
                  <span
                    className={`rounded-sm px-2 py-0.5 text-xs font-semibold ${statusClass(detail.status)}`}
                  >
                    {detail.status}
                  </span>
                  {detail.hadithType && (
                    <span
                      className="rounded-sm bg-violet-100 px-2 py-0.5 text-xs font-semibold text-violet-700"
                      dir="auto"
                    >
                      {detail.hadithType}
                    </span>
                  )}
                </div>
                {(detail.chapterAr || detail.subChapterAr) && (
                  <div
                    className={`mt-2 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-ink-600 ${
                      hasArabicScript(detail.chapterAr ?? detail.subChapterAr ?? '') ? 'font-arabic' : ''
                    }`}
                    dir="auto"
                  >
                    {detail.chapterAr && <span>{detail.chapterAr}</span>}
                    {detail.chapterAr && detail.subChapterAr && (
                      <span className="text-ink-300">/</span>
                    )}
                    {detail.subChapterAr && <span className="text-ink-500">{detail.subChapterAr}</span>}
                  </div>
                )}
                <h1
                  className="mt-4 font-arabic text-2xl leading-loose text-ink-900 sm:text-3xl"
                  dir="rtl"
                >
                  {detail.normalizedMatn}
                </h1>
                {STATUS_EXPLAIN[detail.status] && (
                  <p className="mt-3 max-w-2xl text-xs leading-snug text-ink-500">
                    {t(STATUS_EXPLAIN[detail.status] as DictKey)}
                  </p>
                )}
                {primaryMatn && (
                  <MatnTranslateControls
                    matnId={primaryMatn.id}
                    textRu={primaryMatn.textRu}
                    textEn={primaryMatn.textEn}
                  />
                )}
              </section>

              {/* 2. Иснад (текст) — кликабельные рави. Только если есть fullTextAr. */}
              {detail.fullTextAr && (
                <section id="isnad-text" className={SECTION_ANCHOR}>
                  <SectionHeading>{t('hadith.detail.isnad_text')}</SectionHeading>
                  <Card className="p-4">
                    <IsnadText
                      html={detail.fullTextAr}
                      narratorByExternalId={narratorByExternalId}
                      onNarratorClick={handleTextNarratorClick}
                    />
                  </Card>
                  <p className="mt-2 text-xs text-ink-400">{t('hadith.detail.isnad_text_hint')}</p>
                </section>
              )}

              {/* 3. Граф иснада — полноэкранный (controlled: владеет страница) */}
              <section id="sanad" className={SECTION_ANCHOR}>
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-500">
                    <Network size={14} aria-hidden /> {t('hadith.detail.graph')}
                  </h2>
                  <div className="flex flex-wrap items-center gap-3">
                    {/* Тогл «Основная цепь | Все пути (N)» — только при resolved>0. */}
                    {resolvedTuruqCount > 0 && (
                      <div className="inline-flex rounded-md border border-border-strong p-0.5 text-xs">
                        <button
                          type="button"
                          onClick={() => setViewMode('main')}
                          aria-pressed={viewMode === 'main'}
                          className={`rounded-sm px-2 py-1 font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
                            viewMode === 'main'
                              ? 'bg-accent-50 text-accent-700'
                              : 'text-ink-500 hover:text-ink-700'
                          }`}
                        >
                          {t('hadith.detail.graph.main_chain')}
                        </button>
                        <button
                          type="button"
                          onClick={handleShowTuruq}
                          aria-pressed={viewMode === 'turuq'}
                          className={`inline-flex items-center gap-1.5 rounded-sm px-2 py-1 font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
                            viewMode === 'turuq'
                              ? 'bg-accent-50 text-accent-700'
                              : 'text-ink-500 hover:text-ink-700'
                          }`}
                        >
                          {t('hadith.detail.graph.all_paths').replace(
                            '{count}',
                            String(resolvedTuruqCount),
                          )}
                          {turuqLoading && (
                            <Loader2 size={12} className="animate-spin" aria-hidden />
                          )}
                        </button>
                      </div>
                    )}
                    <span className="text-xs text-ink-400">{t('hadith.detail.tap_hint')}</span>
                  </div>
                </div>
                <div className="relative h-[60vh] w-full overflow-hidden rounded-lg border border-border-strong bg-sunken md:h-[70vh]">
                  {/* turuq ещё грузится (кэша нет) → спиннер вместо пустого графа. */}
                  {viewMode === 'turuq' && turuqLoading && !turuqGraph ? (
                    <div className="flex h-full items-center justify-center gap-2 text-sm text-ink-500">
                      <Loader2 size={16} className="animate-spin" aria-hidden />{' '}
                      {t('hadith.graph.loading')}
                    </div>
                  ) : (
                    <SanadGraph
                      graph={displayGraph}
                      currentHadithId={id}
                      onNarratorSelect={handleGraphNarratorSelect}
                    />
                  )}
                  {/* Единая панель: клики из графа И из текста иснада. */}
                  {selectedNarrator && (
                    <NarratorPanel
                      data={selectedNarrator}
                      textForm={selectedTextForm}
                      onClose={() => setSelectedNarrator(null)}
                    />
                  )}
                </div>
              </section>

              {/* 4. Вердикты (rulings) */}
              {detail.rulings && detail.rulings.length > 0 && (
                <section id="rulings" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.rulings')} · {detail.rulings.length}
                  </SectionHeading>
                  <RulingsList rulings={detail.rulings} hadithExternalId={detail.externalId} />
                </section>
              )}

              {/* 5. Шарх (explanations) */}
              {detail.explanations && detail.explanations.length > 0 && (
                <section id="explanations" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.explanations')} · {detail.explanations.length}
                  </SectionHeading>
                  <ExplanationsList explanations={detail.explanations} />
                </section>
              )}

              {/* 6. Такхридж (crossrefs) */}
              {detail.crossrefs && detail.crossrefs.length > 0 && (
                <section id="crossrefs" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.crossrefs')}
                    {' · '}
                    {t('hadith.detail.crossref.count').replace(
                      '{count}',
                      String(detail.crossrefs.length),
                    )}
                  </SectionHeading>
                  <CrossrefsList crossrefs={detail.crossrefs} />
                </section>
              )}

              {/* 7. Издания (editions) */}
              {detail.editions && detail.editions.length > 0 && (
                <section id="editions" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.editions')} · {detail.editions.length}
                  </SectionHeading>
                  <EditionsList editions={detail.editions} />
                </section>
              )}

              {/* 8. Оценки учёных — РУЧНЫЕ оценки платформы; скрыта при пустоте. */}
              {detail.grades.length > 0 && (
                <section id="grades" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.grades')} · {detail.grades.length}
                  </SectionHeading>
                  <HadithGradesList grades={detail.grades} />
                </section>
              )}

              {/* 9. Вариации matn'а — скрыта при ≤1 матне (1 запись = 1 матн). */}
              {detail.matns.length > 1 && (
                <section id="variations" className={SECTION_ANCHOR}>
                  <SectionHeading>
                    {t('hadith.detail.matns')} · {detail.matns.length}
                  </SectionHeading>
                  <MatnVariations
                    matns={detail.matns}
                    translateInHeroForId={primaryMatn?.id ?? null}
                  />
                </section>
              )}
            </article>
          </>
        )}
      </div>
    </main>
  );
}

export default HadithDetailPage;
