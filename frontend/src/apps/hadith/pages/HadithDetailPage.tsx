import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';

// Backend types ещё не regenerated.
interface SanadDto {
  id: string;
  chainGrade: string | null;
  compiledById: string | null;
  compiledInBookId: string | null;
  primaryChain: boolean;
  narrators: Array<{
    position: number;
    narratorId: string;
    transmissionPhrase: string | null;
  }>;
}

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
  sanads: SanadDto[];
  matns: MatnDto[];
}

/**
 * Vision 49d Section 2.6 Phase 2.b — bundled hadith detail.
 * GET /api/v1/hadith/hadiths/{id}/detail.
 *
 * <p>Phase 2.b - text/list rendering. Phase 2.c - React Flow sanad
 * graph viz (mirror argument-map graph stack).
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
              <h1 className="mt-1 text-xl text-ink-900" dir="auto">
                {state.data.normalizedMatn}
              </h1>
              <div className="mt-2 inline-flex items-center gap-2">
                <span className="rounded-sm bg-ink-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wider">
                  {state.data.status}
                </span>
              </div>
            </header>

            <section className="mb-8">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                {t('hadith.detail.sanads')} · {state.data.sanads.length}
              </h2>
              {state.data.sanads.length === 0 ? (
                <p className="text-sm text-ink-500">{t('hadith.detail.no_sanads')}</p>
              ) : (
                <ul className="space-y-4">
                  {state.data.sanads.map((s) => (
                    <li key={s.id}>
                      <Card className="p-4">
                        <div className="mb-2 flex items-center gap-2 text-xs text-ink-500">
                          {s.primaryChain && (
                            <span className="rounded-sm bg-accent-50 text-accent-700 px-1.5 py-0.5 font-semibold">
                              {t('hadith.detail.primary')}
                            </span>
                          )}
                          {s.chainGrade && (
                            <span className="rounded-sm bg-ink-100 px-1.5 py-0.5 font-medium uppercase">
                              {s.chainGrade}
                            </span>
                          )}
                        </div>
                        <ol className="space-y-1 text-sm text-ink-700">
                          {s.narrators.map((n) => (
                            <li key={`${s.id}-${n.position}`} className="flex items-center gap-2">
                              <span className="font-mono text-xs text-ink-400 w-6">#{n.position}</span>
                              {n.transmissionPhrase && (
                                <span className="text-xs text-ink-500" dir="auto">{n.transmissionPhrase}</span>
                              )}
                              <span className="font-mono text-xs text-ink-600">{n.narratorId.slice(0, 8)}</span>
                            </li>
                          ))}
                        </ol>
                      </Card>
                    </li>
                  ))}
                </ul>
              )}
            </section>

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
                        <div className="mb-2 flex items-center gap-2 text-xs text-ink-500">
                          {m.isPrimary && (
                            <span className="rounded-sm bg-accent-50 text-accent-700 px-1.5 py-0.5 font-semibold">
                              {t('hadith.detail.primary')}
                            </span>
                          )}
                          {m.printedNumber != null && <span className="font-mono">№{m.printedNumber}</span>}
                          {m.volume != null && <span>vol.{m.volume}</span>}
                          {m.pageNo != null && <span>p.{m.pageNo}</span>}
                        </div>
                        <p className="text-sm text-ink-900" dir="auto">{m.textAr}</p>
                        {m.textRu && <p className="mt-2 text-sm text-ink-700" dir="ltr">{m.textRu}</p>}
                        {m.divergenceSummary && (
                          <p className="mt-2 text-xs text-ink-500 italic">{m.divergenceSummary}</p>
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
