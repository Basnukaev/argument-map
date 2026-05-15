import { HelpCircle, Quote, Sparkles, FileText, type LucideIcon } from 'lucide-react';
import { clsx } from 'clsx';

export type NodeType = 'QUESTION' | 'CLAIM' | 'ARGUMENT' | 'EVIDENCE';

interface TypeMeta {
  /** Token CSS variable name (without var()) */
  bg: 'type-abstract' | 'type-empirical';
  Icon: LucideIcon;
  label: string;
}

const TYPE: Record<NodeType, TypeMeta> = {
  QUESTION: { bg: 'type-abstract',  Icon: HelpCircle, label: 'Вопрос' },
  CLAIM:    { bg: 'type-abstract',  Icon: Quote,      label: 'Тезис' },
  ARGUMENT: { bg: 'type-abstract',  Icon: Sparkles,   label: 'Довод' },
  EVIDENCE: { bg: 'type-empirical', Icon: FileText,   label: 'Свидетельство' },
};

/**
 * TypeChip — semantic chip for graph node types.
 *
 *   <TypeChip type="CLAIM" />
 *   <TypeChip type="EVIDENCE" selected />
 *
 * Uses the `type-abstract` / `type-empirical` token pair so dark theme
 * stays calm.
 */
export function TypeChip({
  type,
  selected = false,
  className,
}: {
  type: NodeType;
  selected?: boolean;
  className?: string;
}) {
  const t = TYPE[type];
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider',
        selected
          ? `bg-${t.bg}-fg text-ink-0`
          : `bg-${t.bg}-bg text-${t.bg}-fg`,
        className,
      )}
    >
      <t.Icon size={11} />
      {t.label}
    </span>
  );
}
