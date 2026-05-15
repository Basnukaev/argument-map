import { STATUS_TOKENS, type NodeStatus } from '@/shared/utils/designTokens';
import { useT } from '@/shared/i18n';

type Size = 'sm' | 'md' | 'lg';

interface Props {
  status: NodeStatus;
  size?: Size;
  showIcon?: boolean;
  className?: string;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-5 px-1.5 text-[11px] gap-1 rounded',
  md: 'h-6 px-2 text-[11px] gap-1 rounded-md',
  lg: 'h-7 px-2.5 text-[12px] gap-1.5 rounded-md',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 12, lg: 14 };

function StatusBadge({ status, size = 'md', showIcon = true, className = '' }: Props) {
  const t = useT();
  const token = STATUS_TOKENS[status];
  const Icon = token.Icon;
  return (
    <span
      data-testid="status-badge"
      data-status={status}
      className={`inline-flex items-center font-medium border whitespace-nowrap ${SIZE_CLASSES[size]} ${token.badgeBg} ${token.badgeText} ${token.badgeBorder} ${className}`}
    >
      {showIcon && <Icon size={ICON_SIZE[size]} aria-hidden="true" />}
      {t(token.labelKey)}
    </span>
  );
}

export default StatusBadge;
