import { Lock, Users, Globe } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';
import type { Visibility } from '@/shared/components/visibility/VisibilityRadioGroup';

interface BadgeMeta {
  icon: LucideIcon;
}

const META: Record<Visibility, BadgeMeta> = {
  PRIVATE: { icon: Lock },
  SHARED: { icon: Users },
  PUBLIC: { icon: Globe },
};

interface Props {
  visibility: Visibility | string | null | undefined;
  /** Компактный режим - только иконка с tooltip. По умолчанию иконка + текст */
  compact?: boolean;
  /**
   * Префикс i18n-ключей для подписи. По умолчанию `topic.visibility` для
   * backward compat. Для книг передавать `book.visibility`
   */
  labelPrefix?: 'topic.visibility' | 'book.visibility';
  className?: string;
}

/**
 * Компактный badge с иконкой и подписью visibility сущности. Используется в
 * заголовках страниц и на карточках в списках (тем, книг). Tooltip (`title`)
 * - всегда текстовый label, даже в compact-режиме (accessibility + hover-
 * разовый быстрый просмотр без открытия настроек)
 *
 * Generic - подходит для topics и books, ключи i18n берёт через
 * `labelPrefix`
 */
function VisibilityBadge({
  visibility,
  compact = false,
  labelPrefix = 'topic.visibility',
  className = '',
}: Props) {
  const t = useT();
  // Defensive fallback - на случай если бэк отдаст null или legacy-значение
  const normalized = (visibility ?? 'PRIVATE') as Visibility;
  const meta = META[normalized] ?? META.PRIVATE;
  const Icon = meta.icon;
  const labelKey = `${labelPrefix}.${
    normalized === 'PRIVATE'
      ? 'private_label'
      : normalized === 'SHARED'
        ? 'shared_label'
        : 'public_label'
  }` as DictKey;
  const label = t(labelKey);

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
