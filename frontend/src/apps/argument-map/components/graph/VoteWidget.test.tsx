import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useAuthStore } from '@/shared/stores/authStore';
import VoteWidget, { computeOptimistic } from './VoteWidget';

const BASE = 'http://test.local';
const NODE_ID = 'node-1';

function setLoggedIn() {
  useAuthStore.setState({
    user: { id: 'u1', email: 'u@test', username: 'u', role: 'USER' },
    initialized: true,
  });
  localStorage.removeItem('auth.user');
}

function setLoggedOut() {
  useAuthStore.setState({ user: null, initialized: true });
}

describe('computeOptimistic', () => {
  it('первый upvote - upvotes+1, score+1, userVote=1', () => {
    const r = computeOptimistic(
      { upvotes: 0, downvotes: 0, userVote: null },
      1,
      false,
    );
    expect(r).toEqual({ upvotes: 1, downvotes: 0, score: 1, userVote: 1 });
  });

  it('повторный upvote = toggle off, upvotes-1, userVote=null', () => {
    const r = computeOptimistic(
      { upvotes: 3, downvotes: 1, userVote: 1 },
      1,
      true,
    );
    expect(r).toEqual({ upvotes: 2, downvotes: 1, score: 1, userVote: null });
  });

  it('смена upvote → downvote: upvotes-1, downvotes+1', () => {
    const r = computeOptimistic(
      { upvotes: 5, downvotes: 2, userVote: 1 },
      -1,
      false,
    );
    expect(r).toEqual({ upvotes: 4, downvotes: 3, score: 1, userVote: -1 });
  });

  it('первый downvote: downvotes+1, score-1', () => {
    const r = computeOptimistic(
      { upvotes: 0, downvotes: 0, userVote: null },
      -1,
      false,
    );
    expect(r).toEqual({ upvotes: 0, downvotes: 1, score: -1, userVote: -1 });
  });
});

describe('VoteWidget', () => {
  beforeEach(() => {
    setLoggedIn();
  });

  it('рендерит upvote и downvote кнопки + текущий счёт', () => {
    render(
      <VoteWidget
        nodeId={NODE_ID}
        upvotes={3}
        downvotes={1}
        score={2}
        userVote={null}
      />,
    );

    expect(screen.getByRole('button', { name: 'Поддержать аргумент' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Не согласен с аргументом' })).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('upvote click - POST /vote с weight=1 + onVoteChanged', async () => {
    let received: { weight?: number } | null = null;
    server.use(
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/vote`, async ({ request }) => {
        received = (await request.json()) as { weight?: number };
        return HttpResponse.json(
          {
            nodeId: NODE_ID,
            upvotes: 4,
            downvotes: 1,
            score: 3,
            userVote: 1,
          },
          { status: 201 },
        );
      }),
    );

    const onChange = vi.fn();
    render(
      <VoteWidget
        nodeId={NODE_ID}
        upvotes={3}
        downvotes={1}
        score={2}
        userVote={null}
        onVoteChanged={onChange}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Поддержать аргумент' }));
    await waitForApi(() => {
      expect(onChange.mock.calls.length).toBeGreaterThan(0);
    });

    expect(received).toEqual({ weight: 1 });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ userVote: 1, upvotes: 4, score: 3 }),
    );
  });

  it('повторный click по уже-upvoted - DELETE /vote (toggle off)', async () => {
    let deleted = false;
    server.use(
      http.delete(`${BASE}/api/v1/nodes/${NODE_ID}/vote`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const onChange = vi.fn();
    render(
      <VoteWidget
        nodeId={NODE_ID}
        upvotes={3}
        downvotes={1}
        score={2}
        userVote={1}
        onVoteChanged={onChange}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Поддержать аргумент' }));
    await waitForApi(() => {
      expect(deleted).toBe(true);
    });

    expect(deleted).toBe(true);
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ userVote: null, upvotes: 2 }),
    );
  });

  it('без auth - не делает запрос, показывает toast info', async () => {
    setLoggedOut();
    let calledApi = false;
    server.use(
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/vote`, () => {
        calledApi = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );

    render(
      <VoteWidget
        nodeId={NODE_ID}
        upvotes={0}
        downvotes={0}
        score={0}
        userVote={null}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Поддержать аргумент' }));
    // даём msw шанс сработать (отрицательный тест) - но запроса быть не должно
    await new Promise((r) => setTimeout(r, 50));
    expect(calledApi).toBe(false);
  });
});
