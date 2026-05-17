import { Lock, Users, Globe } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';

/**
 * Уровни visibility темы (ADR-043). Соответствует enum-like константам бэка
 * (см. {@code TopicVisibility} в backend).
 */
export type TopicVisibility = 'PRIVATE' | 'SHARED' | 'PUBLIC';

interface Option {
  value: TopicVisibility;
  icon: LucideIcon;
  labelKey: DictKey;
  hintKey: DictKey;
}

const OPTIONS: readonly Option[] = [
  {
    value: 'PRIVATE',
    icon: Lock,
    labelKey: 'topic.visibility.private_label',
    hintKey: 'topic.visibility.private_hint',
  },
  {
    value: 'SHARED',
    icon: Users,
    labelKey: 'topic.visibility.shared_label',
    hintKey: 'topic.visibility.shared_hint',
  },
  {
    value: 'PUBLIC',
    icon: Globe,
    labelKey: 'topic.visibility.public_label',
    hintKey: 'topic.visibility.public_hint',
  },
];

interface Props {
  value: TopicVisibility;
  onChange: (next: TopicVisibility) => void;
  disabled?: boolean;
}

/**
 * Radio group для выбора видимости темы. Стилизован как набор карточек
 * с иконкой + label + hint - три опции (private / shared / public).
 *
 * Visual rationale: per ui-guidelines используем border-accent для active
 * state и ring-accent/20 для focus-visible
 */
function VisibilityRadioGroup({ value, onChange, disabled = false }: Props) {
  const t = useT();
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3" role="radiogroup">
      {OPTIONS.map((opt) => {
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
                name="topic-visibility"
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
