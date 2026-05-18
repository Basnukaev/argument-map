import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { Check, ChevronDown, ChevronUp, X, Circle } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { toast } from '@/shared/stores/toastStore';
import {
  useOnboardingProgress,
  type OnboardingStepId,
} from '@/shared/hooks/useOnboardingProgress';
import type { DictKey } from '@/shared/i18n/dictionary';

/**
 * Floating widget bottom-end - 4-шаговый onboarding чеклист. Render'ится
 * один раз в App.tsx, контролируется через `useOnboardingProgress`.
 *
 * Состояния:
 * - hidden если user не залогинен / dismissed / loading
 * - mini (collapsed) - только counter «N/4» + chevron expand
 * - full - card 320px с header + steps list + progress bar + dismiss
 *
 * Авто-dismiss при completed=total: показывается celebration toast +
 * через 3 сек widget вызывает dismiss() (запись в localStorage).
 * Пользователь успевает прочитать toast, в это время widget остаётся
 * виден с «4/4 done» состоянием. Если пользователь сам нажмёт X
 * до auto-dismiss - просто скрываем без toast
 */
function OnboardingChecklist() {
  const t = useT();
  const navigate = useNavigate();
  const progress = useOnboardingProgress();
  const [collapsed, setCollapsed] = useState(false);
  const [celebrating, setCelebrating] = useState(false);

  // Auto-dismiss after all 4 completed: показываем toast и через 3 сек -
  // dismiss. Защита от повторного срабатывания через celebrating флаг.
  // setState в effect-body - intentional: синхронно ставим флаг "уже
  // отпраздновали" чтобы не зацикливаться. Сам toast + setTimeout -
  // external side effects (toast store, browser timer) - корректное
  // использование useEffect (sync с external)
  useEffect(() => {
    if (
      !progress.isVisible ||
      progress.completed !== progress.total ||
      celebrating
    ) {
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCelebrating(true);
    toast.success(t('onboarding.completed_toast'));
    const timer = setTimeout(() => {
      progress.dismiss();
    }, 3000);
    return () => clearTimeout(timer);
  }, [progress, t, celebrating]);

  if (!progress.isVisible) return null;

  const handleStepClick = (stepId: OnboardingStepId) => {
    // Если шаг уже completed - не делаем ничего, не сбрасываем фокус
    const step = progress.steps.find((s) => s.id === stepId);
    if (step?.completed) return;

    if (stepId === 'create_topic') {
      navigate('/topics/new');
      return;
    }
    if (stepId === 'add_claim_node' || stepId === 'attach_source') {
      // Открыть граф первой темы пользователя. Если темы ещё нет
      // (firstTopicId == null) - сначала надо create_topic, поведение
      // редкое (UI показывает их раздельно) но обработаем
      if (progress.firstTopicId) {
        navigate(`/topics/${progress.firstTopicId}`);
      } else {
        navigate('/topics/new');
      }
    }
    // add_root_question - non-clickable, появляется автоматически
  };

  return (
    <div
      role="region"
      aria-label={t('onboarding.aria_widget')}
      className="pointer-events-auto fixed bottom-4 end-4 z-40 w-80 max-w-[calc(100vw-2rem)] rounded-md border border-border-strong bg-elevated shadow-sh3"
    >
      {collapsed ? (
        <CollapsedView
          completed={progress.completed}
          total={progress.total}
          onExpand={() => setCollapsed(false)}
          onDismiss={progress.dismiss}
        />
      ) : (
        <ExpandedView
          steps={progress.steps}
          completed={progress.completed}
          total={progress.total}
          onCollapse={() => setCollapsed(true)}
          onDismiss={progress.dismiss}
          onStepClick={handleStepClick}
        />
      )}
    </div>
  );
}

interface CollapsedProps {
  completed: number;
  total: number;
  onExpand: () => void;
  onDismiss: () => void;
}

function CollapsedView({ completed, total, onExpand, onDismiss }: CollapsedProps) {
  const t = useT();
  return (
    <div className="flex items-center gap-2 p-3">
      <button
        type="button"
        onClick={onExpand}
        aria-label={t('onboarding.expand')}
        title={t('onboarding.expand')}
        className="flex flex-1 items-center gap-2 rounded-sm text-start focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
      >
        <ChevronUp size={14} className="text-ink-500" aria-hidden />
        <span className="text-xs font-semibold text-ink-700">
          {t('onboarding.title')}
        </span>
        <span className="ms-auto text-xs font-mono text-ink-500">
          <bdi dir="ltr">
            {completed}/{total}
          </bdi>
        </span>
      </button>
      <button
        type="button"
        onClick={onDismiss}
        aria-label={t('onboarding.dismiss_tooltip')}
        title={t('onboarding.dismiss_tooltip')}
        className="shrink-0 rounded-sm p-1 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
      >
        <X size={12} aria-hidden />
      </button>
    </div>
  );
}

interface ExpandedProps {
  steps: ReadonlyArray<{ id: OnboardingStepId; completed: boolean }>;
  completed: number;
  total: number;
  onCollapse: () => void;
  onDismiss: () => void;
  onStepClick: (id: OnboardingStepId) => void;
}

function ExpandedView({
  steps,
  completed,
  total,
  onCollapse,
  onDismiss,
  onStepClick,
}: ExpandedProps) {
  const t = useT();
  const progressLabel = t('onboarding.progress')
    .replace('{completed}', String(completed))
    .replace('{total}', String(total));
  // первый non-completed step - active (с подсветкой)
  const activeStep = steps.find((s) => !s.completed);
  const percent = Math.round((completed / total) * 100);

  return (
    <div className="p-4">
      <div className="mb-3 flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-ink-900">{t('onboarding.title')}</h3>
          <p className="mt-0.5 text-xs text-ink-500">{t('onboarding.subtitle')}</p>
        </div>
        <button
          type="button"
          onClick={onCollapse}
          aria-label={t('onboarding.minimize')}
          title={t('onboarding.minimize')}
          className="shrink-0 rounded-sm p-1 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
        >
          <ChevronDown size={14} aria-hidden />
        </button>
        <button
          type="button"
          onClick={onDismiss}
          aria-label={t('onboarding.dismiss_tooltip')}
          title={t('onboarding.dismiss_tooltip')}
          className="shrink-0 rounded-sm p-1 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
        >
          <X size={14} aria-hidden />
        </button>
      </div>

      <ul className="space-y-1.5">
        {steps.map((step) => (
          <StepRow
            key={step.id}
            stepId={step.id}
            completed={step.completed}
            active={activeStep?.id === step.id}
            onClick={() => onStepClick(step.id)}
          />
        ))}
      </ul>

      <div className="mt-4">
        <div className="mb-1 flex items-center justify-between text-[11px] font-medium text-ink-500">
          <span>{progressLabel}</span>
          <span className="font-mono">
            <bdi dir="ltr">{percent}%</bdi>
          </span>
        </div>
        <div
          className="h-1.5 w-full overflow-hidden rounded-sm bg-ink-100"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={completed}
        >
          <div
            className="h-full bg-accent-500 transition-all"
            style={{ width: `${percent}%` }}
          />
        </div>
      </div>
    </div>
  );
}

const STEP_LABEL_KEYS: Record<OnboardingStepId, DictKey> = {
  create_topic: 'onboarding.step.create_topic.label',
  add_root_question: 'onboarding.step.add_root_question.label',
  add_claim_node: 'onboarding.step.add_claim_node.label',
  attach_source: 'onboarding.step.attach_source.label',
};

const STEP_ACTION_KEYS: Partial<Record<OnboardingStepId, DictKey>> = {
  create_topic: 'onboarding.step.create_topic.action',
  add_claim_node: 'onboarding.step.add_claim_node.action',
  attach_source: 'onboarding.step.attach_source.action',
};

interface StepRowProps {
  stepId: OnboardingStepId;
  completed: boolean;
  active: boolean;
  onClick: () => void;
}

function StepRow({ stepId, completed, active, onClick }: StepRowProps) {
  const t = useT();
  const label = t(STEP_LABEL_KEYS[stepId]);
  const actionKey = STEP_ACTION_KEYS[stepId];
  const hint =
    stepId === 'add_root_question' && !completed
      ? t('onboarding.step.add_root_question.hint')
      : null;

  // Clickable only если есть action AND not completed (completed просто
  // показывает strikethrough). add_root_question - non-clickable hint
  const clickable = !completed && actionKey != null;

  const Inner = (
    <>
      <span className="mt-0.5 shrink-0">
        {completed ? (
          <Check size={14} className="text-ok-700" aria-hidden />
        ) : (
          <Circle
            size={14}
            className={active ? 'text-accent-500' : 'text-ink-400'}
            aria-hidden
          />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={`block text-xs leading-snug ${
            completed
              ? 'text-ink-400 line-through'
              : active
                ? 'font-medium text-ink-900'
                : 'text-ink-700'
          }`}
        >
          {label}
        </span>
        {hint && (
          <span className="mt-0.5 block text-[11px] text-ink-500">{hint}</span>
        )}
      </span>
    </>
  );

  if (clickable) {
    return (
      <li>
        <button
          type="button"
          onClick={onClick}
          aria-label={actionKey ? t(actionKey) : label}
          className={`flex w-full items-start gap-2 rounded-sm p-1.5 text-start transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
            active
              ? 'bg-accent-50 hover:bg-accent-100'
              : 'hover:bg-ink-50'
          }`}
        >
          {Inner}
        </button>
      </li>
    );
  }

  return (
    <li className="flex items-start gap-2 p-1.5">
      {Inner}
    </li>
  );
}

export default OnboardingChecklist;
