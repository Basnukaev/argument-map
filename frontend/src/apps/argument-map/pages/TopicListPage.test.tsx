import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { waitForApi } from '@/test/asyncHelpers';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import TopicListPage from './TopicListPage';

const BASE = 'http://test.local';

function renderPage() {
  return render(
    <MemoryRouter>
      <TopicListPage />
    </MemoryRouter>,
  );
}

// helper - mocks возвращают PagedResponse, page/size игнорируются (msw
// при handlers без query matching возвращает ответ на любой ?page=&size=)
const pagedEmpty = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 1,
  hasNext: false,
  hasPrev: false,
};

describe('TopicListPage', () => {
  it('показывает "Загрузка" пока запрос идёт', () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, async () => {
        await new Promise((r) => setTimeout(r, 1000));
        return HttpResponse.json(pagedEmpty);
      }),
    );
    renderPage();
    expect(screen.getByText('Загрузка')).toBeInTheDocument();
  });

  it('показывает empty-state на пустом списке', async () => {
    server.use(http.get(`${BASE}/api/v1/topics`, () => HttpResponse.json(pagedEmpty)));
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/Пока нет тем/i)).toBeInTheDocument();
    });
  });

  it('рендерит карточки тем со ссылкой на граф', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json({
          items: [
            {
              id: 't1',
              title: 'Дозволенность мавлида',
              description: 'Разбор позиций',
              createdAt: '2026-05-01T10:00:00Z',
            },
            {
              id: 't2',
              title: 'Виды бид',
              createdAt: '2026-05-02T11:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Дозволенность мавлида')).toBeInTheDocument();
    });
    expect(screen.getByText('Виды бид')).toBeInTheDocument();
    expect(screen.getByText('Разбор позиций')).toBeInTheDocument();

    const link = screen.getByRole('link', { name: /Дозволенность мавлида/i });
    expect(link).toHaveAttribute('href', '/topics/t1');
  });

  it('показывает ошибку при 5xx с Problem Details', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Ошибка сервера', status: 500, detail: 'БД недоступна' },
          { status: 500 },
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Ошибка')).toBeInTheDocument();
    });
    expect(screen.getByText(/БД недоступна/)).toBeInTheDocument();
  });
});
