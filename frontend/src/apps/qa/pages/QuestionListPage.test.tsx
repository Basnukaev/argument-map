import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import QuestionListPage from './QuestionListPage';

const BASE = 'http://test.local';
const QUESTIONS_URL = `${BASE}/api/v1/questions`;

function makeQuestion(id: string, title: string) {
  return {
    id,
    title,
    body: 'тело',
    status: 'OPEN' as const,
    askedBy: 'someone',
    createdAt: '2026-05-01T10:00:00Z',
    updatedAt: '2026-05-01T10:00:00Z',
    voteScore: 0,
    userVote: 0,
  };
}

function pagedResponse(items: ReturnType<typeof makeQuestion>[], hasNext: boolean, page = 0) {
  return {
    items,
    page,
    size: 20,
    totalElements: hasNext ? items.length + 1 : items.length,
    totalPages: hasNext ? page + 2 : page + 1,
    hasNext,
    hasPrev: page > 0,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <QuestionListPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
});

describe('QuestionListPage — переход на страницу: ошибка', () => {
  it('при сбое перехода на страницу показывает осмысленное сообщение об ошибке, а не подзаголовок-строку', async () => {
    server.use(
      http.get(QUESTIONS_URL, ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        if (page === '0') {
          return HttpResponse.json(
            pagedResponse([makeQuestion('q1', 'Первый вопрос')], true),
          );
        }
        // page 1 (переход на стр.2) падает с ProblemDetails без detail/title →
        // formatApiError упадёт на fallbackError.
        return HttpResponse.json(
          { type: 'about:blank', title: '', status: 500 },
          { status: 500 },
        );
      }),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('Первый вопрос')).toBeInTheDocument();
    });

    await userEvent.click(
      await screen.findByRole('button', { name: 'Следующая страница' }),
    );

    // Fallback — осмысленный load_failed, а НЕ подзаголовок
    // «вопросов в обсуждении» (старый баг).
    await waitForApi(() => {
      expect(screen.getByText(/Не удалось загрузить вопросы/i)).toBeInTheDocument();
    });
    expect(screen.queryByText('вопросов в обсуждении')).not.toBeInTheDocument();
  });
});
