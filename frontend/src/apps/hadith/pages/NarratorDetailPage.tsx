import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, BookOpen, Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useT, type DictKey } from '@/shared/i18n';
import { useAuthStore } from '@/shared/stores/authStore';
import { RELIABILITY_TOKENS } from '@/apps/hadith/sanadTokens';
import EditableField from '@/apps/hadith/components/curation/EditableField';
import NarratorCommentaryList from '@/apps/hadith/components/NarratorCommentaryList';
import { normalizeArabic } from '@/apps/hadith/utils/highlightGharib';
import type { HadithSummaryDto, NarratorCommentaryDto, NarratorResponseDto, Paged } from '@/apps/hadith/types';

/** Цвет чипа провенанса (CANONICAL/VARIANT) — синхронизирован с HadithDetailPage. */
function hadithStatusClass(status: string | undefined): string {
  switch (status) {
    case 'CANONICAL':
      return 'bg-emerald-100 text-emerald-700';
    default:
      return 'bg-ink-100 text-ink-700';
  }
}

/** Короткий лейбл чипа провенанса → i18n. */
const HADITH_STATUS_SHORT: Record<string, DictKey> = {
  CANONICAL: 'hadith.detail.status.CANONICAL.short',
  VARIANT: 'hadith.detail.status.VARIANT.short',
};

/** Tooltip пояснения провенанса → i18n. */
const HADITH_STATUS_EXPLAIN: Record<string, DictKey> = {
  CANONICAL: 'hadith.detail.status.CANONICAL',
  VARIANT: 'hadith.detail.status.VARIANT',
  WEAK: 'hadith.detail.status.WEAK',
  FABRICATED: 'hadith.detail.status.FABRICATED',
};

/** Курация Фаза 3.b — enum-опции ADMIN-правки степени надёжности рави. */
const RELIABILITY_OPTIONS: { value: string; label: string }[] = [
  { value: 'THIQA', label: 'THIQA' },
  { value: 'SADUQ', label: 'SADUQ' },
  { value: 'MAQBUL', label: 'MAQBUL' },
  { value: 'DAIF', label: 'DAIF' },
  { value: 'MATRUK', label: 'MATRUK' },
  { value: 'SAHABI', label: 'SAHABI' },
  { value: 'UNKNOWN', label: 'UNKNOWN' },
];

/**
 * Биография передатчика + список переданных им хадисов (علم الرجال).
 * Тянет /narrators/{id} (bio) и /narrators/{id}/transmitted параллельно.
 */
