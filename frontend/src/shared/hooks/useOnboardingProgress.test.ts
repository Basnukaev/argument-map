import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useAuthStore } from '@/shared/stores/authStore';
import { useOnboardingProgress } from './useOnboardingProgress';
import { waitForApi } from '@/test/asyncHelpers';

const API = 'http://test.local';
const USER_ID = '11111111-1111-1111-1111-111111111111';
const OTHER_USER_ID = '22222222-2222-2222-2222-222222222222';
const TOPIC_ID = '33333333-3333-3333-3333-333333333333';
const ROOT_NODE_ID = '44444444-4444-4444-4444-444444444444';

function setAuthenticated(): void {
  useAuthStore.setState({
    user: {
      id: USER_ID,
      username: 'test',
      email: 'test@example.com',
      role: 'USER',
    },
    accessToken: 'tok',
    initialized: true,
    isLoading: false,
  });
}

function setAnonymous(): void {
  useAuthStore.setState({
    user: null,
    accessToken: null,
    initialized: true,
    isLoading: false,
  });
}

describe('useOnboardingProgress', () => {
  beforeEach(() => {
    window.localStorage.removeItem('onboarding_dismissed');
    window.localStorage.removeItem('auth.user');
    setAnonymous();
  });

  it('user не залогинен - isVisible=false, не делает fetch', async () => {
    const { result } = renderHook(() => useOnboardingProgress());
    expect(result.current.isVisible).toBe(false);
    expect(result.current.completed).toBe(0);
    expect(result.current.total).toBe(4);
  });

  it('пользователь без тем - все 4 шага не выполнены', async () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );

    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());

    await waitForApi(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.completed).toBe(0);
    expect(result.current.steps[0]?.completed).toBe(false);
    expect(result.current.firstTopicId).toBeNull();
    expect(result.current.isVisible).toBe(true);
  });

  it('тема есть + root QUESTION + CLAIM + source attached - все 4 done', async () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [
            {
              id: TOPIC_ID,
              title: 'Topic',
              createdBy: USER_ID,
              rootNodeId: ROOT_NODE_ID,
              visibility: 'PRIVATE',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        }),
      ),
      http.get(`${API}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json({
          topic: { id: TOPIC_ID, createdBy: USER_ID, rootNodeId: ROOT_NODE_ID },
          nodes: [
            { id: ROOT_NODE_ID, nodeType: 'QUESTION', content: 'Q?' },
            {
              id: 'claim-1',
              nodeType: 'CLAIM',
              content: 'C',
              inlineCitations: [{ ordinal: 1, sourceId: 's1', title: 't' }],
            },
          ],
          edges: [],
        }),
      ),
    );

    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());

    await waitForApi(() => {
      expect(result.current.completed).toBe(4);
    });

    expect(result.current.steps.every((s) => s.completed)).toBe(true);
    expect(result.current.firstTopicId).toBe(TOPIC_ID);
  });

  it('тема создана + CLAIM, но source не привязан - 3 из 4', async () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [
            {
              id: TOPIC_ID,
              title: 'Topic',
              createdBy: USER_ID,
              rootNodeId: ROOT_NODE_ID,
              visibility: 'PRIVATE',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        }),
      ),
      http.get(`${API}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json({
          topic: { id: TOPIC_ID, createdBy: USER_ID, rootNodeId: ROOT_NODE_ID },
          nodes: [
            { id: ROOT_NODE_ID, nodeType: 'QUESTION', content: 'Q?' },
            { id: 'claim-1', nodeType: 'CLAIM', content: 'C' },
          ],
          edges: [],
        }),
      ),
    );

    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());

    await waitForApi(() => {
      expect(result.current.completed).toBe(3);
    });
    expect(result.current.steps[3]?.completed).toBe(false);
  });

  it('темы есть, но не свои (createdBy другой user) - все 4 шага не выполнены', async () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [
            {
              id: TOPIC_ID,
              title: 'Foreign topic',
              createdBy: OTHER_USER_ID,
              rootNodeId: ROOT_NODE_ID,
              visibility: 'PUBLIC',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );

    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());

    await waitForApi(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.completed).toBe(0);
    expect(result.current.firstTopicId).toBeNull();
  });

  it('dismiss() пишет localStorage и isVisible=false', async () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );

    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());

    await waitForApi(() => {
      expect(result.current.isVisible).toBe(true);
    });

    act(() => {
      result.current.dismiss();
    });

    expect(result.current.isDismissed).toBe(true);
    expect(result.current.isVisible).toBe(false);
    expect(window.localStorage.getItem('onboarding_dismissed')).toBe('1');
  });

  it('persisted dismissed=1 - не показывается с момента mount', () => {
    server.use(
      http.get(`${API}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );
    window.localStorage.setItem('onboarding_dismissed', '1');
    setAuthenticated();
    const { result } = renderHook(() => useOnboardingProgress());
    expect(result.current.isDismissed).toBe(true);
    expect(result.current.isVisible).toBe(false);
  });
});
