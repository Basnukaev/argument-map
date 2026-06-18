import { describe, it, expect, beforeEach } from 'vitest';
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { waitForApi } from '@/test/asyncHelpers';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router';
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

/** Показывает текущий pathname+search - для проверки навигации после клика. */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="location">{loc.pathname + loc.search}</div>;
}

/** Рендерит BookListPage на /books и зеркало location - для проверки
 * куда уводит карточка (reader vs обозреватель хадисов). */
function renderWithRoutes() {
  return render(
    <MemoryRouter initialEntries={['/books']}>
      <LocationProbe />
      <Routes>
        <Route path="/books" element={<BookListPage />} />
        <Route path="/books/:bookId" element={<div>READER</div>} />
        <Route path="/hadith" element={<div>HADITH EXPLORER</div>} />
      </Routes>
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
    coverUrl?: string;
    contentKind?: 'TEXT_ONLY' | 'TEXT_AND_FILE' | 'FILE_ONLY';
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

    // обычная книга (BOOK) ведёт в ридер через статичный link
    const link = screen.getByRole('link', { name: /Personal Notes/i });
    expect(link).toHaveAttribute('href', '/books/b2');
    // HADITH_COLLECTION (под-проект #2.B) - не link в ридер, а кнопка-обозреватель
    expect(screen.queryByRole('link', { name: /صحيح البخاري/i })).not.toBeInTheDocument();
    // a11y: aria-label кнопки title-qualified (название сборника + действие),
    // чтобы accessible name отличался между карточками
    expect(
      screen.getByRole('button', { name: /صحيح البخاري — Открыть в обозревателе хадисов/i }),
    ).toBeInTheDocument();
  });

  it('книга с coverUrl → рендерит обложку <img>; без coverUrl → letter-avatar', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged(
            [
              {
                id: 'b1',
                title: 'With Cover',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                coverUrl: 'https://archive.org/cover.jpg',
              },
              {
                id: 'b2',
                title: 'No Cover',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
              },
            ],
            { totalElements: 2 },
          ),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('With Cover')).toBeInTheDocument();
    });

    // карточка с coverUrl: обложка-img (decorative, role=presentation) с этим src
    const covers = screen.getAllByRole('presentation');
    expect(covers.some((img) => img.getAttribute('src') === 'https://archive.org/cover.jpg')).toBe(true);

    // карточка без coverUrl показывает letter-avatar (первая буква title)
    const link = screen.getByRole('link', { name: /No Cover/i });
    expect(within(link).getByText('N')).toBeInTheDocument();
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

  it('пагинация: «следующая» грузит вторую страницу и ЗАМЕНЯЕТ список', async () => {
    let callCount = 0;
    server.use(
      http.get(`${BASE}/api/v1/library/books`, ({ request }) => {
        callCount += 1;
        const url = new URL(request.url);
        // бэк 0-based: page=0 — первая, page=1 — вторая (UI ?page=2)
        const page = Number(url.searchParams.get('page') ?? '0');
        if (page === 0) {
          return HttpResponse.json({
            items: [{ id: 'b1', title: 'First Book', bookType: 'BOOK', visibility: 'PUBLIC' }],
            page: 0,
            size: 20,
            totalElements: 40,
            totalPages: 2,
            hasNext: true,
            hasPrev: false,
          });
        }
        return HttpResponse.json({
          items: [{ id: 'b2', title: 'Second Book', bookType: 'BOOK', visibility: 'PUBLIC' }],
          page: 1,
          size: 20,
          totalElements: 40,
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
    const next = screen.getByRole('button', { name: 'Следующая страница' });
    expect(next).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(next);
    await waitForApi(() => {
      expect(screen.getByText('Second Book')).toBeInTheDocument();
    });
    // вторая страница ЗАМЕНИЛА первую (REPLACE, не append)
    expect(screen.queryByText('First Book')).not.toBeInTheDocument();
    expect(callCount).toBeGreaterThanOrEqual(2);
  });

  it('HADITH_COLLECTION карточка показывает бейдж «Сборник хадисов» и это кнопка, не link', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged([{ id: 'b1', title: 'صحيح البخاري', bookType: 'HADITH_COLLECTION', visibility: 'PUBLIC' }]),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });
    // карточка = button (резолв таргета по клику), не статичный link на ридер
    const card = screen.getByRole('button', { name: /Открыть в обозревателе хадисов/i });
    // бейдж типа внутри карточки («Сборник хадисов» также есть в type-чипе,
    // поэтому скоупим к карточке через within)
    expect(within(card).getByText('Сборник хадисов')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /صحيح البخاري/i })).not.toBeInTheDocument();
  });

  it('FILE_ONLY книга показывает chip «Только PDF»; TEXT_AND_FILE / TEXT_ONLY - нет', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged(
            [
              {
                id: 'b1',
                title: 'Scan Only',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                contentKind: 'FILE_ONLY',
              },
              {
                id: 'b2',
                title: 'Text And File',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                contentKind: 'TEXT_AND_FILE',
              },
              {
                id: 'b3',
                title: 'Text Only',
                bookType: 'BOOK',
                visibility: 'PUBLIC',
                contentKind: 'TEXT_ONLY',
              },
            ],
            { totalElements: 3 },
          ),
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Scan Only')).toBeInTheDocument();
    });

    // chip «Только PDF» только у FILE_ONLY карточки
    const fileOnlyCard = screen.getByRole('link', { name: /Scan Only/i });
    expect(within(fileOnlyCard).getByText('Только PDF')).toBeInTheDocument();

    const textAndFileCard = screen.getByRole('link', { name: /Text And File/i });
    expect(within(textAndFileCard).queryByText('Только PDF')).not.toBeInTheDocument();

    const textOnlyCard = screen.getByRole('link', { name: /Text Only/i });
    expect(within(textOnlyCard).queryByText('Только PDF')).not.toBeInTheDocument();
  });

  it('клик по HADITH_COLLECTION резолвит by-book и навигирует в /hadith?collectionId=', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged([{ id: 'b1', title: 'Bukhari', bookType: 'HADITH_COLLECTION', visibility: 'PUBLIC' }]),
        ),
      ),
      http.get(`${BASE}/api/v1/hadith/collections/by-book/b1`, () =>
        HttpResponse.json({ id: 'col-99', slug: 'bukhari', bookId: 'b1' }),
      ),
    );
    renderWithRoutes();
    await waitForApi(() => {
      expect(screen.getByText('Bukhari')).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Открыть в обозревателе хадисов/i }));

    await waitForApi(() => {
      expect(screen.getByText('HADITH EXPLORER')).toBeInTheDocument();
    });
    expect(screen.getByTestId('location')).toHaveTextContent('/hadith?collectionId=col-99');
  });

  it('HADITH_COLLECTION с 404 by-book - defensive fallback в обычный ридер', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(
          paged([{ id: 'b1', title: 'No Bridge', bookType: 'HADITH_COLLECTION', visibility: 'PUBLIC' }]),
        ),
      ),
      http.get(`${BASE}/api/v1/hadith/collections/by-book/b1`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404 },
          { status: 404 },
        ),
      ),
    );
    renderWithRoutes();
    await waitForApi(() => {
      expect(screen.getByText('No Bridge')).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Открыть в обозревателе хадисов/i }));

    await waitForApi(() => {
      expect(screen.getByText('READER')).toBeInTheDocument();
    });
    expect(screen.getByTestId('location')).toHaveTextContent('/books/b1');
  });
});
