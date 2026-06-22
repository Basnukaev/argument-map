import { useEffect, useRef } from 'react';
import { Delete, X } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useHotkey } from '@/shared/hooks/useHotkey';

interface Props {
  /** Вставить букву (в конец значения) - state держит родитель. */
  onInsert: (char: string) => void;
  /** Стереть последний символ. */
  onBackspace: () => void;
  /** Закрыть панель. */
  onClose: () => void;
}

/**
 * ArabicKeyboard - попап-панель с арабскими буквами для поисковых полей.
 * Целевой юзер - не арабский машинист, поэтому раскладка АЛФАВИТНАЯ (а не
 * стандартная QWERTY-арабская): буквы идут по алфавиту, их проще искать.
 *
 * Stateless по вводу: вставку / бэкспейс / закрытие отдаёт наружу
 * (onInsert / onBackspace / onClose), значение хранит родитель (SearchInput).
 *
 * Фокус не уводим с инпута: onMouseDown preventDefault на каждой кнопке -
 * стандартный приём виртуальных клавиатур (клик не блюрит поле, курсор
 * остаётся, можно печатать дальше).
 */

/**
 * Стандартная физическая арабская раскладка (Arabic 101, QWERTY-mapped) —
 * НЕ алфавитный порядок, как на реальной клавиатуре (FB-4a). Три ряда:
 * верхний (ضصثقفغعهخحجد), средний (شسيبلاتنمكط), нижний (ئءؤرلاىةوزظ).
 */
const KEY_ROWS: ReadonlyArray<ReadonlyArray<string>> = [
  ['ض', 'ص', 'ث', 'ق', 'ف', 'غ', 'ع', 'ه', 'خ', 'ح', 'ج', 'د'],
  ['ش', 'س', 'ي', 'ب', 'ل', 'ا', 'ت', 'ن', 'م', 'ك', 'ط'],
  ['ئ', 'ء', 'ؤ', 'ر', 'لا', 'ى', 'ة', 'و', 'ز', 'ظ'],
];

/** Доп. формы (на физической раскладке — shift/иные состояния): ذ + алиф-хамза,
 *  важны для поиска по matn, где встречаются именно эти формы. */
const EXTRA_FORMS: ReadonlyArray<string> = ['ذ', 'أ', 'إ', 'آ'];

function ArabicKeyboard({ onInsert, onBackspace, onClose }: Props) {
  const t = useT();
  const ref = useRef<HTMLDivElement>(null);

  // Закрытие по клику вне панели. pointerdown (как в ContextMenu) - срабатывает
  // до blur инпута, не конфликтует с preventDefault на кнопках клавиатуры.
  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose();
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [onClose]);

  // Escape закрывает. enableOnFormTags: фокус остаётся в search-инпуте.
  useHotkey('escape', onClose, { enableOnFormTags: true });

  /** Кнопка-буква. preventDefault на mousedown - сохраняет фокус инпута. */
  function letterButton(char: string) {
    return (
      <button
        key={char}
        type="button"
        onMouseDown={(e) => e.preventDefault()}
        onClick={() => onInsert(char)}
        aria-label={char}
        className="grid h-9 min-w-8 place-items-center rounded-sm border border-border-strong bg-elevated px-1 font-arabic text-base text-ink-900 transition-colors hover:bg-accent-100 hover:text-accent-700"
      >
        {char}
      </button>
    );
  }

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label={t('common.arabic_keyboard')}
      dir="ltr"
      className="absolute end-0 top-full z-30 mt-1 w-[28rem] max-w-[calc(100vw-1rem)] rounded-md border border-border bg-elevated p-2.5 shadow-sh3"
    >
      {KEY_ROWS.map((row, i) => (
        <div
          key={row.join('')}
          className={`flex flex-wrap justify-center gap-0.5${i > 0 ? ' mt-1' : ''}`}
        >
          {row.map(letterButton)}
        </div>
      ))}
      <div className="mt-1 flex flex-wrap justify-center gap-0.5">
        {EXTRA_FORMS.map(letterButton)}
      </div>
      <div className="mt-2 flex items-center gap-1.5">
        <button
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => onInsert(' ')}
          aria-label={t('common.keyboard.space')}
          className="h-9 flex-1 rounded-sm border border-border-strong bg-elevated text-sm text-ink-700 transition-colors hover:bg-ink-100"
        >
          {t('common.keyboard.space')}
        </button>
        <button
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={onBackspace}
          aria-label={t('common.keyboard.backspace')}
          className="grid h-9 w-11 shrink-0 place-items-center rounded-sm border border-border-strong bg-elevated text-ink-700 transition-colors hover:bg-ink-100"
        >
          <Delete size={16} aria-hidden />
        </button>
        <button
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={onClose}
          aria-label={t('common.keyboard.close')}
          className="grid h-9 w-9 shrink-0 place-items-center rounded-sm border border-border-strong bg-elevated text-ink-700 transition-colors hover:bg-ink-100"
        >
          <X size={16} aria-hidden />
        </button>
      </div>
    </div>
  );
}

export default ArabicKeyboard;
