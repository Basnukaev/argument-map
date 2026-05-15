import { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { NODE_TYPE_META, type NodeType } from '@/apps/argument-map/utils/edgeRules';
import { useT, type DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];
type NodeStatus = NonNullable<NodeDto['status']>;

interface Props {
  value: string;
  onChange: (id: string) => void;
  options: NodeDto[];
  /** id, который нужно скрыть из опций (например, для "Куда" исключаем выбранный "Откуда") */
  excludeId?: string;
  placeholder?: string;
  disabled?: boolean;
  /** id для связки с label через htmlFor */
  id?: string;
}

// Точка показывается только для оценённых узлов. UNVERIFIED намеренно
// без точки - иначе на свежем графе все узлы получают визуально одинаковую
// серую точку, бесполезный шум. Цвета совпадают с STATUS_TOKENS.bar
const STATUS_DOT: Record<NodeStatus, string | null> = {
  STANDING: 'bg-ok-500',
  DISPUTED: 'bg-warn-500',
  REFUTED: 'bg-err-500',
  UNVERIFIED: null,
};

const STATUS_LABEL_KEY: Record<NodeStatus, DictKey> = {
  STANDING: 'status.STANDING',
  DISPUTED: 'status.DISPUTED',
  REFUTED: 'status.REFUTED',
  UNVERIFIED: 'status.UNVERIFIED',
};

const PREVIEW_LEN = 80;

function previewContent(node: NodeDto, emptyText: string): string {
  const c = node.content ?? '';
  return c.length > PREVIEW_LEN ? `${c.slice(0, PREVIEW_LEN)}…` : c || emptyText;
}

/**
 * Кастомный dropdown для выбора узла. Заменяет нативный `<select>` где нужны
 * lucide-иконки типа и цветной маркер статуса в опции - в `<option>` нельзя
 * вставить SVG.
 *
 * Закрывается по клику вне, Escape, выбору. Не имеет встроенного поиска -
 * для текущего масштаба (десятки узлов на тему) достаточно скролла.
 */
function NodeSelect({ value, onChange, options, excludeId, placeholder, disabled, id }: Props) {
  const t = useT();
  const fallbackId = useId();
  const buttonId = id ?? fallbackId;

  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const filtered = options.filter(
    (n): n is NodeDto & { id: string } => Boolean(n.id) && n.id !== excludeId,
  );
  const selected = filtered.find((n) => n.id === value);

  // закрываем по клику вне контейнера; click-listener вешается только пока open
  useEffect(() => {
    if (!open) return;
    function onClickOutside(event: MouseEvent) {
      if (!containerRef.current) return;
      if (!containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onEsc(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    document.addEventListener('keydown', onEsc);
    return () => {
      document.removeEventListener('mousedown', onClickOutside);
      document.removeEventListener('keydown', onEsc);
    };
  }, [open]);

  function pick(nodeId: string) {
    onChange(nodeId);
    setOpen(false);
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        id={buttonId}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={`flex w-full items-center gap-2 rounded-md border border-border-strong bg-elevated px-3 py-2 text-start text-sm transition-colors ${
          disabled
            ? 'cursor-not-allowed opacity-60'
            : 'hover:border-ink-400 focus:border-accent-500 focus:outline-none focus:ring-2 focus:ring-accent-500/20'
        }`}
      >
        {selected ? (
          <NodeOptionInline node={selected} t={t} />
        ) : (
          <span className="flex-1 text-ink-500">{placeholder ?? t('node.select_placeholder')}</span>
        )}
        <ChevronDown
          size={16}
          className={`shrink-0 text-ink-500 transition-transform ${open ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {open && (
        <ul
          role="listbox"
          aria-labelledby={buttonId}
          className="absolute inset-x-0 z-20 mt-1 max-h-72 overflow-y-auto rounded-md border border-border bg-elevated shadow-lg"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-sm italic text-ink-500">{t('graph.no_nodes_in_select')}</li>
          ) : (
            filtered.map((n) => {
              const isSelected = n.id === value;
              return (
                <li key={n.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => pick(n.id)}
                    className={`flex w-full items-start gap-2 px-3 py-2 text-start text-sm transition-colors ${
                      isSelected ? 'bg-accent-50' : 'hover:bg-ink-50'
                    }`}
                  >
                    <NodeOptionInline node={n} compact={false} t={t} />
                  </button>
                </li>
              );
            })
          )}
        </ul>
      )}
    </div>
  );
}

interface InlineProps {
  node: NodeDto;
  t: (key: DictKey) => string;
  compact?: boolean;
}

/** Inline-вид опции - используется и в триггере (compact), и в списке (full) */
function NodeOptionInline({ node, t, compact = true }: InlineProps) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const meta = NODE_TYPE_META[nodeType];
  const Icon = meta.Icon;
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const statusLabel = t(STATUS_LABEL_KEY[status]);

  return (
    <>
      <Icon size={16} className="mt-0.5 shrink-0 text-ink-700" aria-hidden="true" />
      <span className="flex-1 min-w-0">
        <span className="flex items-center gap-2">
          <span className="text-xs font-medium text-ink-500">{t(meta.labelKey)}</span>
          {STATUS_DOT[status] && (
            <span
              className={`inline-block h-1.5 w-1.5 shrink-0 rounded-full ${STATUS_DOT[status]}`}
              title={statusLabel}
              aria-label={statusLabel}
            />
          )}
        </span>
        <span dir="auto" className={`block text-ink-900 ${compact ? 'truncate' : 'whitespace-pre-wrap break-words'}`}>
          {previewContent(node, t('node.empty_content'))}
        </span>
      </span>
    </>
  );
}

export default NodeSelect;
