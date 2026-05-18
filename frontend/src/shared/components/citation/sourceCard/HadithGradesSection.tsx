import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, Pencil, Plus, Trash2 } from 'lucide-react';
import {
  apiDeleteRaw,
  apiGetRaw,
  apiPatchRaw,
  apiPostRaw,
  ApiError,
  formatApiError,
} from '@/shared/api/client';
import { hasArabicScript, useT } from '@/shared/i18n';
import { useAuthStore } from '@/shared/stores/authStore';
import { toast } from '@/shared/stores/toastStore';
import Button from '@/shared/components/ui/Button';
import Modal from '@/shared/components/ui/Modal';
import Field from '@/shared/components/ui/Field';
import type { components } from '@/shared/api/types';

type HadithGradeDto = components['schemas']['HadithGradeResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];
type GradeValue = 'SAHIH' | 'HASAN' | 'DAIF' | 'MAUDU';

const GRADES: ReadonlyArray<GradeValue> = ['SAHIH', 'HASAN', 'DAIF', 'MAUDU'];

/**
 * Цветовые токены для badge оценки. Используем фиксированную палитру
 * (emerald / blue / orange / rose), а не designTokens - смысловые
 * категории грейдов уникальны для этого раздела
 */
const GRADE_BADGE: Record<GradeValue, string> = {
  SAHIH:
    'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-500/15 dark:text-emerald-300 dark:border-emerald-500/30',
  HASAN:
    'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-500/15 dark:text-blue-300 dark:border-blue-500/30',
  DAIF:
    'bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-500/15 dark:text-orange-300 dark:border-orange-500/30',
  MAUDU:
    'bg-rose-100 text-rose-900 border-rose-300 dark:bg-rose-500/20 dark:text-rose-300 dark:border-rose-500/40',
};

interface Props {
  sourceId: string;
  sourceType: string | undefined;
}

/**
 * Multi-grading секция учёных для HADITH source. Conditional - рендерится
 * только если sourceType === 'HADITH'. Collapsible header с count, при
 * раскрытии - list оценок + кнопка добавления. Add/Edit через Modal
 * (Scholar autocomplete + Grade radio + citation/comment fields).
 *
 * Backend - `/api/v1/sources/{sourceId}/grades` (GET/POST), а так же
 * `/api/v1/sources/grades/{gradeId}` (PATCH/DELETE). Edit/Delete видны
 * только автору либо ADMIN (frontend hide; backend - source of truth
 * через 403 forbidden-hadith-grade-write)
 */
export function HadithGradesSection({ sourceId, sourceType }: Props) {
  const t = useT();
  const user = useAuthStore((s) => s.user);
  const [open, setOpen] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [grades, setGrades] = useState<HadithGradeDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<HadithGradeDto | null>(null);

  const isHadith = sourceType === 'HADITH';

  const fetchGrades = useCallback(
    (signal?: AbortSignal) => {
      // setLoading/setError завернуты в Promise.resolve() чтобы выполниться
      // в microtask, не синхронно в теле эффекта (react-hooks/set-state-in-effect)
      return Promise.resolve()
        .then(() => {
          if (signal?.aborted) return;
          setLoading(true);
          setError(null);
        })
        .then(() =>
          apiGetRaw<HadithGradeDto[]>(`/api/v1/sources/${sourceId}/grades`, { signal }),
        )
        .then((data) => {
          if (signal?.aborted) return;
          setGrades(data);
          setLoaded(true);
        })
        .catch((e: unknown) => {
          if (signal?.aborted) return;
          setError(formatApiError(e, t('hadith.grades.error_load')));
        })
        .finally(() => {
          if (!signal?.aborted) setLoading(false);
        });
    },
    [sourceId, t],
  );

  useEffect(() => {
    if (!open || !isHadith) return;
    const controller = new AbortController();
    void fetchGrades(controller.signal);
    return () => controller.abort();
  }, [open, isHadith, fetchGrades]);

  if (!isHadith) return null;

  const count = loaded ? grades.length : null;

  const onAdd = () => {
    setEditing(null);
    setEditorOpen(true);
  };
  const onEdit = (g: HadithGradeDto) => {
    setEditing(g);
    setEditorOpen(true);
  };

  const onDelete = async (g: HadithGradeDto) => {
    if (!g.id) return;
    const scholarLabel = g.scholarFullName ?? g.scholarName ?? '';
    if (!window.confirm(t('hadith.grades.confirm_delete').replace('{scholar}', scholarLabel))) {
      return;
    }
    try {
      await apiDeleteRaw(`/api/v1/sources/grades/${g.id}`);
      toast.success(t('hadith.grades.delete_success'));
      void fetchGrades();
    } catch (e: unknown) {
      if (e instanceof ApiError && e.is('forbidden-hadith-grade-write')) {
        toast.error(t('hadith.grades.error_forbidden'));
      } else {
        toast.error(formatApiError(e, t('hadith.grades.error_delete')));
      }
    }
  };

  const onEditorClose = () => {
    setEditorOpen(false);
    setEditing(null);
  };

  const onEditorSuccess = () => {
    onEditorClose();
    void fetchGrades();
  };

  return (
    <div className="border-t border-border" data-testid="hadith-grades-section">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 rounded py-2.5 text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 focus-visible:ring-offset-2"
      >
        <span className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-400">
          {t('hadith.grades.title')}
          {count != null && (
            <span className="rounded-full bg-ink-100 px-1.5 py-0.5 text-xs font-semibold tracking-normal text-ink-600 normal-case">
              {count}
            </span>
          )}
        </span>
        <ChevronDown
          aria-hidden
          size={13}
          className={`text-ink-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div className="pb-3" data-testid="hadith-grades-body">
          {loading && !loaded && (
            <p className="px-1 py-2 text-xs text-ink-500">{t('hadith.grades.loading')}</p>
          )}
          {error && (
            <p className="px-1 py-2 text-xs text-err-700">{error}</p>
          )}
          {loaded && !error && grades.length === 0 && (
            <div className="flex flex-col items-start gap-2 px-1 py-2">
              <p className="text-xs italic text-ink-500">{t('hadith.grades.empty_state')}</p>
              <Button
                size="sm"
                variant="secondary"
                icon={Plus}
                onClick={onAdd}
                data-testid="hadith-grades-add-first"
              >
                {t('hadith.grades.add_first')}
              </Button>
            </div>
          )}

          {loaded && grades.length > 0 && (
            <ul className="flex flex-col divide-y divide-border/60 border-y border-border/60">
              {grades.map((g) => (
                <li key={g.id ?? `${g.scholarId}-${g.createdAt}`}>
                  <GradeRow
                    grade={g}
                    canEdit={
                      user != null && (user.id === g.createdBy || user.role === 'ADMIN')
                    }
                    onEdit={() => onEdit(g)}
                    onDelete={() => void onDelete(g)}
                  />
                </li>
              ))}
            </ul>
          )}

          {loaded && grades.length > 0 && (
            <div className="mt-3 px-1">
              <Button
                size="sm"
                variant="secondary"
                icon={Plus}
                onClick={onAdd}
                data-testid="hadith-grades-add"
              >
                {t('hadith.grades.add_button')}
              </Button>
            </div>
          )}
        </div>
      )}

      {editorOpen && (
        <HadithGradeEditor
          sourceId={sourceId}
          editing={editing}
          onClose={onEditorClose}
          onSuccess={onEditorSuccess}
        />
      )}
    </div>
  );
}

interface RowProps {
  grade: HadithGradeDto;
  canEdit: boolean;
  onEdit: () => void;
  onDelete: () => void;
}

function GradeRow({ grade, canEdit, onEdit, onDelete }: RowProps) {
  const t = useT();
  const [expanded, setExpanded] = useState(false);
  const value = grade.grade as GradeValue | undefined;
  const scholarName = grade.scholarFullName ?? grade.scholarName ?? '—';
  const scholarIsAr = hasArabicScript(scholarName);
  const comment = grade.comment ?? null;
  const longComment = (comment ?? '').length > 80;

  return (
    <div className="flex flex-col gap-1.5 px-1 py-2.5">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline gap-2">
            <span
              dir="auto"
              lang={scholarIsAr ? 'ar' : undefined}
              className={
                scholarIsAr
                  ? 'font-naskh text-sm font-semibold text-ink-900'
                  : 'text-sm font-semibold text-ink-900'
              }
            >
              {scholarName}
            </span>
            {grade.scholarDeathYearHijri != null && (
              <span className="text-xs text-ink-500">
                (<bdi>{grade.scholarDeathYearHijri} هـ</bdi>)
              </span>
            )}
          </div>
          {value && (
            <span
              className={`mt-1 inline-block rounded-sm border px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wide ${GRADE_BADGE[value]}`}
              data-testid={`hadith-grade-badge-${value}`}
            >
              {t(`hadith.grades.grade.${value}`)}
            </span>
          )}
        </div>
        {canEdit && (
          <div className="flex flex-none items-center gap-1">
            <button
              type="button"
              onClick={onEdit}
              aria-label={t('hadith.grades.edit')}
              className="rounded p-1 text-ink-500 transition-colors hover:bg-ink-100 hover:text-ink-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
              data-testid="hadith-grade-edit"
            >
              <Pencil size={13} aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={onDelete}
              aria-label={t('hadith.grades.delete')}
              className="rounded p-1 text-ink-500 transition-colors hover:bg-err-50 hover:text-err-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-err-500"
              data-testid="hadith-grade-delete"
            >
              <Trash2 size={13} aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
      {grade.gradeCitation && (
        <p
          dir="auto"
          className="text-xs italic text-ink-600"
          data-testid="hadith-grade-citation"
        >
          {grade.gradeCitation}
        </p>
      )}
      {comment && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="text-start text-xs leading-snug text-ink-700 hover:text-ink-900 focus:outline-none focus-visible:underline"
          dir="auto"
          aria-expanded={expanded}
          data-testid="hadith-grade-comment"
        >
          {expanded || !longComment ? comment : `${comment.slice(0, 80)}…`}
        </button>
      )}
    </div>
  );
}

interface EditorProps {
  sourceId: string;
  editing: HadithGradeDto | null;
  onClose: () => void;
  onSuccess: () => void;
}

interface ScholarSuggestion {
  id: string;
  name: string;
  hint?: string;
}

function HadithGradeEditor({ sourceId, editing, onClose, onSuccess }: EditorProps) {
  const t = useT();
  const isEdit = editing != null;
  const [scholarQuery, setScholarQuery] = useState(
    editing?.scholarFullName ?? editing?.scholarName ?? '',
  );
  const [scholarId, setScholarId] = useState<string | null>(editing?.scholarId ?? null);
  const [grade, setGrade] = useState<GradeValue>(
    (editing?.grade as GradeValue | undefined) ?? 'SAHIH',
  );
  const [gradeCitation, setGradeCitation] = useState(editing?.gradeCitation ?? '');
  const [comment, setComment] = useState(editing?.comment ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitting) return;
    if (!isEdit && !scholarId) {
      setFormError(t('hadith.grades.scholar_required'));
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      if (isEdit && editing?.id) {
        await apiPatchRaw<HadithGradeDto>(`/api/v1/sources/grades/${editing.id}`, {
          grade,
          gradeCitation: gradeCitation.trim() ? gradeCitation.trim() : undefined,
          comment: comment.trim() ? comment.trim() : undefined,
        });
        toast.success(t('hadith.grades.update_success'));
      } else {
        await apiPostRaw<HadithGradeDto>(`/api/v1/sources/${sourceId}/grades`, {
          scholarId,
          grade,
          gradeCitation: gradeCitation.trim() ? gradeCitation.trim() : undefined,
          comment: comment.trim() ? comment.trim() : undefined,
        });
        toast.success(t('hadith.grades.add_success'));
      }
      onSuccess();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.is('hadith-grade-duplicate')) {
          setFormError(t('hadith.grades.error_duplicate'));
        } else if (err.is('invalid-hadith-grade')) {
          setFormError(t('hadith.grades.error_invalid_hadith'));
        } else if (err.is('forbidden-hadith-grade-write')) {
          setFormError(t('hadith.grades.error_forbidden'));
        } else {
          setFormError(formatApiError(err, t('hadith.grades.error_save')));
        }
      } else {
        setFormError(formatApiError(err, t('hadith.grades.error_save')));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      onClose={submitting ? () => undefined : onClose}
      title={isEdit ? t('hadith.grades.edit_title') : t('hadith.grades.add_title')}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {isEdit ? (
          <div className="text-sm text-ink-700">
            <span className="text-xs text-ink-500">{t('hadith.grades.scholar')}: </span>
            <span dir="auto" className="font-semibold">
              {editing?.scholarFullName ?? editing?.scholarName ?? '—'}
            </span>
          </div>
        ) : (
          <ScholarAutocomplete
            value={scholarQuery}
            onChange={(v, id) => {
              setScholarQuery(v);
              setScholarId(id);
            }}
          />
        )}

        <Field label={t('hadith.grades.grade.label')} required>
          <div className="flex flex-wrap gap-2" role="radiogroup">
            {GRADES.map((g) => (
              <label
                key={g}
                className={`flex cursor-pointer items-center gap-1.5 rounded-sm border px-2.5 py-1.5 text-xs font-semibold transition-colors ${
                  grade === g
                    ? GRADE_BADGE[g]
                    : 'border-ink-200 bg-elevated text-ink-700 hover:border-ink-300'
                }`}
              >
                <input
                  type="radio"
                  name="hadith-grade"
                  value={g}
                  checked={grade === g}
                  onChange={() => setGrade(g)}
                  className="sr-only"
                />
                {t(`hadith.grades.grade.${g}`)}
              </label>
            ))}
          </div>
        </Field>

        <Field
          label={t('hadith.grades.citation_label')}
          hint={t('hadith.grades.citation_hint')}
        >
          <Field.Input
            value={gradeCitation}
            onChange={(e) => setGradeCitation(e.target.value)}
            maxLength={500}
            dir="auto"
          />
        </Field>

        <Field label={t('hadith.grades.comment_label')}>
          <Field.Textarea
            rows={3}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={5000}
            dir="auto"
          />
        </Field>

        {formError && (
          <div className="rounded-sm border border-err-300 bg-err-50 px-3 py-2 text-xs text-err-700">
            {formError}
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            disabled={submitting}
          >
            {t('hadith.grades.cancel')}
          </Button>
          <Button
            type="submit"
            variant="primary"
            disabled={submitting}
            data-testid="hadith-grades-submit"
          >
            {submitting ? t('hadith.grades.submitting') : t('hadith.grades.submit')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

interface ScholarAcProps {
  value: string;
  onChange: (query: string, scholarId: string | null) => void;
}

function ScholarAutocomplete({ value, onChange }: ScholarAcProps) {
  const t = useT();
  const [suggestions, setSuggestions] = useState<ScholarSuggestion[]>([]);
  const [openList, setOpenList] = useState(false);

  useEffect(() => {
    // Stale suggestions из предыдущего длинного query - не очищаем здесь
    // (sync setState в effect триггерит react-hooks/set-state-in-effect),
    // вместо этого `showList` ниже учитывает minimum length value и
    // скрывает stale items когда пользователь стёр input до 1 символа
    if (!value || value.trim().length < 2) return undefined;
    const controller = new AbortController();
    const handle = window.setTimeout(async () => {
      try {
        const res = await apiGetRaw<components['schemas']['PagedResponseAuthorityResponse']>(
          `/api/v1/authorities?q=${encodeURIComponent(value.trim())}&size=10`,
          { signal: controller.signal },
        );
        if (controller.signal.aborted) return;
        const items: ScholarSuggestion[] = (res.items ?? [])
          .filter((a): a is AuthorityDto & { id: string } => Boolean(a.id))
          .map((a) => ({
            id: a.id,
            name: a.fullName ?? a.name ?? '',
            hint:
              a.deathYearHijri != null ? `${a.deathYearHijri} هـ` : undefined,
          }));
        setSuggestions(items);
      } catch {
        if (!controller.signal.aborted) setSuggestions([]);
      }
    }, 250);
    return () => {
      controller.abort();
      window.clearTimeout(handle);
    };
  }, [value]);

  // Computed show-flag учитывает minimum length value, не позволяя
  // показать stale suggestions после очистки input до 1 символа
  const showList = openList && value.trim().length >= 2 && suggestions.length > 0;

  return (
    <Field label={t('hadith.grades.scholar')} required hint={t('hadith.grades.scholar_hint')}>
      <div className="relative">
        <Field.Input
          value={value}
          placeholder={t('hadith.grades.scholar_placeholder')}
          onChange={(e) => {
            onChange(e.target.value, null);
            setOpenList(true);
          }}
          onFocus={() => setOpenList(true)}
          onBlur={() => {
            window.setTimeout(() => setOpenList(false), 150);
          }}
          dir="auto"
        />
        {showList && (
          <ul
            role="listbox"
            className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-sm border border-ink-200 bg-elevated shadow-sh3"
            onMouseDown={(e) => e.preventDefault()}
          >
            {suggestions.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  className="block w-full px-3 py-1.5 text-start text-sm text-ink-800 hover:bg-accent-50 hover:text-accent-700"
                  onClick={() => {
                    onChange(s.name, s.id);
                    setOpenList(false);
                  }}
                  dir="auto"
                >
                  <span>{s.name}</span>
                  {s.hint && <span className="ms-2 text-xs text-ink-500">{s.hint}</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Field>
  );
}

export default HadithGradesSection;