function NarratorDetailPage() {
  const t = useT();
  const { id } = useParams<{ id: string }>();
  // Роль для гейта ADMIN inline-правки полей рави (курация Фаза 3.b).
  const userRole = useAuthStore((s) => s.user?.role);
  const [bio, setBio] = useState<NarratorResponseDto | null>(null);
  const [transmitted, setTransmitted] = useState<HadithSummaryDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // Nonce рефетча bio после ADMIN-правки поля — bump инициирует useEffect.
  const [nonce, setNonce] = useState(0);

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
  }, [id, nonce]);

  const rel = bio?.reliabilityGrade ? RELIABILITY_TOKENS[bio.reliabilityGrade] : null;

  // Рефетч bio после ADMIN inline-правки поля рави (курация Фаза 3.b).
  const refetchBio = () => setNonce((n) => n + 1);

  /**
   * Нормализация для дедупа: снять огласовки + сложить буквы (normalizeArabic),
   * затем убрать пунктуацию и свернуть пробелы. Это позволяет сравнивать
   * «الصحابي الجليل حافظ الصحابة» и «الصحابي الجليل ، حافظ الصحابة» как
   * семантически идентичные (арабская запятая — стилистическая, не смысловая).
   */
  function normForDedup(text: string): string {
    return normalizeArabic(text)
      .replace(/[،؛؟٪«»,.;:!?()[\]"'…]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  /**
   * B4: дедуп серого verbatim-бара.
   * Бар несёт gradeText (или reliabilityComment как фолбэк). Если тот же текст
   * уже присутствует в одном из комментариев учёных — он будет показан там с
   * атрибуцией (критик · книга · страница), и дублировать его в баре нет смысла.
   * Сравниваем через normForDedup — снимает огласовки, складывает буквы и убирает
   * пунктуацию, чтобы «... ، ...» и «... ...» считались одним текстом.
   */
  function isBarDuplicated(
    barText: string,
    commentaries: NarratorCommentaryDto[] | null | undefined,
  ): boolean {
    if (!commentaries || commentaries.length === 0) return false;
    const normBar = normForDedup(barText);
    return commentaries.some((c) =>
      c.comments.some((verdict) => {
        const normVerdict = normForDedup(verdict);
        return normVerdict.includes(normBar) || normBar.includes(normVerdict);
      }),
    );
  }

  /**
   * B4: дедуп поля tabaqa.
   * У alminasa поле `level` для сподвижников содержит почётный эпитет
   * («الصحابي الجليل»), а не номер поколения (طبقة). Тот же текст попадает
   * в gradeText («الصحابي الجليل حافظ الصحابة» — расширенная версия того же
   * эпитета). Показывать «Поколение = الصحابي الجليل» вводит в заблуждение.
   * Скрываем tabaqa, если его нормализованная форма содержится в нормализованном
   * gradeText или совпадает с ним — т.е. tabaqa является подстрокой/частью gradeText.
   * Для обычных рави с реальной طبقة («الطبقة الثامنة») gradeText будет иным
   * («ثقة حافظ»), совпадения нет → поле показывается нормально.
   */
  function isTabaqaEpithet(
    tabaqa: string,
    gradeText: string | null | undefined,
  ): boolean {
    if (!gradeText) return false;
    const normTabaqa = normForDedup(tabaqa);
    const normGrade = normForDedup(gradeText);
    return normGrade.includes(normTabaqa);
  }

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
                {/* Степень надёжности — рич-чип; ADMIN-правка (курация 3.b)
                    карандашом рядом (виден и когда grade ещё не выставлен). */}
                <span className="shrink-0">
                  <EditableField
                    entityTable="hd_narrators"
                    entityId={bio.id}
                    fieldName="reliability_grade"
                    value={bio.reliabilityGrade}
                    kind="enum"
                    options={RELIABILITY_OPTIONS}
                    role={userRole}
                    onSaved={refetchBio}
                    label={
                      rel && bio.reliabilityGrade ? (
                        <span
                          className={`rounded-sm px-2 py-1 font-arabic text-sm font-semibold ${rel.chip}`}
                          dir="rtl"
                        >
                          {rel.ar}
                        </span>
                      ) : undefined
                    }
                  />
                </span>
              </div>
              {rel && bio.reliabilityGrade && (
                <div className="mt-1 text-sm font-medium text-ink-600">
                  {t(`hadith.reliability.${bio.reliabilityGrade}` as DictKey)}
                </div>
              )}
            </header>

            {/* B2 (Field Layout Options, Вариант 1 «поля-карточки»): метка и значение
                в общей рамке (gestalt «общая область») — арабское значение больше НЕ
                уплывает от метки в широкой колонке (проблема RU-интерфейса). Карточка
                направление-агностична → симметрично в RU/AR; тёмная тема через
                семантические токены (border-border/bg-sunken/text-ink-*). Длинные
                поля (кунья, жизненный путь) — на всю ширину. Дизайн-референс:
                docs/specs/2026-06-17-hadith-explorer-ux-feedback.md (B2). */}
            <dl className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {bio.kunya && (
                <div className="rounded-xl border border-border bg-sunken px-4 py-3.5 sm:col-span-2">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.kunya')}
                  </dt>
                  <dd className="mt-2 font-arabic text-base leading-relaxed text-ink-800" dir="rtl">
                    <EditableField
                      entityTable="hd_narrators"
                      entityId={bio.id}
                      fieldName="kunya"
                      value={bio.kunya}
                      kind="text"
                      role={userRole}
                      onSaved={refetchBio}
                    />
                  </dd>
                </div>
              )}
              {bio.laqab && (
                <div className="rounded-xl border border-border bg-sunken px-4 py-3.5">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.laqab')}
                  </dt>
                  <dd className="mt-2 font-arabic text-base leading-relaxed text-ink-800" dir="rtl">
                    <EditableField
                      entityTable="hd_narrators"
                      entityId={bio.id}
                      fieldName="laqab"
                      value={bio.laqab}
                      kind="text"
                      role={userRole}
                      onSaved={refetchBio}
                    />
                  </dd>
                </div>
              )}
              {/* M3: табака — фолбэк для отсутствующего generation у alminasa-рави.
                  B4: скрываем если tabaqa — почётный эпитет (содержится в gradeText),
                  что типично для сподвижников (alminasa пишет туда «الصحابي الجليل»). */}
              {bio.tabaqa && !isTabaqaEpithet(bio.tabaqa, bio.gradeText) && (
                <div className="rounded-xl border border-border bg-sunken px-4 py-3.5">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.generation')}
                  </dt>
                  <dd className="mt-2 font-arabic text-base leading-relaxed text-ink-800" dir="auto">
                    <EditableField
                      entityTable="hd_narrators"
                      entityId={bio.id}
                      fieldName="tabaqa"
                      value={bio.tabaqa}
                      kind="text"
                      role={userRole}
                      onSaved={refetchBio}
                    />
                  </dd>
                </div>
              )}
              {(bio.yearDeathHijri != null || bio.bornOnText || bio.diedOnText) && (
                <div className="rounded-xl border border-border bg-sunken px-4 py-3.5">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.years')}
                  </dt>
                  <dd className="mt-2 text-base leading-relaxed text-ink-800" dir="auto">
                    {bio.yearDeathHijri != null ? (
                      <>
                        {bio.yearBirthHijri != null ? `${bio.yearBirthHijri}–` : ''}
                        {bio.yearDeathHijri} {t('hadith.graph.hijri')}
                      </>
                    ) : (
                      [bio.bornOnText, bio.diedOnText].filter(Boolean).join(' — ')
                    )}
                  </dd>
                </div>
              )}
              {(bio.birthplace || bio.primaryResidence || bio.deathPlace) && (
                <div className="rounded-xl border border-border bg-sunken px-4 py-3.5 sm:col-span-2">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                    {t('hadith.narrator.life_path')}
                  </dt>
                  <dd className="mt-2 font-arabic text-base leading-relaxed text-ink-800" dir="auto">
                    {[bio.birthplace, bio.primaryResidence, bio.deathPlace]
                      .filter(Boolean)
                      .join(' → ')}
                  </dd>
                </div>
              )}
            </dl>

            {/* M3: verbatim джарх — gradeText (alminasa), фолбэк reliabilityComment.
                B4: скрываем бар если его текст уже присутствует в секции «Оценки учёных»
                — дублирование без атрибуции хуже, чем атрибутированная цитата там. */}
            {(() => {
              const barText = bio.gradeText ?? bio.reliabilityComment;
              if (!barText) return null;
              if (isBarDuplicated(barText, bio.commentaries)) return null;
              return (
                <div
                  className="mb-8 rounded-md bg-sunken p-3 text-sm leading-relaxed text-ink-700"
                  dir="auto"
                >
                  {/* ADMIN-правка (курация 3.b) редактирует поле grade_text. */}
                  <EditableField
                    entityTable="hd_narrators"
                    entityId={bio.id}
                    fieldName="grade_text"
                    value={bio.gradeText}
                    kind="text"
                    role={userRole}
                    onSaved={refetchBio}
                    label={<span dir="auto">{barText}</span>}
                  />
                </div>
              );
            })()}

            {/* Оценки учёных о передатчике (джарх/таʿдиль) — внешние цитаты из
                риджаль-книг с атрибуцией (критик · книга · стр.). */}
            {bio.commentaries && bio.commentaries.length > 0 && (
              <section className="mb-8">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                  {t('hadith.narrator.commentaries.title')}
                </h2>
                <NarratorCommentaryList commentaries={bio.commentaries} />
              </section>
            )}

            {/* M3: сеть передатчиков — ученики / учителя из relations. */}
            {bio.relations && bio.relations.length > 0 && (
              <section className="mb-8">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-ink-500">
                  {t('hadith.narrator.network')}
                </h2>
                <ul className="space-y-1.5">
                  {bio.relations.map((rel, i) => {
                    const label = rel.relatedName ?? '—';
                    const roleLabel = rel.role ? `${rel.role}` : null;
                    const meta = rel.cnt > 0 ? ` · ${rel.cnt}` : '';
                    return (
                      // У relation нет id; список неизменяемый (detail-снимок) → index ок.
                      <li key={`${rel.relatedNarratorId ?? rel.relatedName ?? 'r'}-${i}`}>
                        {rel.relatedNarratorId ? (
                          <Link
                            to={`/hadith/narrators/${rel.relatedNarratorId}`}
                            className="inline-flex flex-wrap items-baseline gap-x-2 rounded-sm text-sm text-accent-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
                          >
                            <span className="font-arabic" dir="rtl">
                              {label}
                            </span>
                            {roleLabel && (
                              <span className="text-xs text-ink-400">
                                {roleLabel}
                                {meta}
                              </span>
                            )}
                          </Link>
                        ) : (
                          <span className="inline-flex flex-wrap items-baseline gap-x-2 text-sm text-ink-700">
                            <span className="font-arabic" dir="rtl">
                              {label}
                            </span>
                            {roleLabel && (
                              <span className="text-xs text-ink-400">
                                {roleLabel}
                                {meta}
                              </span>
                            )}
                          </span>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </section>
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
                            <span
                              className={`rounded-sm px-1.5 py-0.5 font-semibold uppercase ${hadithStatusClass(h.status)}`}
                              title={
                                h.status && HADITH_STATUS_EXPLAIN[h.status]
                                  ? t(HADITH_STATUS_EXPLAIN[h.status] as DictKey)
                                  : undefined
                              }
                            >
                              {h.status && HADITH_STATUS_SHORT[h.status]
                                ? t(HADITH_STATUS_SHORT[h.status] as DictKey)
                                : h.status}
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
