import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import NarratorListPage from './NarratorListPage';

const BASE = 'http://test.local';

function paged(items: unknown[]) {
  return { items, page: 0, size: 30, totalElements: items.length, totalPages: 1, hasNext: false };
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
              transmittedCountCached: 0,
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
});
