import type { FormEvent, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
import { useT } from '@/shared/i18n';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Опциональный max-width Tailwind class (например `max-w-xl`). По умолчанию Modal сам выбирает. */
  maxWidth?: string;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void | Promise<void>;
  submitting: boolean;
  submitDisabled?: boolean;
  submitLabel: string;
  submittingLabel: string;
  submitIcon?: LucideIcon;
  error?: string | null;
  /** Опциональная подсказка hotkey'а (например `⌘+↵ создать`) в левом
   * нижнем углу footer'а. Если не задана - footer симметричный по краям. */
  hotkeyHint?: ReactNode;
  children: ReactNode;
}

/**
 * Универсальный wrapper для модалок с формой - Modal + form + error +
 * footer (Cancel/Submit). Извлечён из AddNodeModal/AddEdgeModal как
 * общий pattern (F-05 audit). Form content передаётся через children.
 */
function FormModal({
  open,
  onClose,
  title,
  maxWidth,
  onSubmit,
  submitting,
  submitDisabled,
  submitLabel,
  submittingLabel,
  submitIcon,
  error,
  hotkeyHint,
  children,
}: Props) {
  const t = useT();
  return (
    <Modal open={open} onClose={onClose} title={title} maxWidth={maxWidth}>
      <form onSubmit={onSubmit} className="space-y-5">
        {children}

        {error && (
          <div className="rounded-md border border-err-500/40 bg-err-100 p-3 text-xs text-err-700">
            {error}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-border pt-3">
          {hotkeyHint ? (
            <span className="hidden items-center gap-1 text-xs text-ink-500 sm:inline-flex">
              {hotkeyHint}
            </span>
          ) : (
            <span />
          )}
          <div className="ms-auto flex items-center gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" icon={submitIcon} disabled={submitting || submitDisabled}>
              {submitting ? submittingLabel : submitLabel}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}

export { Kbd };
export default FormModal;
