import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { waitForApi } from '@/test/asyncHelpers';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import CreateTopicPage from './CreateTopicPage';

const BASE = 'http://test.local';

function renderWithRouter() {
  return render(
    <MemoryRouter initialEntries={['/topics/new']}>
      <Routes>
        <Route path="/topics/new" element={<CreateTopicPage />} />
        <Route path="/topics/:id" element={<div data-testid="graph-page">Graph page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CreateTopicPage', () => {
  it('кнопка "Создать" задизейблена пока обязательные поля пусты', () => {
    renderWithRouter();
    expect(screen.getByRole('button', { name: 'Создать' })).toBeDisabled();
  });

  it('успешный POST редиректит на страницу графа созданной темы', async () => {
    let receivedBody: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/topics`, async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json({
          id: 'new-topic-id',
          title: 'X',
          rootNodeId: 'root-id',
          createdBy: 'u',
          createdAt: '2026-05-03T00:00:00Z',
        });
      }),
    );

    const user = userEvent.setup();
    renderWithRouter();

    await user.type(screen.getByLabelText('Название'), 'Тестовая тема');
    await user.type(screen.getByLabelText('Описание (необязательно)'), 'Описание');
    await user.type(screen.getByLabelText('Корневой вопрос'), 'Это вопрос?');

    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitForApi(() => {
      expect(screen.getByTestId('graph-page')).toBeInTheDocument();
    });

    expect(receivedBody).toEqual({
      title: 'Тестовая тема',
      description: 'Описание',
      rootQuestion: 'Это вопрос?',
    });
  });

  it('показывает field-errors при 400 с errors[]', async () => {
    server.use(
      http.post(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          {
            type: 'https://example.com/errors/validation',
            title: 'Ошибка валидации',
            status: 400,
            errors: [{ field: 'title', message: 'не должно быть пустым' }],
          },
          { status: 400 },
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithRouter();

    await user.type(screen.getByLabelText('Название'), 'X');
    await user.type(screen.getByLabelText('Корневой вопрос'), 'Q');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitForApi(() => {
      expect(screen.getByText('не должно быть пустым')).toBeInTheDocument();
    });
  });

  it('показывает общую ошибку при 422 без errors[]', async () => {
    server.use(
      http.post(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          {
            type: 'https://example.com/errors/something',
            title: 'Бизнес-ошибка',
            status: 422,
            detail: 'Что-то пошло не так',
          },
          { status: 422 },
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithRouter();

    await user.type(screen.getByLabelText('Название'), 'X');
    await user.type(screen.getByLabelText('Корневой вопрос'), 'Q');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitForApi(() => {
      expect(screen.getByText('Что-то пошло не так')).toBeInTheDocument();
    });
  });
});
