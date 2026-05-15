import { NODE_TYPE_TOKENS, type NodeType } from '@/shared/utils/designTokens';
import { useT } from '@/shared/i18n';

type Size = 'sm' | 'md';

interface Props {
  type: NodeType;
  size?: Size;
  className?: string;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-5 px-1.5 text-xs gap-1 rounded',
  md: 'h-6 px-2 text-xs gap-1 rounded',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 12 };

function TypeChip({ type, size = 'md', className = '' }: Props) {
  const t = useT();
  const token = NODE_TYPE_TOKENS[type];
  const Icon = token.Icon;
  return (
    <span
      data-testid="type-chip"
      data-type={type}
      className={`inline-flex items-center font-semibold uppercase tracking-wider ${SIZE_CLASSES[size]} ${token.chipBg} ${token.chipText} ${className}`}
    >
      <Icon size={ICON_SIZE[size]} aria-hidden="true" />
      {t(token.labelKey)}
    </span>
  );
}

export default TypeChip;
