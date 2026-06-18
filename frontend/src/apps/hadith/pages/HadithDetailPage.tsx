import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, Loader2, Network, Plus } from 'lucide-react';
import { apiGetRaw } from '@/shared/api/client';
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
import AddHadithGradeModal from '@/apps/hadith/components/AddHadithGradeModal';
import MatnVariations from '@/apps/hadith/components/MatnVariations';
import SiblingMatns from '@/apps/hadith/components/SiblingMatns';
import MatnTranslateControls from '@/apps/hadith/components/MatnTranslateControls';
import HighlightedMatn from '@/apps/hadith/components/HighlightedMatn';
import HadithTabs, { type HadithTabItem } from '@/apps/hadith/components/HadithTabs';
import { parseIsnadHtml } from '@/apps/hadith/utils/parseIsnadHtml';
import { useApiQuery } from '@/shared/hooks/useApiQuery';
import { invalidateCache } from '@/shared/hooks/queryCache';
import Button from '@/shared/components/ui/Button';
import { useAuthStore, hasRoleAtLeast } from '@/shared/stores/authStore';
import { useT, type DictKey, hasArabicScript } from '@/shared/i18n';
import type {
  ExplanationDto,
  HadithDetailDto,
  NarratorData,
  SanadFlowNodeData,
  SanadGraphResponse,
  TransmitterRole,
} from '@/apps/hadith/types';
import type { components } from '@/shared/api/types';

type CollectionItem = components['schemas']['CollectionResponse'];

/** Цвет бейджа происхождения (ось провенанса CANONICAL/VARIANT). */
function statusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

