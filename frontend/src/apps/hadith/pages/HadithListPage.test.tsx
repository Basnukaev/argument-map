import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import HadithListPage from './HadithListPage';

const BASE = 'http://test.local';

function paged(items: unknown[], opts: { hasNext?: boolean } = {}) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: opts.hasNext ? 2 : 1,
    hasNext: opts.hasNext ?? false,
  };
}

function hadith(id: string, primaryNumber: number, previewMatn: string, collectionId = 'c1') {
  return {
    id,
    collectionId,
    primaryNumber,
    normalizedMatn: 'norm',
    previewMatn,
    status: 'VARIANT',
    sourceId: null,
    createdAt: '2026-01-01',
  };
}

const COLLECTIONS = [
  {
    id: 'c1',
    slug: 'bukhari',
    nameAr: 'صحيح البخاري',
    nameEn: 'Sahih al-Bukhari',
    nameRu: 'Сахих аль-Бухари',
    totalHadith: 7563,
    hadithCount: 100,
  },
  {
    id: 'c2',
    slug: 'muslim',
    nameAr: 'صحيح مسلم',
    nameEn: 'Sahih Muslim',
    nameRu: 'Сахих Муслим',
    totalHadith: 7470,
    hadithCount: 1,
  },
];

describe('HadithListPage', () => {
  it('рендерит чипы-сборники и карточки с диакритизированным previewMatn', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, () =>
        HttpResponse.json(paged([hadith('h1', 1, 'إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ')])),
      ),
    );
    render(
      <MemoryRouter>
        <HadithListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Сахих аль-Бухари/ })).toBeInTheDocument();
      // карточка показывает реальный текст с огласовками (а не folded normalized)
      expect(screen.getByText(/إِنَّمَا الأَعْمَالُ/)).toBeInTheDocument();
    });
  });

  it('клик по чипу сборника фильтрует список по collectionId', async () => {
    const collectionIds: (string | null)[] = [];
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, ({ request }) => {
        collectionIds.push(new URL(request.url).searchParams.get('collectionId'));
        return HttpResponse.json(paged([hadith('h1', 1, 'متن')]));
      }),
    );
    render(
      <MemoryRouter>
        <HadithListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Сахих аль-Бухари/ })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /Сахих аль-Бухари/ }));

    await waitFor(() => {
      expect(collectionIds[collectionIds.length - 1]).toBe('c1');
    });
  });

  it('honors ?collectionId= из URL: предвыбирает сборник и сразу фильтрует список', async () => {
    const collectionIds: (string | null)[] = [];
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, ({ request }) => {
        collectionIds.push(new URL(request.url).searchParams.get('collectionId'));
        return HttpResponse.json(paged([hadith('h1', 1, 'متن', 'c1')]));
      }),
    );
    render(
      <MemoryRouter initialEntries={['/hadith?collectionId=c1']}>
        <HadithListPage />
      </MemoryRouter>,
    );
    // первый же запрос списка уже идёт с collectionId=c1 (param honored на load)
    await waitFor(() => {
      expect(collectionIds[0]).toBe('c1');
    });
    // чип сборника предвыбран (aria-pressed)
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Сахих аль-Бухари/ })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
    });
  });

  it('две оси фасетов: чип происхождения шлёт status, чип достоверности — authenticity', async () => {
    const calls: { status: string | null; authenticity: string | null }[] = [];
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, ({ request }) => {
        const u = new URL(request.url).searchParams;
        calls.push({ status: u.get('status'), authenticity: u.get('authenticity') });
        return HttpResponse.json(paged([hadith('h1', 1, 'متن')]));
      }),
    );
    render(
      <MemoryRouter>
        <HadithListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText(/متن/)).toBeInTheDocument();
    });
    // первый запрос — обе оси пусты (ALL)
    expect(calls[0]).toEqual({ status: null, authenticity: null });

    // ось происхождения: чип «Сахихайн» → status=CANONICAL, authenticity не выставлен
    await userEvent.click(screen.getByRole('button', { name: 'Сахихайн' }));
    await waitFor(() => {
      expect(calls[calls.length - 1]).toEqual({ status: 'CANONICAL', authenticity: null });
    });

    // ось достоверности: чип «Даиф» → authenticity=DAIF, провенанс сохраняется
    await userEvent.click(screen.getByRole('button', { name: 'Даиф' }));
    await waitFor(() => {
      expect(calls[calls.length - 1]).toEqual({ status: 'CANONICAL', authenticity: 'DAIF' });
    });
  });

  it('смена сортировки шлёт sort param (default number → alphabetical)', async () => {
    const sorts: (string | null)[] = [];
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, ({ request }) => {
        sorts.push(new URL(request.url).searchParams.get('sort'));
        return HttpResponse.json(paged([hadith('h1', 1, 'متن')]));
      }),
    );
    render(
      <MemoryRouter>
        <HadithListPage />
      </MemoryRouter>,
    );
    await waitForApi(() => {
      expect(screen.getByText(/متن/)).toBeInTheDocument();
    });
    expect(sorts[0]).toBe('number');

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: /Сортировка/ }),
      'alphabetical',
    );

    await waitFor(() => {
      expect(sorts[sorts.length - 1]).toBe('alphabetical');
    });
  });
});
