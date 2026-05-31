import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2, Network } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import SanadGraph from '@/apps/hadith/components/SanadGraph';
import HadithGradesList from '@/apps/hadith/components/HadithGradesList';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { HadithGrade } from '@/apps/hadith/types';

// Backend types ещё не regenerated для hadith-домена — inline.
interface MatnDto {
  id: string;
  textAr: string;
  textRu: string | null;
  textEn: string | null;
  sourceBookId: string | null;
  printedNumber: number | null;
  pageNo: number | null;
  volume: number | null;
  isPrimary: boolean;
  divergenceSummary: string | null;
}

interface HadithDetail {
  id: string;
  primaryBookId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: string;
  sourceId: string | null;
  createdAt: string;
  matns: MatnDto[];
  grades: HadithGrade[];
}

/**
 * Hadith Explorer Phase 3 — страница хадиса с графом иснада.
 *
 * <p>Шапка + варианты matn'а грузятся из {@code /detail}; центральная
 * визуализация (граф иснада) — отдельный компонент SanadGraph, который
 * сам тянет {@code /sanad-graph} (дедуплицированный, преднастроенный под
 * React Flow). Раньше иснады рендерились плоским списком UUID — заменено
 * на навигируемый граф.
 */
function HadithDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<AsyncState<HadithDetail>>({ kind: 'loading' });

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    apiGetRaw<HadithDetail>(`/api/v1/hadith/hadiths/${id}/detail`, {
      signal: controller.signal,
    })
      .then((d) => setState({ kind: 'success', data: d }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message = e instanceof ApiError ? e.problem.title : String(e);
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [id]);

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
            <Loader2 size={16} className="animate-spin" /> {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{state.message}</div>
          </Card>
        )}

        {state.kind === 'success' && (
          <article>
            <header className="mb-6">
              <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-ink-500">
                <BookOpen size={12} aria-hidden /> Hadith {state.data.primaryNumber ?? '—'}
              </div>
              <h1 className="mt-1 font-arabic text-2xl leading-relaxed text-ink-900" dir="rtl">
                {state.data.normalizedMatn}
              </h1>
              <div className="mt-2 inline-flex items-center gap-2">
                <span className="rounded-sm bg-ink-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wider">
                  {state.data.status}
                </span>
              </div>
            </header>

            <section className="mb-8">
              <div className="mb-2 flex items-center justify-between gap-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-ink-500">
                  <Network size={14} aria-hidden /> {t('hadith.detail.graph')}
                </h2>
                <span className="text-xs text-ink-400">{t('hadith.detail.tap_hint')}</span>
              </div>
              <div className="h-[560px] w-full overflow-hidden rounded-lg border border-border-strong bg-bg-sunken md:h-[640px]">
                {id && <SanadGraph hadithId={id} />}
              </div>
            </section>

            <HadithGradesList grades={state.data.grades ?? []} />

            <section>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                {t('hadith.detail.matns')} · {state.data.matns.length}
              </h2>
              {state.data.matns.length === 0 ? (
                <p className="text-sm text-ink-500">{t('hadith.detail.no_matns')}</p>
              ) : (
                <ul className="space-y-3">
                  {state.data.matns.map((m) => (
                    <li key={m.id}>
                      <Card className="p-4">
                        <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-ink-500">
                          {m.isPrimary && (
                            <span className="rounded-sm bg-accent-50 px-1.5 py-0.5 font-semibold text-accent-700">
                              {t('hadith.detail.primary')}
                            </span>
                          )}
                          {m.printedNumber != null && <span className="font-mono">№{m.printedNumber}</span>}
                          {m.volume != null && <span>vol.{m.volume}</span>}
                          {m.pageNo != null && <span>p.{m.pageNo}</span>}
                        </div>
                        <p className="font-arabic text-lg leading-loose text-ink-900" dir="rtl">
                          {m.textAr}
                        </p>
                        {m.textRu && <p className="mt-2 text-sm text-ink-700" dir="ltr">{m.textRu}</p>}
                        {m.divergenceSummary && (
                          <p className="mt-2 text-xs italic text-ink-500" dir="auto">
                            {m.divergenceSummary}
                          </p>
                        )}
                      </Card>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </article>
        )}
      </div>
    </main>
  );
}

export default HadithDetailPage;