/** Цвет бейджа достоверности по оси authenticity. */
function authenticityClass(authenticity: string): string {
  switch (authenticity) {
    case 'SAHIH':
      return 'bg-emerald-100 text-emerald-700';
    case 'HASAN':
      return 'bg-teal-100 text-teal-700';
    case 'DAIF':
      return 'bg-amber-100 text-amber-700';
    case 'MAUDU':
      return 'bg-rose-100 text-rose-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

/** Ключ пояснения статуса (провенанс) — полная фраза под текстом-героем. */
const STATUS_EXPLAIN: Record<string, DictKey> = {
  CANONICAL: 'hadith.detail.status.CANONICAL',
  VARIANT: 'hadith.detail.status.VARIANT',
  WEAK: 'hadith.detail.status.WEAK',
  FABRICATED: 'hadith.detail.status.FABRICATED',
};

/** Короткий лейбл бейджа происхождения (CANONICAL/VARIANT → i18n). */
const STATUS_SHORT: Record<string, DictKey> = {
  CANONICAL: 'hadith.detail.status.CANONICAL.short',
  VARIANT: 'hadith.detail.status.VARIANT.short',
};

/** Лейбл + tooltip бейджа достоверности (SAHIH/HASAN/DAIF/MAUDU). */
const AUTHENTICITY_LABEL: Record<string, DictKey> = {
  SAHIH: 'hadith.detail.authenticity.SAHIH',
  HASAN: 'hadith.detail.authenticity.HASAN',
  DAIF: 'hadith.detail.authenticity.DAIF',
  MAUDU: 'hadith.detail.authenticity.MAUDU',
};
const AUTHENTICITY_TIP: Record<string, DictKey> = {
  SAHIH: 'hadith.detail.authenticity.SAHIH.tip',
  HASAN: 'hadith.detail.authenticity.HASAN.tip',
  DAIF: 'hadith.detail.authenticity.DAIF.tip',
  MAUDU: 'hadith.detail.authenticity.MAUDU.tip',
};

/** Тип хадиса (مرفوع/موقوف/مقطوع/قدسي) → i18n-лейбл + tooltip; ключ = ар-значение. */
const HADITH_TYPE_LABEL: Record<string, DictKey> = {
  مرفوع: 'hadith.detail.hadithType.مرفوع',
  موقوف: 'hadith.detail.hadithType.موقوف',
  مقطوع: 'hadith.detail.hadithType.مقطوع',
  قدسي: 'hadith.detail.hadithType.قدسي',
};
const HADITH_TYPE_TIP: Record<string, DictKey> = {
  مرفوع: 'hadith.detail.hadithType.مرفوع.tip',
  موقوف: 'hadith.detail.hadithType.موقوف.tip',
  مقطوع: 'hadith.detail.hadithType.مقطوع.tip',
  قدسي: 'hadith.detail.hadithType.قدسي.tip',
};

/** Заголовок секции — единый стиль для всех новых секций. */
function SectionHeading({ children }: { children: ReactNode }) {
  return (
    <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">{children}</h2>
  );
}

/**
 * Hadith Explorer — страница одного хадиса. Detail грузится через useApiQuery
 * (SWR-кэш). Граф иснада (sanad-graph) поднят сюда (lifted fetch, фикс C2):
 * страница строит Map<externalId, NarratorData> из узлов графа и владеет ЕДИНЫМ
 * selected-state — клик по графу И клик по рави в тексте открывают одну
 * NarratorPanel (без второй конкурирующей панели). Клик из текста резолвится из
 * уже загруженного графа, без доп. фетча.
 *
 * Контент разложен по вкладкам-переключателю (С10): клик по вкладке показывает
 * ТОЛЬКО её секцию (Текст / Граф / Вердикты / Шарх / …); остальные скрыты.
 * Активная вкладка — single-state, синхронизирована с URL hash (deep-link).
 * Шапка хадиса (сборник, номер, бейджи провенанса/достоверности/типа, глава)
 * — над переключателем, постоянный контекст. Вкладка «Текст» — огласованный
 * full_text_ar с кликабельными рави И гариб-подсветкой на не-рави сегментах
 * (С7); legacy без full_text_ar → fallback на normalizedMatn.
 */
function HadithDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();

  // Роль для гейта write-действий (добавление оценки — SCHOLAR+, как другие
  // admin/научные действия). Аноним/USER/STUDENT кнопку не видят.
  const userRole = useAuthStore((s) => s.user?.role);
  const canGrade = hasRoleAtLeast(userRole, 'SCHOLAR');

  // Модалка «Добавить оценку» + nonce для рефетча detail после POST. Nonce
  // вшит в path (useApiQuery рефетчит при смене path); при 0 — path без
  // суффикса (existing-поведение и тесты не затронуты).
  const [gradeModalOpen, setGradeModalOpen] = useState(false);
  const [gradeNonce, setGradeNonce] = useState(0);

  const detailPath = id
    ? `/api/v1/hadith/hadiths/${id}/detail${gradeNonce > 0 ? `?r=${gradeNonce}` : ''}`
    : null;
  const state = useApiQuery<HadithDetailDto>(detailPath);
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

  // Толкования разбиты на три независимые секции по kind: شروح (SHARH),
  // علل (ILAL — скрытые дефекты), غريب (GHARIB — редкие слова). Группируем
  // ОДНИМ проходом, сохраняя порядок с бэка (ORDER BY kind стабилен — не
  // пересортировываем). Каждая секция graceful-hide при пустоте.
  const explanationGroups = useMemo(() => {
    const sharh: ExplanationDto[] = [];
    const ilal: ExplanationDto[] = [];
    const gharib: ExplanationDto[] = [];
    for (const e of detail?.explanations ?? []) {
      if (e.kind === 'ILAL') ilal.push(e);
      else if (e.kind === 'GHARIB') gharib.push(e);
      else sharh.push(e); // SHARH и legacy/неизвестное → в общий «Шарх»
    }
    return { sharh, ilal, gharib };
  }, [detail?.explanations]);

  // Число импортированных параллельных передач (resolved crossrefs) — тогл
  // «Все пути» виден только при наличии хотя бы одной.
  const resolvedTuruqCount = detail?.crossrefs?.filter((c) => c.relatedHadithId).length ?? 0;

  // БЕЗ unmounted-гарда (откат review-минора С59): React 18+ сам
  // безопасно глушит setState после unmount, а ref-гард ломался в
  // StrictMode навсегда — dev-симуляция remount гоняет cleanup, флаг
  // не сбрасывался → результат фетча молча выбрасывался, спиннер
  // вечный (gotcha «StrictMode + unmounted-ref»).

  // Переключение на «Все пути»: ленивый фетч turuq-graph (раз, кэш в state).
  const handleShowTuruq = () => {
    setViewMode('turuq');
    if (turuqGraph || turuqLoading || !id) return;
    setTuruqLoading(true);
    apiGetRaw<SanadGraphResponse>(`/api/v1/hadith/hadiths/${id}/turuq-graph`)
      .then((g) => {
        setTuruqGraph(g);
      })
      .catch(() => {
        // Тихий фолбэк на основную цепь: показываем её, не роняем секцию.
        setViewMode('main');
      })
      .finally(() => {
        setTuruqLoading(false);
      });
  };

  // Граф для рендера: основная цепь либо объединённый turuq (controlled).
  const displayGraph = viewMode === 'turuq' ? turuqGraph : graph;

  // Вкладки переключателя зависят от наличия данных (graceful hide пустых).
  // «Текст» (огласованный full_text_ar ИЛИ legacy normalizedMatn) и «Граф»
  // есть всегда; остальные — по условию на свои данные.
  const tabs = useMemo<HadithTabItem[]>(() => {
    const items: HadithTabItem[] = [
      { id: 'text', labelKey: 'hadith.detail.nav.text' },
      // «Иснад» = вкладка с графом иснада (sanad-graph). Текст иснада влит в
      // «Текст» (С7), поэтому здесь — структурный граф цепи передачи.
      { id: 'sanad', labelKey: 'hadith.detail.nav.sanad' },
    ];
    if ((detail?.rulings?.length ?? 0) > 0) items.push({ id: 'rulings', labelKey: 'hadith.detail.nav.rulings' });
    // Три независимые секции толкований — вкладка по условию на свою группу.
    if (explanationGroups.sharh.length > 0) items.push({ id: 'explanations', labelKey: 'hadith.detail.nav.explanations' });
    if (explanationGroups.ilal.length > 0) items.push({ id: 'ilal', labelKey: 'hadith.detail.nav.ilal' });
    if (explanationGroups.gharib.length > 0) items.push({ id: 'gharib', labelKey: 'hadith.detail.nav.gharib' });
    if ((detail?.crossrefs?.length ?? 0) > 0) items.push({ id: 'crossrefs', labelKey: 'hadith.detail.nav.crossrefs' });
    if ((detail?.editions?.length ?? 0) > 0) items.push({ id: 'editions', labelKey: 'hadith.detail.nav.editions' });
    // Оценки учёных — РУЧНЫЕ оценки платформы (не дубль вердиктов): вкладку
    // показываем при непустом списке ЛИБО когда у юзера есть право добавить
    // оценку (SCHOLAR+ видит вкладку с кнопкой «Добавить»).
    if ((detail?.grades?.length ?? 0) > 0 || canGrade) items.push({ id: 'grades', labelKey: 'hadith.detail.nav.grades' });
    // Вариации — у alminasa 1 запись = 1 матн; вкладка нужна только при >1.
    if ((detail?.matns.length ?? 0) > 1) items.push({ id: 'variations', labelKey: 'hadith.detail.nav.variations' });
    // Параллельные тексты — ленивый блок, видим только при наличии resolved crossrefs.
    if (resolvedTuruqCount > 0) items.push({ id: 'sibling-matns', labelKey: 'hadith.detail.nav.sibling_matns' });
    return items;
  }, [detail, explanationGroups, resolvedTuruqCount, canGrade]);

  // Активная вкладка переключателя — single-state, deep-link через URL hash.
  // Инициализация из hash (если он валиден для какой-то вкладки), иначе первая.
  const [activeTab, setActiveTab] = useState<string>(() => {
    const fromHash = typeof window !== 'undefined' ? window.location.hash.slice(1) : '';
    return fromHash || 'text';
  });

  // Если активная вкладка пропала из набора (сменился хадис / условие отвалилось)
  // — откатываемся на первую доступную. tabs всегда непустой (текст+граф).
  const activeTabId = tabs.some((tt) => tt.id === activeTab) ? activeTab : (tabs[0]?.id ?? 'text');

  // Смена вкладки: обновляем state + URL hash (deep-link), без скролл-прыжка
  // (replaceState не триггерит нативный scroll-to-anchor).
  const handleTabSelect = (next: string) => {
    setActiveTab(next);
    if (typeof window !== 'undefined') {
      window.history.replaceState(null, '', `#${next}`);
    }
  };

  // Hash изменился извне (back/forward, прямой deep-link) → синхронизируем state.
  useEffect(() => {
    const onHashChange = () => {
      const h = window.location.hash.slice(1);
      if (h) setActiveTab(h);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  // Клик по рави в тексте (вкладка «Текст»): NarratorData из графа → добавляем
  // role для панели, сохраняем форму имени из текста (textForm) для подписи «في
  // الإسناد». Переключаемся на вкладку «Иснад»: NarratorPanel абсолютно
  // позиционируется внутри контейнера графа (его relative-родитель живёт только
  // на этой вкладке), плюс рави виден в графе в контексте цепи.
  const handleTextNarratorClick = (data: NarratorData, textForm: string) => {
    const node = graph?.nodes.find((n) => n.data?.externalId === data.externalId);
    // Рави-узлы всегда передатчики (у version-узлов нет externalId в data);
    // VERSION сюда не попадёт — фолбэк на NARRATOR для типобезопасности.
    const role: TransmitterRole =
      node && node.role !== 'VERSION' ? node.role : 'NARRATOR';
    setSelectedNarrator({ ...data, role });
    setSelectedTextForm(textForm);
    handleTabSelect('sanad');
  };

  // Форма имени из текста иснада по externalId — чтобы подпись
  // «كما ورد في الإسناد» показывалась и при клике ИЗ ГРАФА (консистентность
  // панелей — фидбек С59).
  const textFormByExternalId = useMemo(() => {
    if (!detail?.fullTextAr) return null;
    const map = new Map<string, string>();
    for (const seg of parseIsnadHtml(detail.fullTextAr)) {
      if (seg.kind === 'rawy' && seg.externalId != null && !map.has(seg.externalId)) {
        map.set(seg.externalId, seg.text);
      }
    }
    return map;
  }, [detail?.fullTextAr]);

  // Клик из графа: textForm резолвим из текста иснада по externalId
  // (рави без rawy-тега в тексте — подпись не показывается).
  const handleGraphNarratorSelect = (data: SanadFlowNodeData) => {
    setSelectedNarrator(data);
    setSelectedTextForm(
      data.externalId != null ? textFormByExternalId?.get(data.externalId) : undefined,
    );
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
            {/* Шапка хадиса — постоянный контекст над переключателем: сборник,
                номер, бейджи провенанса/достоверности/типа, глава/подглава. */}
            <header className="mb-2">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-600">
                {collectionName && (
                  <span className="font-medium text-ink-700" dir="auto">
                    {collectionName}
                  </span>
                )}
                {detail.primaryNumber != null && (
                  <span className="font-mono text-ink-500">№{detail.primaryNumber}</span>
                )}
                {/* Ось ПРОВЕНАНСА (происхождение): i18n-лейбл + tooltip. */}
                <span
                  className={`rounded-sm px-2 py-0.5 text-xs font-semibold ${statusClass(detail.status)}`}
                  title={
                    STATUS_EXPLAIN[detail.status]
                      ? t(STATUS_EXPLAIN[detail.status] as DictKey)
                      : undefined
                  }
                >
                  {STATUS_SHORT[detail.status]
                    ? t(STATUS_SHORT[detail.status] as DictKey)
                    : detail.status}
                </span>
                {/* Ось ДОСТОВЕРНОСТИ (если выведена): бейдж + tooltip-пояснение. */}
                {detail.authenticity && AUTHENTICITY_LABEL[detail.authenticity] && (
                  <span
                    className={`rounded-sm px-2 py-0.5 text-xs font-semibold ${authenticityClass(detail.authenticity)}`}
                    title={
                      AUTHENTICITY_TIP[detail.authenticity]
                        ? t(AUTHENTICITY_TIP[detail.authenticity] as DictKey)
                        : undefined
                    }
                  >
                    {t(AUTHENTICITY_LABEL[detail.authenticity] as DictKey)}
                  </span>
                )}
                {/* Тип хадиса (مرفوع/...): РЕАЛЬНЫЙ термин i18n + tooltip-определение. */}
                {detail.hadithType && (
                  <span
                    className="rounded-sm bg-violet-100 px-2 py-0.5 text-xs font-semibold text-violet-700"
                    dir="auto"
                    title={
                      HADITH_TYPE_TIP[detail.hadithType]
                        ? t(HADITH_TYPE_TIP[detail.hadithType] as DictKey)
                        : undefined
                    }
                  >
                    {HADITH_TYPE_LABEL[detail.hadithType]
                      ? t(HADITH_TYPE_LABEL[detail.hadithType] as DictKey)
                      : detail.hadithType}
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
            </header>

            <HadithTabs items={tabs} active={activeTabId} onSelect={handleTabSelect} />

            {/* Переключатель: рендерим ТОЛЬКО активную вкладку (С10). */}
            <div role="tabpanel">
              {/* Вкладка «Текст» — огласованный full_text_ar с кликабельными рави
                  И гариб-подсветкой на не-рави сегментах (С7); legacy без
                  full_text_ar → fallback на normalizedMatn. */}
              {activeTabId === 'text' && (
                <section>
                  {detail.fullTextAr ? (
                    <>
                      <h1
                        className="font-arabic text-xl leading-loose text-ink-900 sm:text-2xl"
                        dir="rtl"
                      >
                        <IsnadText
                          html={detail.fullTextAr}
                          narratorByExternalId={narratorByExternalId}
                          onNarratorClick={handleTextNarratorClick}
                          gharib={explanationGroups.gharib}
                        />
                      </h1>
                      <p className="mt-2 text-xs text-ink-400">{t('hadith.detail.isnad_text_hint')}</p>
                    </>
                  ) : (
                    <h1
                      className="font-arabic text-2xl leading-loose text-ink-900 sm:text-3xl"
                      dir="rtl"
                    >
                      <HighlightedMatn matn={detail.normalizedMatn} gharib={explanationGroups.gharib} />
                    </h1>
                  )}
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
                      role={userRole}
                    />
                  )}
                </section>
              )}

              {/* Вкладка «Граф» — полноэкранный (controlled: владеет страница) */}
              {activeTabId === 'sanad' && (
                <section>
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
              )}

              {/* Вкладка «Вердикты» (rulings) */}
              {activeTabId === 'rulings' && detail.rulings && detail.rulings.length > 0 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.rulings')} · {detail.rulings.length}
                  </SectionHeading>
                  <RulingsList rulings={detail.rulings} hadithExternalId={detail.externalId} />
                </section>
              )}

              {/* Вкладка «Шарх» (kind=SHARH) — общий разбор хадиса */}
              {activeTabId === 'explanations' && explanationGroups.sharh.length > 0 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.explanations')} · {explanationGroups.sharh.length}
                  </SectionHeading>
                  <ExplanationsList explanations={explanationGroups.sharh} variant="SHARH" />
                </section>
              )}

              {/* Вкладка «Иляль» (kind=ILAL) — скрытые дефекты передачи */}
              {activeTabId === 'ilal' && explanationGroups.ilal.length > 0 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.ilal')} · {explanationGroups.ilal.length}
                  </SectionHeading>
                  <p className="-mt-2 mb-3 text-xs leading-snug text-ink-500">
                    {t('hadith.detail.ilal.hint')}
                  </p>
                  <ExplanationsList explanations={explanationGroups.ilal} variant="ILAL" />
                </section>
              )}

              {/* Вкладка «Гариб» (kind=GHARIB) — толкования редких слов матна */}
              {activeTabId === 'gharib' && explanationGroups.gharib.length > 0 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.gharib')} · {explanationGroups.gharib.length}
                  </SectionHeading>
                  <p className="-mt-2 mb-3 text-xs leading-snug text-ink-500">
                    {t('hadith.detail.gharib.hint')}
                  </p>
                  <ExplanationsList explanations={explanationGroups.gharib} variant="GHARIB" />
                </section>
              )}

              {/* Вкладка «Такхридж» (crossrefs) */}
              {activeTabId === 'crossrefs' && detail.crossrefs && detail.crossrefs.length > 0 && (
                <section>
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

              {/* Вкладка «Издания» (editions) */}
              {activeTabId === 'editions' && detail.editions && detail.editions.length > 0 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.editions')} · {detail.editions.length}
                  </SectionHeading>
                  <EditionsList editions={detail.editions} />
                </section>
              )}

              {/* Вкладка «Оценки учёных» — РУЧНЫЕ оценки платформы. Видна при
                  наличии оценок ЛИБО когда юзер может добавить (SCHOLAR+): тогда
                  показываем кнопку «Добавить» + empty-state. */}
              {activeTabId === 'grades' && (detail.grades.length > 0 || canGrade) && (
                <section>
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-500">
                      {t('hadith.detail.grades')}
                      {detail.grades.length > 0 ? ` · ${detail.grades.length}` : ''}
                    </h2>
                    {canGrade && (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={Plus}
                        onClick={() => setGradeModalOpen(true)}
                      >
                        {t('hadith.grade.add')}
                      </Button>
                    )}
                  </div>
                  <HadithGradesList grades={detail.grades} />
                </section>
              )}

              {/* Вкладка «Вариации» matn'а — только при >1 матне (1 запись = 1 матн). */}
              {activeTabId === 'variations' && detail.matns.length > 1 && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.matns')} · {detail.matns.length}
                  </SectionHeading>
                  <MatnVariations
                    matns={detail.matns}
                    translateInHeroForId={primaryMatn?.id ?? null}
                  />
                </section>
              )}

              {/* Вкладка «Параллельные тексты» — ленивый блок, при resolved crossrefs > 0. */}
              {activeTabId === 'sibling-matns' && resolvedTuruqCount > 0 && id && (
                <section>
                  <SectionHeading>
                    {t('hadith.detail.nav.sibling_matns')}
                  </SectionHeading>
                  <SiblingMatns
                    hadithId={id}
                    resolvedTuruqCount={resolvedTuruqCount}
                    currentMatn={primaryMatn?.textAr}
                  />
                </section>
              )}
            </div>

            {/* Модалка «Добавить оценку» — идиома {open && <Modal/>}; чистый
                state на каждое открытие. После POST инвалидируем кэш detail и
                бампаем nonce → useApiQuery рефетчит свежий список оценок. */}
            {gradeModalOpen && id && (
              <AddHadithGradeModal
                hadithId={id}
                onClose={() => setGradeModalOpen(false)}
                onCreated={() => {
                  if (detailPath) invalidateCache((k) => k === detailPath);
                  setGradeNonce((n) => n + 1);
                }}
              />
            )}
          </>
        )}
      </div>
    </main>
  );
}

export default HadithDetailPage;
