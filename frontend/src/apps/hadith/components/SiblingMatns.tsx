import { useState } from 'react';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import { siblingDiff } from '@/apps/hadith/utils/matnDiff';
import type { components } from '@/shared/api/types';

type SiblingMatnDto = components['schemas']['SiblingMatnDto'];

/**
 * Текст матна параллельной передачи. Если передан текущий матн (`currentMatn`)
 * — рендерится пословный diff: слова, расходящиеся с текущим текстом,
 * подсвечены амбром; совпадающие — обычным цветом. Без `currentMatn`
 * (graceful) — текст as-is. RTL naskh.
 */
function SiblingText({ textAr, currentMatn }: { textAr: string; currentMatn?: string }) {
  // diff дешёвый (десятки слов на карточку) — без memo (YAGNI, как в MatnDiff).
  // Пустой currentMatn → siblingDiff пометит весь текст different; чтобы не
  // красить карточку целиком когда сравнивать не с чем, рендерим plain.
  if (!currentMatn) {
    return (
      <p className="mt-3 font-arabic text-lg leading-loose text-ink-900" dir="rtl">
        {textAr}
      </p>
    );
  }
  const segments = siblingDiff(currentMatn, textAr);
  return (
    <p className="mt-3 font-arabic text-lg leading-loose text-ink-900" dir="rtl">
      {segments.map((seg, idx) => (
        // key с index намеренно: производный append-only список без
        // естественного id (слова повторяются), как в MatnDiff.
        <span
          key={`${idx}-${seg.text}`}
          className={seg.different ? 'rounded-sm bg-amber-100 px-0.5 text-amber-900' : undefined}
        >
          {seg.text}{' '}
        </span>
      ))}
    </p>
  );
}

/**
 * Одна карточка параллельной передачи: шапка (сборник · №N) + mono externalId
 * + ссылка «→ Перейти» + арабский текст матна с подсветкой расхождений с
 * текущим текстом.
 */
function SiblingCard({ s, currentMatn }: { s: SiblingMatnDto; currentMatn?: string }) {
  const t = useT();
  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
        {s.collectionNameRu && (
          <span className="font-medium text-ink-700" dir="auto">
            {s.collectionNameRu}
          </span>
        )}
        {s.printedNumber != null && (
          <span className="font-mono">№{s.printedNumber}</span>
        )}
        {s.externalId && (
          <span className="font-mono text-[11px] text-ink-400">{s.externalId}</span>
        )}
        {s.hadithId && (
          <Link
            to={`/hadith/hadiths/${s.hadithId}`}
            className="ms-auto inline-flex items-center gap-1 rounded-sm text-accent-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
          >
            {t('hadith.siblings.goto')}
          </Link>
        )}
      </div>
      {s.textAr && <SiblingText textAr={s.textAr} currentMatn={currentMatn} />}
    </Card>
  );
}

/**
 * Ленивый блок параллельных передач. До клика — кнопка с числом передач
 * (resolvedTuruqCount). После клика — fetch sibling-matns, затем карточки
 * или inline-сообщение об ошибке / пустом ответе. Кнопка исчезает после
 * успешной загрузки.
 */
function SiblingMatns({
  hadithId,
  resolvedTuruqCount,
  currentMatn,
}: {
  hadithId: string;
  /** Число resolved crossrefs (передаётся из страницы, уже посчитано). */
  resolvedTuruqCount: number;
  /**
   * Текущий (основной) матн хадиса — база для подсветки расхождений в sibling'ах.
   * Опционален: без него карточки рендерят текст без diff (graceful).
   */
  currentMatn?: string;
}) {
  const t = useT();
  const [siblings, setSiblings] = useState<SiblingMatnDto[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // БЕЗ unmounted-гарда (откат review-минора С59): React 18+ сам глушит
  // setState после unmount, а ref-гард ломался в StrictMode навсегда
  // (cleanup при dev-remount, флаг не сбрасывался → вечный спиннер).

  const handleShow = () => {
    if (loading || siblings !== null) return;
    setLoading(true);
    setError(null);
    apiGetRaw<SiblingMatnDto[]>(`/api/v1/hadith/hadiths/${hadithId}/sibling-matns`)
      .then((data) => {
        setSiblings(data);
      })
      .catch((e: unknown) => {
        setError(formatApiError(e, t('hadith.siblings.error')));
      })
      .finally(() => {
        setLoading(false);
      });
  };

  // Кнопка: показывается пока siblings не загружены (null).
  if (siblings === null) {
    return (
      <div>
        <button
          type="button"
          onClick={handleShow}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-ink-700 hover:bg-ink-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading && <Loader2 size={14} className="animate-spin" aria-hidden />}
          {t('hadith.siblings.show_btn').replace('{count}', String(resolvedTuruqCount))}
        </button>
        {error && (
          <p className="mt-2 text-sm text-err-600">{error}</p>
        )}
      </div>
    );
  }

  // Загружено, но пусто.
  if (siblings.length === 0) {
    return (
      <p className="text-sm text-ink-500">{t('hadith.siblings.empty')}</p>
    );
  }

  // Карточки. Подзаголовок поясняет назначение секции (матны той же традиции
  // из других сборников) + что подсвечены расхождения с текущим текстом.
  return (
    <div>
      <p className="mb-3 max-w-2xl text-sm leading-snug text-ink-500">
        {t('hadith.siblings.subtitle')}{' '}
        <span className="inline-flex items-center gap-1 align-baseline">
          <span className="inline-block h-3 w-3 rounded-sm bg-amber-100" aria-hidden />
          {t('hadith.siblings.diff_legend')}
        </span>
      </p>
      <ul className="space-y-3">
        {siblings.map((s) => (
          <li key={s.hadithId ?? s.externalId ?? String(s.printedNumber)}>
            <SiblingCard s={s} currentMatn={currentMatn} />
          </li>
        ))}
      </ul>
    </div>
  );
}

export default SiblingMatns;
