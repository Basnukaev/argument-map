import { FileText, Image as ImageIcon } from 'lucide-react';
import type { ReaderMode } from '@/shared/components/reader/utils';
import { useT, type DictKey } from '@/shared/i18n';

interface Props {
  mode: ReaderMode;
  onChange: (mode: ReaderMode) => void;
}

/**
 * Toggle между Text / PDF режимами reader'а. По дизайн-референсу
 * platform_reader.jsx::PageToolbar - сегментированный switcher с
 * выделением активного через bg-elevated + shadow.
 */
function ReaderModeSwitch({ mode, onChange }: Props) {
  const t = useT();
  const options: { k: ReaderMode; labelKey: DictKey; icon: typeof FileText }[] = [
    { k: 'text', labelKey: 'reader.mode.text', icon: FileText },
    { k: 'pdf', labelKey: 'reader.mode.pdf', icon: ImageIcon },
  ];
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md bg-ink-100 p-0.5">
      {options.map((o) => {
        const Icon = o.icon;
        const active = mode === o.k;
        return (
          <button
            key={o.k}
            type="button"
            onClick={() => onChange(o.k)}
            className={`inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium transition-colors ${
              active
                ? 'bg-elevated text-ink-900 shadow-sh2'
                : 'text-ink-500 hover:text-ink-700'
            }`}
          >
            <Icon size={13} aria-hidden="true" />
            {t(o.labelKey)}
          </button>
        );
      })}
    </div>
  );
}

export default ReaderModeSwitch;
