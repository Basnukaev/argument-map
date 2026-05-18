import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import OnboardingChecklist from './OnboardingChecklist';
import { useAuthStore } from '@/shared/stores/authStore';

const API = 'http://test.local';
const USER_ID = '11111111-1111-1111-1111-111111111111';
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

function renderWidget() {
  return render(
    <MemoryRouter>
      <OnboardingChecklist />
    </MemoryRouter>,
  );
}

describe('OnboardingChecklist', () => {
  beforeEach(() => {
    window.localStorage.removeItem('onboarding_dismissed');
    window.localStorage.removeItem('auth.user');
    setAnonymous();
  });

  it('user не залогинен - ничего не рендерится', () => {
    const { container } = renderWidget();
    expect(container.firstChild).toBeNull();
  });

  it('user без тем - показан widget со всеми 4 шагами не done', async () => {
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
    renderWidget();

    await waitForApi(() => {
      expect(screen.getByText('Начни работу')).toBeInTheDocument();
    });

    expect(screen.getByText('Создай первую тему')).toBeInTheDocument();
    // progress 0 из 4
    expect(screen.getByText('0 из 4')).toBeInTheDocument();
  });

  it('dismiss button скрывает widget + пишет localStorage', async () => {
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
    renderWidget();

    await waitForApi(() => {
      expect(screen.getByText('Начни работу')).toBeInTheDocument();
    });

    // 2 X-кнопки (collapse chevron + dismiss X). Берём dismiss по title
    const dismissBtn = screen.getAllByTitle('Скрыть подсказку')[0];
    expect(dismissBtn).toBeDefined();
    await userEvent.click(dismissBtn!);

    expect(screen.queryByText('Начни работу')).not.toBeInTheDocument();
    expect(window.localStorage.getItem('onboarding_dismissed')).toBe('1');
  });

  it('тема + CLAIM + source - все 4 done, progressbar 100%', async () => {
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
            { id: ROOT_NODE_ID, nodeType: 'QUESTION', content: 'Q' },
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
    renderWidget();

    await waitForApi(() => {
      expect(screen.getByText('4 из 4')).toBeInTheDocument();
    });

    const progressbar = screen.getByRole('progressbar');
    expect(progressbar).toHaveAttribute('aria-valuenow', '4');
    expect(progressbar).toHaveAttribute('aria-valuemax', '4');
  });
});
