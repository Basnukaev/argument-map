import { useState } from 'react';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { apiGetRaw, ApiError, formatApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type SiblingMatnDto = components['schemas']['SiblingMatnDto'];

/**
 * Одна карточка параллельной передачи: шапка (сборник · №N) + mono externalId
 * + ссылка «→ Перейти» + арабский текст матна (RTL naskh).
 */
function SiblingCard({ s }: { s: SiblingMatnDto }) {
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
      {s.textAr && (
        <p className="mt-3 font-arabic text-lg leading-loose text-ink-900" dir="rtl">
          {s.textAr}
        </p>
      )}
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
}: {
  hadithId: string;
  /** Число resolved crossrefs (передаётся из страницы, уже посчитано). */
  resolvedTuruqCount: number;
}) {
  const t = useT();
  const [siblings, setSiblings] = useState<SiblingMatnDto[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShow = () => {
    if (loading || siblings !== null) return;
    setLoading(true);
    setError(null);
    apiGetRaw<SiblingMatnDto[]>(`/api/v1/hadith/hadiths/${hadithId}/sibling-matns`)
      .then((data) => setSiblings(data))
      .catch((e: unknown) => {
        setError(formatApiError(e, t('hadith.siblings.error')));
        if (!(e instanceof ApiError)) return;
      })
      .finally(() => setLoading(false));
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

  // Карточки.
  return (
    <ul className="space-y-3">
      {siblings.map((s) => (
        <li key={s.hadithId ?? s.externalId ?? String(s.printedNumber)}>
          <SiblingCard s={s} />
        </li>
      ))}
    </ul>
  );
}

export default SiblingMatns;
