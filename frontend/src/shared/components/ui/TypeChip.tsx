import { NODE_TYPE_TOKENS, type NodeType } from '@/shared/utils/designTokens';
import { useT } from '@/shared/i18n';

type Size = 'sm' | 'md';

interface Props {
  type: NodeType;
  size?: Size;
  className?: string;
}

// Вертикальный размер задаём через py-*, а не фиксированный h-*, чтобы текст
// не упирался в границы плашки (баг: метка касалась краёв rect). px/py дают
// гарантированный отступ внутри chip со всех сторон. leading-none убирает
// лишний line-box у uppercase-текста.
const SIZE_CLASSES: Record<Size, string> = {
  sm: 'px-2 py-1 text-xs gap-1 leading-none rounded',
  md: 'px-2.5 py-1 text-xs gap-1.5 leading-none rounded',
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
