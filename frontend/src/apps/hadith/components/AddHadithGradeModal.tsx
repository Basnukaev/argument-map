import { useEffect, useState } from 'react';
import { Check, Loader2, Search } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import Select from '@/shared/components/ui/Select';
import { apiGetRaw, apiPostRaw, formatApiError, ApiError } from '@/shared/api/client';
import { useT, hasArabicScript, type DictKey } from '@/shared/i18n';
import { toast } from '@/shared/stores/toastStore';
import type {
  AuthorityResponseDto,
  HadithGradeValue,
  Paged,
} from '@/apps/hadith/types';

interface Props {
  hadithId: string;
  onClose: () => void;
  /** Вызывается после успешного POST — страница рефетчит detail. */
  onCreated: () => void;
}

/** 4 enum-оценки (зеркало backend HadithGradeValue) — порядок sahih→maudu. */
const GRADE_VALUES: readonly HadithGradeValue[] = ['SAHIH', 'HASAN', 'DAIF', 'MAUDU'];

/**
 * Модалка «Добавить оценку учёного» на хадис (ADR-062 Option B). Форма:
 * autocomplete учёного (SCHOLAR из справочника authorities) + enum-оценка +
 * ссылка + комментарий. POST `/api/v1/hadith/hadiths/{id}/grades` — мост лениво
 * создаёт source хадиса и пишет в `hadith_grades`. Идиома проекта — рендерится
 * условно (`{open && <AddHadithGradeModal/>}`), чистый state на каждом открытии.
 */
