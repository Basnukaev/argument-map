import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiGetRaw, ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import type { components } from '@/shared/api/types';

/**
 * Onboarding 4-step checklist - detection логика и dismissed-state.
 *
 * Хранит `onboarding_dismissed` в localStorage (per-device, без бэка - см.
 * backlog: backend whitelist для preferences не трогаем в MVP).
 *
 * Detection:
 *   1. `create_topic` - у user'а есть хотя бы одна тема (createdBy === user.id)
 *   2. `add_root_question` - у этой темы rootNodeId != null (бэк гарантирует
 *      создание корневого QUESTION node при topic create - значит автоматом
 *      done если step 1 done; держим явно для прозрачности)
 *   3. `add_claim_node` - в graph есть хотя бы один CLAIM узел (не QUESTION
 *      root) - первая тема пользователя
 *   4. `attach_source` - в graph есть узел с `inlineCitations.length > 0`
 *      (1 SQL call возвращает это поле на GET /graph)
 *
 * Refresh strategy: hook делает single fetch при mount + при изменении user.id.
 * Не подписывается на granular events (новый topic / node) - widget refetch'ается
 * при следующем mount (например при route change через `<OnboardingChecklist key={...} />`).
 * Для MVP достаточно: пользователь увидит свежее состояние при следующем переходе
 * между страницами либо при manual refresh
 */

export type OnboardingStepId =
  | 'create_topic'
  | 'add_root_question'
  | 'add_claim_node'
  | 'attach_source';

export const ONBOARDING_STEP_ORDER: readonly OnboardingStepId[] = [
  'create_topic',
  'add_root_question',
  'add_claim_node',
  'attach_source',
] as const;

export interface OnboardingStep {
  id: OnboardingStepId;
  completed: boolean;
}

const DISMISSED_KEY = 'onboarding_dismissed';

type PagedTopics = components['schemas']['PagedResponseTopicResponse'];
type Graph = components['schemas']['GraphResponse'];

function readDismissed(): boolean {
  if (typeof window === 'undefined') return false;
  return window.localStorage.getItem(DISMISSED_KEY) === '1';
}

function writeDismissed(value: boolean): void {
  if (typeof window === 'undefined') return;
  if (value) window.localStorage.setItem(DISMISSED_KEY, '1');
  else window.localStorage.removeItem(DISMISSED_KEY);
}

interface ComputedSteps {
  steps: OnboardingStep[];
  completed: number;
}

function buildSteps(input: {
  hasTopic: boolean;
  hasRootQuestion: boolean;
  hasClaimNode: boolean;
  hasAttachedSource: boolean;
}): ComputedSteps {
  const steps: OnboardingStep[] = [
    { id: 'create_topic', completed: input.hasTopic },
    { id: 'add_root_question', completed: input.hasRootQuestion },
    { id: 'add_claim_node', completed: input.hasClaimNode },
    { id: 'attach_source', completed: input.hasAttachedSource },
  ];
  return { steps, completed: steps.filter((s) => s.completed).length };
}

interface OnboardingProgress {
  steps: OnboardingStep[];
  completed: number;
  total: number;
  isDismissed: boolean;
  /** Манипулятор пользователя - спрятать widget принудительно */
  dismiss: () => void;
  /** Сбросить dismissed (для тестов / settings reset) */
  reset: () => void;
  /** Видим ли widget сейчас (NOT dismissed AND user залогинен) */
  isVisible: boolean;
  /** UUID первой темы пользователя - для action linking на graph */
  firstTopicId: string | null;
  /** Loading - первый fetch не закончился. UI прячется пока loading. */
  isLoading: boolean;
}

/**
 * Public hook. Sole consumer - `<OnboardingChecklist />` в App.tsx.
 *
 * Возвращает computed state. Widget сам решает render/hide на основании
 * `isVisible` (false если no user / dismissed).
 *
 * Не делает запросы пока user == null - exit early до auth bootstrap.
 */
