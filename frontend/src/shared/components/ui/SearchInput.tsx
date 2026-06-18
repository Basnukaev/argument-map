import { useState } from 'react';
import { Keyboard, Search, X } from 'lucide-react';
import { useT } from '@/shared/i18n';
import ArabicKeyboard from './ArabicKeyboard';

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel?: string;
  /** Доп. классы на внешнюю обёртку - напр. для ширины / flex-grow. */
  className?: string;
  /**
   * Opt-in: trailing кнопка-тогл арабской виртуальной клавиатуры (для
   * пользователей без арабской раскладки). Вставка идёт в конец значения.
   * Включается только на арабо-ориентированных полях (matn / рави / книги) -
   * для русскоязычного поиска (темы / Q&A) не задавать. По умолчанию false:
   * компонент рендерится ровно как раньше, без регрессии.
   */
  arabicKeyboard?: boolean;
}

/**
 * SearchInput - единый стилизованный поиск для всех list-страниц.
 * Leading Search-иконка + clear (×) кнопка когда поле непустое.
 *
 * Консолидирует inline-паттерны которые раньше были скопированы в
 * BookListPage / HadithListPage / TopicListPage / QuestionListPage.
 * Высота h-9 совпадает с Button md / Select md / FilterChips - один
 * визуальный ряд в ListToolbar.
 *
 * dir="auto" на input: запрос может быть арабский (поиск по matn) или
 * русский - браузер сам выбирает направление по первому strong-символу.
 *
 * arabicKeyboard (opt-in): trailing кнопка-тогл открывает попап с арабскими
 * буквами под полем (ArabicKeyboard). Вставка в конец значения, фокус
 * инпута сохраняется.
 */
function SearchInput({
  value,
  onChange,
  placeholder,
  ariaLabel,
  className = '',
  arabicKeyboard = false,
}: Props) {
  const t = useT();
  const label = ariaLabel ?? t('common.search');
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  return (
    <div
      className={`relative flex h-9 items-center rounded-md border border-border-strong bg-elevated transition-colors focus-within:border-accent-500 focus-within:ring-2 focus-within:ring-accent-500/20 ${className}`}
    >
      <Search size={15} className="ms-3 shrink-0 text-ink-400" aria-hidden />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={label}
        dir="auto"
        className="min-w-0 flex-1 bg-transparent px-3 text-sm text-ink-900 outline-none placeholder:text-ink-400"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          title={t('common.clear')}
          aria-label={t('common.clear')}
          className="me-1 grid h-6 w-6 shrink-0 place-items-center rounded-sm text-ink-400 transition-colors hover:bg-ink-100 hover:text-ink-700"
        >
          <X size={12} aria-hidden />
        </button>
      )}
      {arabicKeyboard && (
        <button
          type="button"
          onClick={() => setKeyboardOpen((v) => !v)}
          title={t('common.arabic_keyboard')}
          aria-label={t('common.arabic_keyboard')}
          aria-expanded={keyboardOpen}
          className={`me-2 grid h-6 w-6 shrink-0 place-items-center rounded-sm transition-colors hover:bg-ink-100 ${
            keyboardOpen ? 'bg-accent-100 text-accent-700' : 'text-ink-400 hover:text-ink-700'
          }`}
        >
          <Keyboard size={14} aria-hidden />
        </button>
      )}
      {arabicKeyboard && keyboardOpen && (
        <ArabicKeyboard
          onInsert={(char) => onChange(value + char)}
          onBackspace={() => onChange(value.slice(0, -1))}
          onClose={() => setKeyboardOpen(false)}
        />
      )}
    </div>
  );
}

export default SearchInput;