function AddHadithGradeModal({ hadithId, onClose, onCreated }: Props) {
  const t = useT();

  // Autocomplete учёного: query → debounced fetch → выбор.
  const [scholarQuery, setScholarQuery] = useState('');
  const [scholarResults, setScholarResults] = useState<AuthorityResponseDto[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedScholar, setSelectedScholar] = useState<AuthorityResponseDto | null>(null);

  const [grade, setGrade] = useState<HadithGradeValue>('SAHIH');
  const [citation, setCitation] = useState('');
  const [note, setNote] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Debounced поиск учёных (250 мс). Фильтр type=SCHOLAR — клиентский
  // (authorities-каталог отдаёт все типы; оценивать хадис может только учёный,
  // иначе backend 400). setState только внутри async-колбэков (после debounce/
  // fetch), не в теле эффекта — идиома проекта (react-hooks/set-state-in-effect;
  // см. useApiQuery). Сброс результатов для короткого/выбранного query — в
  // обработчиках ввода (handle*Scholar), не здесь.
  useEffect(() => {
    const q = scholarQuery.trim();
    // Нечего искать (выбран scholar либо query короткий) — эффект бездействует;
    // очистка результатов уже сделана в onChange/handlePickScholar.
    if (selectedScholar || q.length < 2) {
      return;
    }
    const ctl = new AbortController();
    const timer = setTimeout(() => {
      setSearching(true);
      apiGetRaw<Paged<AuthorityResponseDto>>(
        `/api/v1/authorities?q=${encodeURIComponent(q)}&size=10`,
        { signal: ctl.signal },
      )
        .then((paged) => {
          if (ctl.signal.aborted) return;
          setScholarResults((paged.items ?? []).filter((a) => a.type === 'SCHOLAR'));
          setSearching(false);
        })
        .catch(() => {
          if (ctl.signal.aborted) return;
          setScholarResults([]);
          setSearching(false);
        });
    }, 250);
    return () => {
      ctl.abort();
      clearTimeout(timer);
    };
  }, [scholarQuery, selectedScholar]);

  function handleQueryChange(value: string) {
    if (selectedScholar) setSelectedScholar(null);
    setScholarQuery(value);
    // Короткий query → сразу прячем устаревшие результаты + спиннер (event
    // handler, не эффект). Длинный — эффект запустит debounced fetch.
    if (value.trim().length < 2) {
      setScholarResults([]);
      setSearching(false);
    }
  }

  function handlePickScholar(scholar: AuthorityResponseDto) {
    setSelectedScholar(scholar);
    setScholarQuery(scholar.name);
    setScholarResults([]);
    setSearching(false);
  }

  function handleClearScholar() {
    setSelectedScholar(null);
    setScholarQuery('');
    setScholarResults([]);
    setSearching(false);
  }

  function handleSubmit() {
    if (!selectedScholar || submitting) return;
    setSubmitting(true);
    setError(null);
    apiPostRaw(`/api/v1/hadith/hadiths/${hadithId}/grades`, {
      scholarId: selectedScholar.id,
      grade,
      gradeCitation: citation.trim() || null,
      comment: note.trim() || null,
    })
      .then(() => {
        toast.success(t('hadith.grade.success'));
        onCreated();
        onClose();
      })
      .catch((e: unknown) => {
        // 409 — этот учёный уже оценил хадис: дружелюбное сообщение.
        if (e instanceof ApiError && e.is('hadith-grade-duplicate')) {
          setError(t('hadith.grade.error_duplicate'));
        } else {
          setError(formatApiError(e, t('hadith.grade.error')));
        }
        setSubmitting(false);
      });
  }

  const gradeOptions = GRADE_VALUES.map((g) => ({
    value: g,
    label: t(`hadith.grade.value.${g}` as DictKey),
  }));

  return (
    <Modal
      open
      onClose={onClose}
      title={t('hadith.grade.add_title')}
      subtitle={t('hadith.grade.add_subtitle')}
    >
      <div className="space-y-4">
        <Field
          label={t('hadith.grade.field.scholar')}
          hint={t('hadith.grade.field.scholar_hint')}
          required
        >
          <div className="relative">
            <Field.Input
              leftIcon={<Search size={14} className="text-ink-400" aria-hidden />}
              value={scholarQuery}
              placeholder={t('hadith.grade.field.scholar_placeholder')}
              dir="auto"
              className={hasArabicScript(scholarQuery) ? 'font-arabic' : ''}
              onChange={(e) => handleQueryChange(e.target.value)}
            />
            {selectedScholar ? (
              <button
                type="button"
                onClick={handleClearScholar}
                className="absolute end-2 top-1/2 -translate-y-1/2 inline-flex items-center gap-1 text-xs font-medium text-accent-600 hover:text-accent-700"
              >
                <Check size={12} aria-hidden />
                {selectedScholar.deathYearHijri != null
                  ? t('hadith.detail.ruling.died').replace(
                      '{year}',
                      String(selectedScholar.deathYearHijri),
                    )
                  : null}
              </button>
            ) : (
              searching && (
                <Loader2
                  size={14}
                  className="absolute end-3 top-1/2 -translate-y-1/2 animate-spin text-ink-400"
                  aria-hidden
                />
              )
            )}
          </div>

          {/* Результаты поиска — список выбора (скрыт после выбора). */}
          {!selectedScholar && scholarQuery.trim().length >= 2 && (
            <div className="mt-1.5 max-h-48 overflow-y-auto rounded-sm border border-border bg-elevated">
              {searching && scholarResults.length === 0 ? (
                <div className="px-3 py-2 text-xs text-ink-500">
                  {t('hadith.grade.scholar_searching')}
                </div>
              ) : scholarResults.length === 0 ? (
                <div className="px-3 py-2 text-xs text-ink-500">
                  {t('hadith.grade.scholar_search_empty')}
                </div>
              ) : (
                <ul className="divide-y divide-border">
                  {scholarResults.map((a) => (
                    <li key={a.id}>
                      <button
                        type="button"
                        onClick={() => handlePickScholar(a)}
                        className="flex w-full flex-col items-start gap-0.5 px-3 py-2 text-start hover:bg-accent-50"
                      >
                        <span
                          className={`text-sm font-medium text-ink-900 ${
                            hasArabicScript(a.name) ? 'font-arabic' : ''
                          }`}
                          dir="auto"
                        >
                          {a.name}
                        </span>
                        {(a.fullName || a.deathYearHijri != null) && (
                          <span className="text-xs text-ink-500" dir="auto">
                            {a.fullName ?? ''}
                            {a.deathYearHijri != null
                              ? ` · ${t('hadith.detail.ruling.died').replace('{year}', String(a.deathYearHijri))}`
                              : ''}
                          </span>
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </Field>

        <Field label={t('hadith.grade.field.grade')} required>
          <Select
            value={grade}
            onChange={(v) => setGrade(v as HadithGradeValue)}
            options={gradeOptions}
            ariaLabel={t('hadith.grade.field.grade')}
            className="w-full"
          />
        </Field>

        <Field
          label={t('hadith.grade.field.citation')}
          hint={t('hadith.grade.field.citation_hint')}
        >
          <Field.Input
            value={citation}
            dir="auto"
            onChange={(e) => setCitation(e.target.value)}
          />
        </Field>

        <Field
          label={t('hadith.grade.field.note')}
          hint={t('hadith.grade.field.note_hint')}
        >
          <Field.Textarea
            rows={3}
            value={note}
            dir="auto"
            onChange={(e) => setNote(e.target.value)}
          />
        </Field>

        {error && <div className="text-xs text-err-500">{error}</div>}

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={!selectedScholar || submitting}
            icon={submitting ? Loader2 : undefined}
            className={submitting ? '[&>svg]:animate-spin' : ''}
          >
            {t('hadith.grade.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default AddHadithGradeModal;
