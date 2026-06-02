import { describe, it, expect, beforeAll, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import HadithDetailPage from './HadithDetailPage';

const BASE = 'http://test.local';

// HadithSectionNav использует IntersectionObserver — jsdom его не даёт.
beforeAll(() => {
  class IOMock {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): [] {
      return [];
    }
  }
  vi.stubGlobal('IntersectionObserver', IOMock);
});

const DETAIL = {
  id: 'h1',
  collectionId: 'c1',
  primaryNumber: 1,
  normalizedMatn: 'إنما الأعمال بالنيات',
  status: 'CANONICAL',
  sourceId: null,
  createdAt: '2026-01-01',
  matns: [
    {
      id: 'm1',
      textAr: 'إنما الأعمال بالنيات',
      textRu: null,
      textEn: null,
      collectionId: 'c1',
      printedNumber: 1,
      pageNo: null,
      volume: null,
      isPrimary: true,
      divergenceSummary: null,
    },
  ],
  grades: [{ scholar: 'аль-Бухари', grade: 'Сахих', note: 'муттафакун алейхи' }],
};

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
];

function mockEndpoints(detail: typeof DETAIL = DETAIL) {
  server.use(
    http.get(`${BASE}/api/v1/hadith/hadiths/h1/detail`, () => HttpResponse.json(detail)),
    http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
    http.get(`${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`, () =>
      HttpResponse.json({ hadithId: 'h1', nodes: [], edges: [], sanads: [] }),
    ),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/hadith/hadiths/h1']}>
      <Routes>
        <Route path="/hadith/hadiths/:id" element={<HadithDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('HadithDetailPage', () => {
  it('рендерит четыре секции: текст, иснад, оценки, вариации', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      // текст-герой (h1) — отдельно от копии текста в карточке вариации
      expect(
        screen.getByRole('heading', { level: 1, name: /إنما الأعمال بالنيات/ }),
      ).toBeInTheDocument();
      // имя сборника подтянуто из /collections
      expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
      // секционная навигация
      expect(screen.getByRole('navigation', { name: 'Разделы хадиса' })).toBeInTheDocument();
      // оценки
      expect(screen.getByText('аль-Бухари')).toBeInTheDocument();
    });
  });

  it('секционная навигация ведёт якорными ссылками на секции', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('link', { name: 'Текст' })).toHaveAttribute('href', '#text');
    });
    expect(screen.getByRole('link', { name: 'Иснад' })).toHaveAttribute('href', '#sanad');
    expect(screen.getByRole('link', { name: 'Оценки' })).toHaveAttribute('href', '#grades');
    expect(screen.getByRole('link', { name: 'Вариации' })).toHaveAttribute('href', '#variations');
  });

  it('показывает пояснение статуса CANONICAL', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/Канонический — достоверный/)).toBeInTheDocument();
    });
  });

  it('пустой список оценок → дружелюбный empty-state', async () => {
    mockEndpoints({ ...DETAIL, grades: [] });
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Оценки учёных пока не добавлены')).toBeInTheDocument();
    });
  });
});
