import { NODE_TYPE_TOKENS, type NodeType } from '@/utils/designTokens';

type Size = 'sm' | 'md';

interface Props {
  type: NodeType;
  size?: Size;
  className?: string;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-5 px-1.5 text-[10px] gap-1 rounded',
  md: 'h-6 px-2 text-[11px] gap-1 rounded',
};

const ICON_SIZE: Record<Size, number> = { sm: 11, md: 12 };

function TypeChip({ type, size = 'md', className = '' }: Props) {
  const token = NODE_TYPE_TOKENS[type];
  const Icon = token.Icon;
  return (
    <span
      data-testid="type-chip"
      data-type={type}
      className={`inline-flex items-center font-semibold uppercase tracking-wider ${SIZE_CLASSES[size]} ${token.chipBg} ${token.chipText} ${className}`}
    >
      <Icon size={ICON_SIZE[size]} aria-hidden="true" />
      {token.label}
    </span>
  );
}

export default TypeChip;
