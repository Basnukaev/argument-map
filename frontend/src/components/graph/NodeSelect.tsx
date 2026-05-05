import { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { NODE_TYPE_META, type NodeType } from '@/utils/edgeRules';
import type { components } from '@/api/types';

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

const STATUS_DOT: Record<NodeStatus, string> = {
  STANDING: 'bg-green-500',
  DISPUTED: 'bg-amber-500',
  REFUTED: 'bg-red-500',
  UNVERIFIED: 'bg-gray-400',
};

const STATUS_LABEL: Record<NodeStatus, string> = {
  STANDING: 'Устоявшийся',
  DISPUTED: 'Спорный',
  REFUTED: 'Опровергнут',
  UNVERIFIED: 'Не оценён',
};

const PREVIEW_LEN = 80;

function previewContent(node: NodeDto): string {
  const c = node.content ?? '';
  return c.length > PREVIEW_LEN ? `${c.slice(0, PREVIEW_LEN)}…` : c || '(без содержимого)';
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
        className={`flex w-full items-center gap-2 rounded-md border border-gray-300 bg-white px-3 py-2 text-left text-sm transition-colors ${
          disabled
            ? 'cursor-not-allowed opacity-60'
            : 'hover:border-gray-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200'
        }`}
      >
        {selected ? (
          <NodeOptionInline node={selected} />
        ) : (
          <span className="flex-1 text-gray-500">{placeholder ?? '- выбрать узел -'}</span>
        )}
        <ChevronDown
          size={16}
          className={`shrink-0 text-gray-500 transition-transform ${open ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {open && (
        <ul
          role="listbox"
          aria-labelledby={buttonId}
          className="absolute left-0 right-0 z-20 mt-1 max-h-72 overflow-y-auto rounded-md border border-gray-200 bg-white shadow-lg"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-sm italic text-gray-500">Нет доступных узлов</li>
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
                    className={`flex w-full items-start gap-2 px-3 py-2 text-left text-sm transition-colors ${
                      isSelected ? 'bg-blue-50' : 'hover:bg-gray-50'
                    }`}
                  >
                    <NodeOptionInline node={n} compact={false} />
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
  compact?: boolean;
}

/** Inline-вид опции - используется и в триггере (compact), и в списке (full) */
function NodeOptionInline({ node, compact = true }: InlineProps) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const meta = NODE_TYPE_META[nodeType];
  const Icon = meta.Icon;
  const status: NodeStatus = node.status ?? 'UNVERIFIED';

  return (
    <>
      <Icon size={16} className="mt-0.5 shrink-0 text-gray-700" aria-hidden="true" />
      <span className="flex-1 min-w-0">
        <span className="flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">{meta.label}</span>
          <span
            className={`inline-block h-1.5 w-1.5 shrink-0 rounded-full ${STATUS_DOT[status]}`}
            title={STATUS_LABEL[status]}
            aria-label={STATUS_LABEL[status]}
          />
        </span>
        <span className={`block text-gray-900 ${compact ? 'truncate' : 'whitespace-pre-wrap break-words'}`}>
          {previewContent(node)}
        </span>
      </span>
    </>
  );
}

export default NodeSelect;
