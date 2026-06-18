import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import NarratorListPage from './NarratorListPage';

const BASE = 'http://test.local';

function paged(
  items: unknown[],
  opts: { page?: number; hasNext?: boolean; totalPages?: number } = {},
) {
  const page = opts.page ?? 0;
  const totalPages = opts.totalPages ?? (opts.hasNext ? 2 : 1);
  return {
    items,
    page,
    size: 30,
    totalElements: totalPages * 30,
    totalPages,
    hasNext: opts.hasNext ?? page < totalPages - 1,
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

  it('пагинация: клик «следующая» грузит вторую страницу и ЗАМЕНЯЕТ список', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators`, ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        // бэк 0-based: page=1 = вторая страница (UI ?page=2). Обе страницы
        // одного набора (totalPages=2) — пагинация остаётся видимой.
        if (page === '1') {
          return HttpResponse.json(paged([narrator('n2', 'الشافعي')], { page: 1, totalPages: 2 }));
        }
        return HttpResponse.json(paged([narrator('n1', 'مالك بن أنس')], { page: 0, totalPages: 2 }));
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

    await userEvent.click(screen.getByRole('button', { name: 'Следующая страница' }));

    await waitForApi(() => {
      // вторая страница ЗАМЕНИЛА первую (REPLACE, не append)
      expect(screen.getByText('الشافعي')).toBeInTheDocument();
      expect(screen.queryByText('مالك بن أنس')).not.toBeInTheDocument();
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
    // 4 символа печатаются быстрее 300ms debounce → промежуточные
    // keystroke (م/ما/مال) НЕ должны слать запрос, только финальный مالك.
    await userEvent.type(input, 'مالك');

    // Ждём пока финальный запрос придёт (debounce 300ms, timeout 800ms)
    await waitFor(
      () => {
        expect(queries[queries.length - 1]).toBe('مالك');
      },
      { timeout: 800 },
    );

    // Suppression-проверка (ловит регрессию debounce): после settle прошёл
    // РОВНО один q-запрос, а не по одному на keystroke. Без debounce было
    // бы 4 (م, ما, مال, مالك). Даём ещё запас времени чтобы добить хвост.
    await new Promise((r) => setTimeout(r, 350));
    expect(queries).toEqual(['مالك']);
  });
});