export function useOnboardingProgress(): OnboardingProgress {
  const user = useAuthStore((s) => s.user);
  const [isDismissed, setIsDismissed] = useState<boolean>(readDismissed());
  const [computed, setComputed] = useState<ComputedSteps>(() =>
    buildSteps({
      hasTopic: false,
      hasRootQuestion: false,
      hasClaimNode: false,
      hasAttachedSource: false,
    }),
  );
  const [firstTopicId, setFirstTopicId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    // Не fetch'имся пока user не залогинен. На logout - reset state локально.
    // Lint react-hooks/set-state-in-effect отключён точечно: reset case
    // синхронный (deliberate sync с external auth-store), остальные set
    // в then-callback'ах - за scope effect-body
    if (!user) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setComputed(
        buildSteps({
          hasTopic: false,
          hasRootQuestion: false,
          hasClaimNode: false,
          hasAttachedSource: false,
        }),
      );
      setFirstTopicId(null);
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    setIsLoading(true);

    void (async () => {
      try {
        // Step 1: fetch первую страницу тем. owned тема = topic.createdBy === user.id
        // (или role==='ADMIN' - bypass: считаем что у админов тоже первая работа -
        // создание темы как у обычного user'а). Фильтр по createdBy на бэке нет -
        // фильтруем client-side по первой странице (size=20). На практике у нового
        // user'а тем 0-1, fit'ится в первую страницу
        const topicsPage = await apiGetRaw<PagedTopics>(
          '/api/v1/topics?page=0&size=20',
          { signal: controller.signal },
        );
        const items = topicsPage.items ?? [];
        const ownTopics = items.filter((tp) => tp.createdBy === user.id);
        const firstOwn = ownTopics[0];
        const hasTopic = ownTopics.length > 0;

        if (!hasTopic || !firstOwn || !firstOwn.id) {
          setComputed(
            buildSteps({
              hasTopic: false,
              hasRootQuestion: false,
              hasClaimNode: false,
              hasAttachedSource: false,
            }),
          );
          setFirstTopicId(null);
          setIsLoading(false);
          return;
        }

        setFirstTopicId(firstOwn.id);
        // Step 2 - rootNodeId уже в topic response. Backend гарантирует
        // root QUESTION при topic create, но defensive check на null
        const hasRootQuestion = Boolean(firstOwn.rootNodeId);

        // Step 3/4: fetch graph первой темы. Один call - получаем nodes +
        // inlineCitations bulk-loaded (см. ADR Inline citations)
        const graph = await apiGetRaw<Graph>(
          `/api/v1/topics/${firstOwn.id}/graph`,
          { signal: controller.signal },
        );
        const nodes = graph.nodes ?? [];

        const hasClaimNode = nodes.some((n) => n.nodeType === 'CLAIM');
        const hasAttachedSource = nodes.some(
          (n) => (n.inlineCitations ?? []).length > 0,
        );

        setComputed(
          buildSteps({
            hasTopic: true,
            hasRootQuestion,
            hasClaimNode,
            hasAttachedSource,
          }),
        );
        setIsLoading(false);
      } catch (e: unknown) {
        if (controller.signal.aborted) return;
        // 401 - user токен expired, скрываем widget гладко
        if (e instanceof ApiError && e.status === 401) {
          setIsLoading(false);
          return;
        }
        // Прочие ошибки - просто скрываем (НЕ ломаем app shell)
        setIsLoading(false);
      }
    })();

    return () => controller.abort();
  }, [user]);

  const dismiss = useCallback(() => {
    writeDismissed(true);
    setIsDismissed(true);
  }, []);

  const reset = useCallback(() => {
    writeDismissed(false);
    setIsDismissed(false);
  }, []);

  // auto-dismiss при completed === total - widget сам прячется (после
  // celebration toast - в компоненте через setTimeout). Не пишем dismissed
  // в localStorage пока сам widget не вызовет dismiss() - это даёт
  // 3-секундное окно для toast
  const isVisible = useMemo(() => {
    if (!user) return false;
    if (isDismissed) return false;
    if (isLoading) return false;
    return true;
  }, [user, isDismissed, isLoading]);

  return {
    steps: computed.steps,
    completed: computed.completed,
    total: ONBOARDING_STEP_ORDER.length,
    isDismissed,
    dismiss,
    reset,
    isVisible,
    firstTopicId,
    isLoading,
  };
}
