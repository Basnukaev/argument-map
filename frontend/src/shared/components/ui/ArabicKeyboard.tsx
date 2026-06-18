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

/** Полный алфавит (28 букв) построчно по 7 - сетка переносится естественно. */
const ALPHABET: ReadonlyArray<string> = [
  'ا', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ',
  'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص',
  'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق',
  'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي',
];

/** Варианты букв (хамза / мадда / та-марбута / алиф-максура / лям-алиф) -
 *  важны для поиска по matn, где встречаются именно эти формы. */
const VARIANTS: ReadonlyArray<string> = [
  'ء', 'أ', 'إ', 'آ', 'ؤ', 'ئ', 'ة', 'ى', 'لا',
];

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
        className="grid h-9 min-w-9 place-items-center rounded-sm border border-border-strong bg-elevated font-arabic text-lg text-ink-900 transition-colors hover:bg-accent-100 hover:text-accent-700"
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
      dir="rtl"
      className="absolute end-0 top-full z-30 mt-1 w-[19rem] max-w-[calc(100vw-2rem)] rounded-md border border-border bg-elevated p-2.5 shadow-sh3"
    >
      <div className="grid grid-cols-7 gap-1">
        {ALPHABET.map(letterButton)}
      </div>
      <div className="mt-1 grid grid-cols-7 gap-1">
        {VARIANTS.map(letterButton)}
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
