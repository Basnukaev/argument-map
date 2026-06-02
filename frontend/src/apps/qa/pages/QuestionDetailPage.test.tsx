import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import QuestionDetailPage from './QuestionDetailPage';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const QUESTION_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const ASKER_ID = '11111111-1111-1111-1111-111111111111';
const OTHER_ID = '22222222-2222-2222-2222-222222222222';

const QUESTION = {
  id: QUESTION_ID,
  title: 'Можно ли так делать?',
  body: 'Тело вопроса',
  status: 'OPEN' as const,
  askedBy: ASKER_ID,
  createdAt: '2026-05-01T10:00:00Z',
  updatedAt: '2026-05-01T10:00:00Z',
  voteScore: 0,
  userVote: 0,
};

function setUser(id: string, role: 'USER' | 'ADMIN') {
  useAuthStore.setState({
    user: { id, username: 'u', email: 'u@x.local', role },
    accessToken: 'fake-jwt',
    isLoading: false,
    initialized: true,
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/qa/${QUESTION_ID}`]}>
      <Routes>
        <Route path="/qa/:questionId" element={<QuestionDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  if (typeof window !== 'undefined') {
    window.sessionStorage.clear();
  }
  server.use(
    http.get(`${BASE}/api/v1/questions/${QUESTION_ID}`, () =>
      HttpResponse.json(QUESTION),
    ),
    // useViewTracking POST (best-effort, silent fail) — но onUnhandledRequest:
    // 'error' требует явный handler
    http.post(`${BASE}/api/v1/questions/${QUESTION_ID}/views`, () =>
      HttpResponse.json({}, { status: 200 }),
    ),
    // AnswersSection + QuestionCitationsSection грузятся на той же странице
    http.get(`${BASE}/api/v1/questions/${QUESTION_ID}/answers`, () =>
      HttpResponse.json([]),
    ),
    http.get(`${BASE}/api/v1/questions/${QUESTION_ID}/sources`, () =>
      HttpResponse.json([]),
    ),
    http.get(`${BASE}/api/v1/sources`, () =>
      HttpResponse.json({
        items: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      }),
    ),
  );
});

describe('QuestionDetailPage — ownership gating кнопки удаления', () => {
  it('автор вопроса видит меню действий (удаление)', async () => {
    setUser(ASKER_ID, 'USER');
    renderPage();

    await waitForApi(() => {
      expect(
        screen.getByRole('heading', { name: /Можно ли так делать/i }),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole('button', { name: /Действия с вопросом/i }),
    ).toBeInTheDocument();
  });

  it('ADMIN (не автор) видит меню действий', async () => {
    setUser(OTHER_ID, 'ADMIN');
    renderPage();

    await waitForApi(() => {
      expect(
        screen.getByRole('heading', { name: /Можно ли так делать/i }),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole('button', { name: /Действия с вопросом/i }),
    ).toBeInTheDocument();
  });

  it('обычный пользователь (не автор, не ADMIN) НЕ видит меню действий', async () => {
    setUser(OTHER_ID, 'USER');
    renderPage();

    await waitForApi(() => {
      expect(
        screen.getByRole('heading', { name: /Можно ли так делать/i }),
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('button', { name: /Действия с вопросом/i }),
    ).not.toBeInTheDocument();
  });

  it('анонимный пользователь НЕ видит меню действий', async () => {
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isLoading: false,
      initialized: true,
    });
    renderPage();

    await waitForApi(() => {
      expect(
        screen.getByRole('heading', { name: /Можно ли так делать/i }),
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('button', { name: /Действия с вопросом/i }),
    ).not.toBeInTheDocument();
  });
});
