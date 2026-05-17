import { Lock, Users, Globe } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';
import type { TopicVisibility } from '@/apps/argument-map/components/VisibilityRadioGroup';

interface BadgeMeta {
  icon: LucideIcon;
  labelKey: DictKey;
}

const META: Record<TopicVisibility, BadgeMeta> = {
  PRIVATE: { icon: Lock, labelKey: 'topic.visibility.private_label' },
  SHARED: { icon: Users, labelKey: 'topic.visibility.shared_label' },
  PUBLIC: { icon: Globe, labelKey: 'topic.visibility.public_label' },
};

interface Props {
  visibility: TopicVisibility | string | null | undefined;
  /** Компактный режим - только иконка с tooltip. По умолчанию иконка + текст */
  compact?: boolean;
  className?: string;
}

/**
 * Компактный badge с иконкой и подписью visibility темы. Используется в
 * заголовках страниц и на карточках в списке тем. Tooltip (`title`) - всегда
 * текстовый label, даже в compact-режиме (accessibility + hover-разовый
 * быстрый просмотр без открытия настроек темы)
 */
function VisibilityBadge({ visibility, compact = false, className = '' }: Props) {
  const t = useT();
  // Defensive fallback - на случай если бэк отдаст null или legacy-значение
  const normalized = (visibility ?? 'PRIVATE') as TopicVisibility;
  const meta = META[normalized] ?? META.PRIVATE;
  const Icon = meta.icon;
  const label = t(meta.labelKey);

  return (
    <span
      title={label}
      className={`inline-flex items-center gap-1 rounded-sm border border-border bg-elevated px-1.5 py-0.5 text-xs font-medium text-ink-700 ${className}`}
    >
      <Icon size={11} aria-hidden />
      {!compact && <span>{label}</span>}
    </span>
  );
}

export default VisibilityBadge;
