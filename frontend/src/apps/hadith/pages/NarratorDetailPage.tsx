import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import { RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import type { HadithSummaryDto, NarratorResponseDto, Paged } from '@/apps/hadith/types';

/**
 * Биография передатчика + список переданных им хадисов (علم الرجال).
 * Тянет /narrators/{id} (bio) и /narrators/{id}/transmitted параллельно.
 */
function NarratorDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();
  const [bio, setBio] = useState<NarratorResponseDto | null>(null);
  const [transmitted, setTransmitted] = useState<HadithSummaryDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    // Сброс при смене id передатчика: новый fetch = loading + чистая
    // ошибка (idiom как в useApiQuery), а не cosmetic setState.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);
    Promise.all([
      apiGetRaw<NarratorResponseDto>(`/api/v1/hadith/narrators/${id}`, { signal: controller.signal }),
      apiGetRaw<Paged<HadithSummaryDto>>(`/api/v1/hadith/narrators/${id}/transmitted`, {
        signal: controller.signal,
      }),
    ])
      .then(([b, tx]) => {
        setBio(b);
        setTransmitted(tx.items);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e.problem.title : String(e));
        setLoading(false);
      });
    return () => controller.abort();
  }, [id]);

  const rel = bio?.reliabilityGrade ? RELIABILITY_TOKENS[bio.reliabilityGrade] : null;

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-[980px] px-3 py-6 sm:px-6 sm:py-8">
        <div className="mb-4">
          <Link to="/hadith/narrators" className="text-sm text-ink-500 hover:text-accent-700">
            <span className="inline-flex items-center gap-1">
              <ArrowLeft size={14} aria-hidden /> {t('hadith.narrators.title')}
            </span>
          </Link>
        </div>

        {loading && (
          <div className="flex items-center gap-2 py-12 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" /> {t('common.loading')}
          </div>
        )}

        {error && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="text-sm text-ink-900">{error}</div>
          </Card>
        )}

        {bio && !loading && (
          <article>
            <header className="mb-6">
              <div className="flex items-start justify-between gap-3">
                <h1 className="font-arabic text-3xl leading-tight text-ink-900" dir="rtl">
                  {bio.nameAr}
                </h1>
                {rel && bio.reliabilityGrade && (
                  <span
                    className={`shrink-0 rounded-sm px-2 py-1 font-arabic text-sm font-semibold ${rel.chip}`}
                    dir="rtl"
                  >
                    {rel.ar}
                  </span>
                )}
              </div>
              {rel && bio.reliabilityGrade && (
                <div className="mt-1 text-sm font-medium text-ink-600">
                  {t(`hadith.reliability.${bio.reliabilityGrade}` as DictKey)}
                </div>
              )}
            </header>

            <dl className="mb-6 grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-3">
              {bio.kunya && (
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.kunya')}
                  </dt>
                  <dd className="mt-0.5 font-arabic text-sm text-ink-800" dir="rtl">{bio.kunya}</dd>
                </div>
              )}
              {bio.laqab && (
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.laqab')}
                  </dt>
                  <dd className="mt-0.5 font-arabic text-sm text-ink-800" dir="rtl">{bio.laqab}</dd>
                </div>
              )}
              {bio.yearDeathHijri != null && (
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.years')}
                  </dt>
                  <dd className="mt-0.5 text-sm text-ink-800">
                    {bio.yearBirthHijri != null ? `${bio.yearBirthHijri}–` : ''}
                    {bio.yearDeathHijri} {t('hadith.graph.hijri')}
                  </dd>
                </div>
              )}
              {(bio.birthplace || bio.primaryResidence) && (
                <div>
                  <dt className="text-[11px] uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.life_path')}
                  </dt>
                  <dd className="mt-0.5 text-sm text-ink-800">
                    {[bio.birthplace, bio.primaryResidence].filter(Boolean).join(' → ')}
                  </dd>
                </div>
              )}
            </dl>

            {bio.reliabilityComment && (
              <div className="mb-8 rounded-md bg-sunken p-3 text-sm leading-relaxed text-ink-700">
                {bio.reliabilityComment}
              </div>
            )}

            <section>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                {t('hadith.narrators.transmitted_title')} · {transmitted.length}
              </h2>
              {transmitted.length === 0 ? (
                <p className="text-sm text-ink-500">{t('hadith.narrators.transmitted_empty')}</p>
              ) : (
                <ul className="space-y-2">
                  {transmitted.map((h) => (
                    <li key={h.id}>
                      <Link
                        to={`/hadith/hadiths/${h.id}`}
                        className="block rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                      >
                        <Card interactive className="p-3">
                          <div className="mb-1 flex items-center gap-2 text-xs text-ink-500">
                            <BookOpen size={12} aria-hidden />
                            {h.primaryNumber != null && <span className="font-mono">№{h.primaryNumber}</span>}
                            <span className="rounded-sm bg-ink-100 px-1.5 py-0.5 font-semibold uppercase">
                              {h.status}
                            </span>
                          </div>
                          <div className="line-clamp-2 font-arabic text-base text-ink-900" dir="rtl">
                            {h.normalizedMatn}
                          </div>
                        </Card>
                      </Link>
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

export default NarratorDetailPage;
