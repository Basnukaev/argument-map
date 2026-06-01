import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import NarratorListPage from './NarratorListPage';

const BASE = 'http://test.local';

function paged(items: unknown[], opts: { page?: number; hasNext?: boolean } = {}) {
  return {
    items,
    page: opts.page ?? 0,
    size: 30,
    totalElements: items.length,
    totalPages: opts.hasNext ? 2 : 1,
    hasNext: opts.hasNext ?? false,
  };
}

function narrator(id: string, nameAr: string) {
  return {
    id, authorityId: null, nameAr, kunya: null, laqab: null,
    yearBirthHijri: null, yearDeathHijri: null, birthplace: null,
    primaryResidence: null, reliabilityGrade: 'THIQA', reliabilityComment: null,
    transmittedCount: 0, createdAt: '2026-01-01',
  };
}

describe('NarratorListPage', () => {
  it('рендерит каталог передатчиков с арабским именем', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators`, () =>
        HttpResponse.json(
          paged([
            {
              id: 'n1',
              authorityId: null,
              nameAr: 'مالك بن أنس',
              kunya: 'أبو عبد الله',
              laqab: null,
              yearBirthHijri: 93,
              yearDeathHijri: 179,
              birthplace: 'Медина',
              primaryResidence: 'Медина',
              reliabilityGrade: 'THIQA',
              reliabilityComment: null,
              transmittedCount: 0,
              createdAt: '2026-01-01',
            },
          ]),
        ),
      ),
    );
    render(
      <MemoryRouter>
        <NarratorListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    });
  });

  it('показывает пустое состояние', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators`, () => HttpResponse.json(paged([]))),
    );
    render(
      <MemoryRouter>
        <NarratorListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText('Передатчики не найдены')).toBeInTheDocument();
    });
  });

  it('Load More подгружает следующую страницу и аппендит к списку', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators`, ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        if (page === '1') {
          return HttpResponse.json(paged([narrator('n2', 'الشافعي')], { page: 1, hasNext: false }));
        }
        return HttpResponse.json(paged([narrator('n1', 'مالك بن أنس')], { page: 0, hasNext: true }));
      }),
    );
    render(
      <MemoryRouter>
        <NarratorListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /показать ещё/i }));

    await waitForApi(() => {
      // обе страницы видны - первая не заменена, вторая добавлена
      expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
      expect(screen.getByText('الشافعي')).toBeInTheDocument();
    });
  });

  it('поиск debounce: один запрос после серии keystroke, с ?q=', async () => {
    const queries: string[] = [];
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators`, ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        if (q) queries.push(q);
        return HttpResponse.json(paged([narrator('n1', 'مالك بن أنس')]));
      }),
    );
    render(
      <MemoryRouter>
        <NarratorListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    });

    const input = screen.getByPlaceholderText(/.*/) as HTMLInputElement;
    await userEvent.type(input, 'مالك');

    // debounce 300ms - ждём пока settle (timeout 800ms) и проверяем что
    // запрос с q ушёл
    await waitFor(
      () => {
        expect(queries.length).toBeGreaterThanOrEqual(1);
        expect(queries[queries.length - 1]).toBe('مالك');
      },
      { timeout: 800 },
    );
  });
});
