import { Lock, Users, Globe } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';

/**
 * Уровни visibility сущности (ADR-043). Соответствует enum-like константам
 * бэка - `TopicVisibility` / `BookVisibility`. Тип общий т.к. enum-значения
 * совпадают и семантика одинаковая
 */
export type Visibility = 'PRIVATE' | 'SHARED' | 'PUBLIC';

/** Алиас для обратной совместимости со старым импортом */
export type TopicVisibility = Visibility;

interface Option {
  value: Visibility;
  icon: LucideIcon;
  labelKey: DictKey;
  hintKey: DictKey;
}

interface Props {
  value: Visibility;
  onChange: (next: Visibility) => void;
  disabled?: boolean;
  /**
   * Префикс i18n-ключей для labels/hints. Расширяется как
   * `${labelPrefix}.private_label`, `.shared_label` и т.д. По умолчанию
   * `topic.visibility` для backward compat. Для книг передавать
   * `book.visibility`
   */
  labelPrefix?: 'topic.visibility' | 'book.visibility';
  /** Уникальное имя radio-группы (для нескольких groups на странице) */
  groupName?: string;
}

/**
 * Radio group для выбора видимости сущности. Стилизован как набор карточек
 * с иконкой + label + hint - три опции (private / shared / public).
 * Generic - подходит для topics и books, ключи i18n берёт через
 * `labelPrefix`
 *
 * Visual rationale: per ui-guidelines используем border-accent для active
 * state и ring-accent/20 для focus-visible
 */
function VisibilityRadioGroup({
  value,
  onChange,
  disabled = false,
  labelPrefix = 'topic.visibility',
  groupName,
}: Props) {
  const t = useT();
  const options: readonly Option[] = [
    {
      value: 'PRIVATE',
      icon: Lock,
      labelKey: `${labelPrefix}.private_label` as DictKey,
      hintKey: `${labelPrefix}.private_hint` as DictKey,
    },
    {
      value: 'SHARED',
      icon: Users,
      labelKey: `${labelPrefix}.shared_label` as DictKey,
      hintKey: `${labelPrefix}.shared_hint` as DictKey,
    },
    {
      value: 'PUBLIC',
      icon: Globe,
      labelKey: `${labelPrefix}.public_label` as DictKey,
      hintKey: `${labelPrefix}.public_hint` as DictKey,
    },
  ];
  const name = groupName ?? `${labelPrefix.replace('.', '-')}`;
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3" role="radiogroup">
      {options.map((opt) => {
        const Icon = opt.icon;
        const selected = value === opt.value;
        return (
          <label
            key={opt.value}
            className={`flex cursor-pointer flex-col gap-1.5 rounded-md border p-3 transition-colors ${
              selected
                ? 'border-accent-500 bg-accent-50'
                : 'border-border bg-elevated hover:border-border-strong'
            } ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
          >
            <div className="flex items-center gap-2">
              <input
                type="radio"
                name={name}
                value={opt.value}
                checked={selected}
                disabled={disabled}
                onChange={() => onChange(opt.value)}
                className="sr-only"
              />
              <Icon
                size={16}
                className={selected ? 'text-accent-600' : 'text-ink-500'}
                aria-hidden
              />
              <span
                className={`text-sm font-semibold ${
                  selected ? 'text-accent-700' : 'text-ink-900'
                }`}
              >
                {t(opt.labelKey)}
              </span>
            </div>
            <p className="text-xs leading-relaxed text-ink-500">
              {t(opt.hintKey)}
            </p>
          </label>
        );
      })}
    </div>
  );
}

export default VisibilityRadioGroup;
