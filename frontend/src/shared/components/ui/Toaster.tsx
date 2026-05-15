import { AlertCircle, AlertTriangle, CheckCircle, Info, X } from 'lucide-react';
import type { ComponentType } from 'react';
import { useToastStore, type Toast, type ToastKind } from '@/shared/stores/toastStore';

const KIND_META: Record<
  ToastKind,
  {
    Icon: ComponentType<{ size?: number; className?: string }>;
    container: string;
    icon: string;
  }
> = {
  error: {
    Icon: AlertCircle,
    container: 'border-err-500/40 bg-err-100',
    icon: 'text-err-700',
  },
  warning: {
    Icon: AlertTriangle,
    container: 'border-warn-500/40 bg-warn-100',
    icon: 'text-warn-700',
  },
  info: {
    Icon: Info,
    container: 'border-accent-500/40 bg-accent-50',
    icon: 'text-accent-700',
  },
  success: {
    Icon: CheckCircle,
    container: 'border-ok-500/40 bg-ok-100',
    icon: 'text-ok-700',
  },
};

function ToastItem({ toast }: { toast: Toast }) {
  const dismiss = useToastStore((s) => s.dismiss);
  const meta = KIND_META[toast.kind];
  const Icon = meta.Icon;
  return (
    <div
      role="status"
      data-testid={`toast-${toast.kind}`}
      className={`pointer-events-auto flex w-80 items-start gap-2 rounded-md border-2 p-3 shadow-sh3 ${meta.container}`}
    >
      <Icon size={18} className={`shrink-0 mt-0.5 ${meta.icon}`} />
      <div className="flex-1 min-w-0">
        <p dir="auto" className="text-sm text-ink-900 break-words">
          {toast.message}
        </p>
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick();
              dismiss(toast.id);
            }}
            className="mt-1 text-xs font-medium text-accent-700 hover:text-accent-600"
          >
            {toast.action.label}
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={() => dismiss(toast.id)}
        aria-label="dismiss"
        className="shrink-0 rounded-sm p-0.5 text-ink-500 hover:bg-ink-200/50 hover:text-ink-700"
      >
        <X size={14} />
      </button>
    </div>
  );
}

function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  if (toasts.length === 0) return null;
  return (
    <div
      className="pointer-events-none fixed bottom-4 end-4 z-50 flex flex-col-reverse gap-2"
      aria-live="polite"
    >
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </div>
  );
}

export default Toaster;
