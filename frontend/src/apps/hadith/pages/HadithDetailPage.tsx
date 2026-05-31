import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2, Network } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import SanadGraph from '@/apps/hadith/components/SanadGraph';
import HadithGradesList from '@/apps/hadith/components/HadithGradesList';
import MatnVariations from '@/apps/hadith/components/MatnVariations';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { HadithGrade, MatnDto } from '@/apps/hadith/types';

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
              <div className="h-[560px] w-full overflow-hidden rounded-lg border border-border-strong bg-sunken md:h-[640px]">
                {id && <SanadGraph hadithId={id} />}
              </div>
            </section>

            <HadithGradesList grades={state.data.grades ?? []} />

            <section>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                {t('hadith.detail.matns')} · {state.data.matns.length}
              </h2>
              <MatnVariations matns={state.data.matns} />
            </section>
          </article>
        )}
      </div>
    </main>
  );
}

export default HadithDetailPage;
