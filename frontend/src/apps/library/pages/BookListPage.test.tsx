import { describe, it, expect, beforeEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { waitForApi } from '@/test/asyncHelpers';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import BookListPage from './BookListPage';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';

function renderPage() {
  return render(
    <MemoryRouter>
      <BookListPage />
    </MemoryRouter>,
  );
}

interface PagedBookFixture {
  items: Array<{
    id: string;
    title: string;
    bookType?: 'BOOK' | 'HADITH_COLLECTION' | 'QURAN' | 'ARTICLE' | 'MANUSCRIPT';
    visibility?: 'PRIVATE' | 'SHARED' | 'PUBLIC';
    language?: string;
    createdBy?: string;
  }>;
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  hasNext?: boolean;
  hasPrev?: boolean;
}

const pagedEmpty = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 1,
  hasNext: false,
  hasPrev: false,
};

function paged(items: PagedBookFixture['items'], opts: Partial<PagedBookFixture> = {}) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: opts.totalElements ?? items.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
    ...opts,
  };
}

describe('BookListPage / Library overview', () => {
  beforeEach(() => {
    // дефолт: empty authorities endpoint (некоторые тесты могут открывать
    // AuthorityFilter dropdown через focus и спровоцировать GET)
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () =>
        HttpResponse.json({ items: [], page: 0, totalElements: 0, hasNext: false }),
      ),
    );
  });

  it('показывает empty state иллюстрированный когда библиотека пустая', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () => HttpResponse.json(pagedEmpty)),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/Библиотека пуста/i)).toBeInTheDocument();
    });
    // должны быть 2 link'а на /admin/shamela: один в hero header (secondary
    // «Импорт из Shamela»), второй - CTA в empty state. Оба валидны
    const importLinks = screen.getAllByRole('link', { name: /Импорт из Shamela/i });
    expect(importLinks.length).toBeGreaterThanOrEqual(1);
    importLinks.forEach((l) => expect(l).toHaveAttribute('href', '/admin/shamela'));
  });

  it('рендерит карточки книг с visibility badge и ссылкой на reader', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged(
            [
              {
                id: 'b1',
                title: 'صحيح البخاري',
                bookType: 'HADITH_COLLECTION',
                visibility: 'PUBLIC',
                language: 'ar',
              },
              {
                id: 'b2',
                title: 'Personal Notes',
                bookType: 'BOOK',
                visibility: 'PRIVATE',
              },
            ],
            { totalElements: 2 },
          ),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });
    expect(screen.getByText('Personal Notes')).toBeInTheDocument();

    const link = screen.getByRole('link', { name: /صحيح البخاري/i });
    expect(link).toHaveAttribute('href', '/books/b1');
  });

  it('debounced search триггерит refetch с ?q=', async () => {
    let lastQuery: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/library/books`, ({ request }) => {
        const url = new URL(request.url);
        lastQuery = url.searchParams.get('q');
        return HttpResponse.json(
          paged(lastQuery ? [{ id: 'bx', title: `Match for ${lastQuery}`, bookType: 'BOOK' }] : []),
        );
      }),
    );
    renderPage();
    await waitForApi(() => {
      // initial fetch без query - empty state
      expect(screen.getByText(/Библиотека пуста/i)).toBeInTheDocument();
    });

    const user = userEvent.setup();
    const input = screen.getByPlaceholderText(/Поиск по названию книги/i);
    await user.type(input, 'Бухари');

    // debounce 300ms - ждём 400ms; act() обёртка чтобы избежать warning
    // про необёрнутый state update внутри setTimeout
    await act(async () => {
      await new Promise((r) => setTimeout(r, 400));
    });
    await waitForApi(() => {
      expect(lastQuery).toBe('Бухари');
    });
  });

  it('«Мои» фильтр - strict createdBy === currentUser.id (не visibility approximation)', async () => {
    // backlog tech debt round 4 #8: «Мои» теперь точно owner-equality,
    // даже если книга PUBLIC/SHARED, она моя если createdBy совпал
    const myUserId = 'user-me';
    const otherUserId = 'user-other';
    useAuthStore.setState({
      user: { id: myUserId, username: 'me', email: 'me@e.com', role: 'USER' },
      initialized: true,
    });

    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged(
            [
              {
                id: 'b1',
                title: 'My Public Book',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                createdBy: myUserId,
              },
              {
                id: 'b2',
                title: 'My Private Notes',
                bookType: 'BOOK',
                visibility: 'PRIVATE',
                createdBy: myUserId,
              },
              {
                id: 'b3',
                title: 'Other User Public',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                createdBy: otherUserId,
              },
            ],
            { totalElements: 3 },
          ),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('My Public Book')).toBeInTheDocument();
    });
    expect(screen.getByText('Other User Public')).toBeInTheDocument();

    const user = userEvent.setup();
    // переключиться на «Мои» - createdBy === myUserId, две моих остаются
    await user.click(screen.getByRole('button', { name: 'Мои' }));
    expect(screen.getByText('My Public Book')).toBeInTheDocument();
    expect(screen.getByText('My Private Notes')).toBeInTheDocument();
    // чужая PUBLIC скрыта (visibility не учитывается)
    expect(screen.queryByText('Other User Public')).not.toBeInTheDocument();

    // обратно на «Все»
    await user.click(screen.getByRole('button', { name: 'Все' }));
    expect(screen.getByText('My Public Book')).toBeInTheDocument();
    expect(screen.getByText('Other User Public')).toBeInTheDocument();
  });

  it('«Мои» фильтр anonymous (user=null) - пустой список', async () => {
    useAuthStore.setState({ user: null, initialized: true });
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged(
            [
              {
                id: 'b1',
                title: 'Public Book',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                createdBy: 'some-owner',
              },
            ],
            { totalElements: 1 },
          ),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Public Book')).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Мои' }));
    // нет owner identity - список пустой, рендерится «не найдено»
    expect(screen.queryByText('Public Book')).not.toBeInTheDocument();
  });

  it('показывает кнопку Load More и аппендит результаты при клике', async () => {
    let callCount = 0;
    server.use(
      http.get(`${BASE}/api/v1/library/books`, ({ request }) => {
        callCount += 1;
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        if (page === 0) {
          return HttpResponse.json({
            items: [{ id: 'b1', title: 'First Book', bookType: 'BOOK', visibility: 'PUBLIC' }],
            page: 0,
            size: 20,
            totalElements: 2,
            totalPages: 2,
            hasNext: true,
            hasPrev: false,
          });
        }
        return HttpResponse.json({
          items: [{ id: 'b2', title: 'Second Book', bookType: 'BOOK', visibility: 'PUBLIC' }],
          page: 1,
          size: 20,
          totalElements: 2,
          totalPages: 2,
          hasNext: false,
          hasPrev: true,
        });
      }),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('First Book')).toBeInTheDocument();
    });
    const loadMore = screen.getByRole('button', { name: /Показать ещё/i });
    expect(loadMore).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(loadMore);
    await waitForApi(() => {
      expect(screen.getByText('Second Book')).toBeInTheDocument();
    });
    expect(screen.getByText('First Book')).toBeInTheDocument();
    expect(callCount).toBeGreaterThanOrEqual(2);
  });
});
