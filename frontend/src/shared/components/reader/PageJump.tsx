import { useState } from 'react';
import Select, { type SelectOption } from '@/shared/components/ui/Select';
import { isArabicText } from '@/shared/components/reader/utils';
import { useT } from '@/shared/i18n';

interface Props {
  currentPage: number;
  totalPages: number;
  currentPrintedPage: string | null;
  currentPart: string | null;
  onJump: (page: number) => void;
  /**
   * Уникальные значения `part` всех страниц книги (entries без NULL).
   * Если >1 - показываем dropdown селектор томов в shamela-style. Если
   * 0/1 - dropdown скрыт (нет смысла переключать).
   */
  availableParts?: string[];
  /**
   * Callback при смене tom'а - родитель должен найти первую internal
   * page с этим part и navigate туда (state.pages.find(p => p.part === ...))
   */
  onPartChange?: (part: string) => void;
  /**
   * Callback при вводе printedPage маркера. Родитель должен найти страницу
   * с (currentPart, printedPage) и navigate туда. Если не найдено -
   * можно вернуть к currentPage.
   */
  onPrintedPageJump?: (printedPage: string) => void;
}

/**
 * Двойной jump-UI (как в shamela):
 * 1. Input для прямого перехода к internal pageNumber (counter 1..N
 *    последовательный через всю книгу).
 * 2. Editable shamela-блок (source-first, ADR-021): dropdown для
 *    `part` (том) + input для `printedPage` (маркер реального издания).
 *    Юзер вводит "Том 3 Стр 39" и попадает на ту же страницу что в
 *    бумажной книге - не привязываясь к нашему internal counter.
 *
 * Sync с внешним currentPage - через key-prop в родителе (PageJump
 * remount'ится с новым initial state). Идиома проекта (см. memory
 * feedback_react_key_remount).
 */
function PageJump({
  currentPage,
  totalPages,
  currentPrintedPage,
  currentPart,
  onJump,
  availableParts = [],
  onPartChange,
  onPrintedPageJump,
}: Props) {
  const t = useT();
  const [draft, setDraft] = useState<string>(String(currentPage));
  const [printedDraft, setPrintedDraft] = useState<string>(currentPrintedPage ?? '');

  const submit = () => {
    const parsed = parseInt(draft, 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
      setDraft(String(currentPage));
      return;
    }
    onJump(parsed);
  };

  const submitPrintedJump = () => {
    if (!onPrintedPageJump) return;
    const trimmed = printedDraft.trim();
    if (!trimmed) {
      setPrintedDraft(currentPrintedPage ?? '');
      return;
    }
    if (trimmed !== (currentPrintedPage ?? '')) onPrintedPageJump(trimmed);
  };

  const partIsArabic = currentPart != null && isArabicText(currentPart);
  const showPartSelector = availableParts.length > 1 && onPartChange != null;
  const showPrintedInput = onPrintedPageJump != null;
  const hasSourceMarker = currentPrintedPage != null || currentPart != null;

  // Опции Тома: arabic part → render as-is с naskh, numeric → локализованное "Том N"
  const partOptions: SelectOption[] = availableParts.map((p) => {
    const arabic = isArabicText(p);
    return {
      value: p,
      label: arabic ? p : `${t('reader.volume')} ${p}`,
      labelClassName: arabic ? 'font-naskh' : '',
      dir: arabic ? 'rtl' : 'ltr',
    };
  });

  return (
    <div className="flex flex-wrap items-center gap-3 text-sm text-ink-700">
      <div className="flex items-center gap-2">
        <span className="text-ink-500">{t('reader.page')}</span>
        <input
          type="number"
          min={1}
          max={totalPages > 0 ? totalPages : undefined}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              submit();
            }
          }}
          onBlur={submit}
          className="h-7 w-20 rounded border border-border-strong px-2 text-center font-mono text-sm outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
          aria-label={t('reader.page')}
        />
        {totalPages > 0 && (
          <span className="font-mono text-ink-400">
            <bdi dir="ltr">/ {totalPages}</bdi>
          </span>
        )}
      </div>

      {/* Editable shamela-блок (Том dropdown + printedPage input). Не
          рендерится для книг без source markers (printedPage/part NULL
          у всех страниц - например для not-shamela импортов) */}
      {hasSourceMarker && (showPartSelector || showPrintedInput) && (
        <div className="flex items-center gap-1.5 rounded-md border border-accent-100 bg-accent-50/60 px-2 py-1 text-xs text-accent-700">
          {showPartSelector ? (
            <Select
              value={currentPart ?? ''}
              onChange={(v) => onPartChange?.(v)}
              options={partOptions}
              size="sm"
              ariaLabel={t('reader.volume_aria')}
              dir={partIsArabic ? 'rtl' : 'ltr'}
              menuMinWidth={120}
              className="w-[100px]"
            />
          ) : currentPart != null ? (
            partIsArabic ? (
              <span className="font-naskh" dir="rtl">
                <bdi>ج: </bdi>
                <bdi>{currentPart}</bdi>
              </span>
            ) : (
              <span className="font-mono" dir="ltr">
                <bdi>{t('reader.volume')} </bdi>
                <bdi>{currentPart}</bdi>
              </span>
            )
          ) : null}
          {currentPart != null && (currentPrintedPage != null || showPrintedInput) && (
            <span className="text-accent-100">·</span>
          )}
          {showPrintedInput ? (
            <div className="flex items-center gap-1">
              <span className="text-accent-600">{t('reader.page_short')}</span>
              <input
                type="text"
                value={printedDraft}
                onChange={(e) => setPrintedDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    submitPrintedJump();
                  }
                }}
                onBlur={submitPrintedJump}
                className="h-6 w-12 rounded border border-accent-100 bg-elevated px-1.5 text-center font-mono text-xs text-accent-700 outline-none focus:border-accent-500 focus:ring-1 focus:ring-accent-500/30"
                aria-label={t('reader.page_short')}
              />
            </div>
          ) : currentPrintedPage != null ? (
            <span className="font-mono">
              <bdi>{t('reader.page_short')}</bdi> <bdi dir="ltr">{currentPrintedPage}</bdi>
            </span>
          ) : null}
        </div>
      )}
    </div>
  );
}

export default PageJump;
