import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useAuthStore } from '@/shared/stores/authStore';
import VoteWidget, { computeOptimisticVote } from './VoteWidget';

const BASE = 'http://test.local';
const TOPIC_ID = 'topic-1';
const VOTE_URL = `/api/v1/topics/${TOPIC_ID}/vote`;
// generic кнопки используют дефолтные generic-лейблы (vote.upvote_action /
// vote.downvote_action), т.к. рендерим без upvoteLabel/downvoteLabel.
const UP = 'Проголосовать за';
const DOWN = 'Проголосовать против';

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

describe('computeOptimisticVote', () => {
  it('первый upvote - score+1, userVote=1', () => {
    const r = computeOptimisticVote({ score: 0, userVote: null }, 1, false);
    expect(r).toEqual({ score: 1, userVote: 1 });
  });

  it('повторный upvote = toggle off, score-1, userVote=null', () => {
    const r = computeOptimisticVote({ score: 2, userVote: 1 }, 1, true);
    expect(r).toEqual({ score: 1, userVote: null });
  });

  it('смена upvote → downvote: score-2 (снять +1, добавить -1)', () => {
    const r = computeOptimisticVote({ score: 3, userVote: 1 }, -1, false);
    expect(r).toEqual({ score: 1, userVote: -1 });
  });

  it('первый downvote: score-1', () => {
    const r = computeOptimisticVote({ score: 0, userVote: null }, -1, false);
    expect(r).toEqual({ score: -1, userVote: -1 });
  });
});

describe('VoteWidget', () => {
  beforeEach(() => {
    setLoggedIn();
  });

  it('рендерит upvote и downvote кнопки + текущий счёт', () => {
    render(<VoteWidget voteUrl={VOTE_URL} score={2} userVote={null} />);

    expect(screen.getByRole('button', { name: UP })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: DOWN })).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('кастомные лейблы кнопок (upvoteLabel/downvoteLabel) переопределяют дефолт', () => {
    render(
      <VoteWidget
        voteUrl={VOTE_URL}
        score={0}
        userVote={null}
        upvoteLabel="Голос за тему"
        downvoteLabel="Голос против темы"
      />,
    );

    expect(screen.getByRole('button', { name: 'Голос за тему' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Голос против темы' })).toBeInTheDocument();
  });

  it('upvote click - POST /vote с weight=1 + onVoteChanged', async () => {
    let received: { weight?: number } | null = null;
    server.use(
      http.post(`${BASE}${VOTE_URL}`, async ({ request }) => {
        received = (await request.json()) as { weight?: number };
        return HttpResponse.json(
          { topicId: TOPIC_ID, upvotes: 3, downvotes: 0, score: 3, userVote: 1 },
          { status: 201 },
        );
      }),
    );

    const onChange = vi.fn();
    render(
      <VoteWidget voteUrl={VOTE_URL} score={2} userVote={null} onVoteChanged={onChange} />,
    );

    await userEvent.click(screen.getByRole('button', { name: UP }));
    await waitForApi(() => {
      expect(onChange.mock.calls.length).toBeGreaterThan(0);
    });

    expect(received).toEqual({ weight: 1 });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ userVote: 1, score: 3 }),
    );
  });

  it('повторный click по уже-upvoted - DELETE /vote (toggle off)', async () => {
    let deleted = false;
    server.use(
      http.delete(`${BASE}${VOTE_URL}`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const onChange = vi.fn();
    render(
      <VoteWidget voteUrl={VOTE_URL} score={2} userVote={1} onVoteChanged={onChange} />,
    );

    await userEvent.click(screen.getByRole('button', { name: UP }));
    await waitForApi(() => {
      expect(deleted).toBe(true);
    });

    expect(deleted).toBe(true);
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ userVote: null, score: 1 }),
    );
  });

  it('без onVoteChanged: после голоса счёт держится на серверном значении (не отскакивает к stale props)', async () => {
    // Регрессия: списки рендерят виджет БЕЗ onVoteChanged → props
    // score/userVote никогда не обновляются. Раньше эффект синхронизации
    // props→local перефайривался когда pending → false и затирал
    // оптимистичный local устаревшими props → счёт «отскакивал» к 2.
    server.use(
      http.post(`${BASE}${VOTE_URL}`, () =>
        HttpResponse.json(
          { topicId: TOPIC_ID, upvotes: 3, downvotes: 0, score: 3, userVote: 1 },
          { status: 201 },
        ),
      ),
    );

    render(<VoteWidget voteUrl={VOTE_URL} score={2} userVote={null} />);

    const upBtn = screen.getByRole('button', { name: UP });
    await userEvent.click(upBtn);

    // POST вернул score=3 → отображается 3 и держится (не возвращается к 2).
    await waitForApi(() => {
      expect(screen.getByText('3')).toBeInTheDocument();
    });
    // Запрос завершён, pending=false — счёт остаётся 3, стрелка активна.
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.queryByText('2')).not.toBeInTheDocument();
    expect(upBtn).toHaveAttribute('aria-pressed', 'true');
  });

  it('без auth - не делает запрос, показывает toast info', async () => {
    setLoggedOut();
    let calledApi = false;
    server.use(
      http.post(`${BASE}${VOTE_URL}`, () => {
        calledApi = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );

    render(<VoteWidget voteUrl={VOTE_URL} score={0} userVote={null} />);

    await userEvent.click(screen.getByRole('button', { name: UP }));
    // даём msw шанс сработать (отрицательный тест) - но запроса быть не должно
    await new Promise((r) => setTimeout(r, 50));
    expect(calledApi).toBe(false);
  });
});
