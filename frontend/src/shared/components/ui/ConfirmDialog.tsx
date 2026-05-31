import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import { useConfirmStore } from '@/shared/stores/confirmStore';
import { useT } from '@/shared/i18n';

/**
 * Глобальный host promise-based confirm. Монтируется один раз в App.tsx
 * (рядом с Toaster). Открывается императивно через `askConfirm(...)`.
 *
 * Mount/unmount по наличию `request` (идиома `{open && <Modal/>}`): Escape
 * и клик по backdrop трактуются как отмена (settle(false)).
 */
function ConfirmDialog() {
  const t = useT();
  const request = useConfirmStore((s) => s.request);
  const settle = useConfirmStore((s) => s.settle);

  if (!request) return null;

  return (
    <Modal
      open
      onClose={() => settle(false)}
      title={request.title ?? t('common.confirm_title')}
      maxWidth="max-w-md"
    >
      <p className="text-sm leading-relaxed text-ink-700" dir="auto">
        {request.message}
      </p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" size="sm" onClick={() => settle(false)}>
          {request.cancelLabel ?? t('common.cancel')}
        </Button>
        <Button
          variant={request.danger ? 'danger' : 'primary'}
          size="sm"
          onClick={() => settle(true)}
          autoFocus
        >
          {request.confirmLabel ?? t('common.confirm')}
        </Button>
      </div>
    </Modal>
  );
}

export default ConfirmDialog;
