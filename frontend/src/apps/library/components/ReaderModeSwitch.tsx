import { FileText, Image as ImageIcon } from 'lucide-react';
import type { ReaderMode } from '@/apps/library/utils/bookReaderUtils';

interface Props {
  mode: ReaderMode;
  onChange: (mode: ReaderMode) => void;
}

/**
 * Toggle между Text / PDF режимами reader'а. По дизайн-референсу
 * platform_reader.jsx::PageToolbar - сегментированный switcher с
 * выделением активного через bg-white + shadow.
 */
function ReaderModeSwitch({ mode, onChange }: Props) {
  const options: { k: ReaderMode; l: string; icon: typeof FileText }[] = [
    { k: 'text', l: 'Текст', icon: FileText },
    { k: 'pdf', l: 'PDF', icon: ImageIcon },
  ];
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md bg-slate-100 p-0.5">
      {options.map((o) => {
        const Icon = o.icon;
        const active = mode === o.k;
        return (
          <button
            key={o.k}
            type="button"
            onClick={() => onChange(o.k)}
            className={`inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-[12px] font-medium transition-colors ${
              active
                ? 'bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,23,42,0.06)]'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Icon size={13} aria-hidden="true" />
            {o.l}
          </button>
        );
      })}
    </div>
  );
}

export default ReaderModeSwitch;
