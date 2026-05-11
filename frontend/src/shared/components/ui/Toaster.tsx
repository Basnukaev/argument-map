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
    container: 'border-red-300 bg-red-50',
    icon: 'text-red-600',
  },
  warning: {
    Icon: AlertTriangle,
    container: 'border-amber-300 bg-amber-50',
    icon: 'text-amber-600',
  },
  info: {
    Icon: Info,
    container: 'border-blue-300 bg-blue-50',
    icon: 'text-blue-600',
  },
  success: {
    Icon: CheckCircle,
    container: 'border-green-300 bg-green-50',
    icon: 'text-green-600',
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
      className={`pointer-events-auto flex w-80 items-start gap-2 rounded-md border-2 p-3 shadow-lg ${meta.container}`}
    >
      <Icon size={18} className={`shrink-0 mt-0.5 ${meta.icon}`} />
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 break-words">{toast.message}</p>
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick();
              dismiss(toast.id);
            }}
            className="mt-1 text-xs font-medium text-blue-700 hover:text-blue-900"
          >
            {toast.action.label}
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={() => dismiss(toast.id)}
        aria-label="Закрыть уведомление"
        className="shrink-0 rounded p-0.5 text-gray-500 hover:bg-gray-200/50 hover:text-gray-700"
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
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col-reverse gap-2"
      aria-live="polite"
    >
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </div>
  );
}

export default Toaster;
