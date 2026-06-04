import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import NarratorDetailPage from './NarratorDetailPage';

const BASE = 'http://test.local';

function renderAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/hadith/narrators/${id}`]}>
      <Routes>
        <Route path="/hadith/narrators/:id" element={<NarratorDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('NarratorDetailPage', () => {
  it('рендерит биографию + список переданных хадисов', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators/n1`, () =>
        HttpResponse.json({
          id: 'n1',
          authorityId: null,
          nameAr: 'مالك بن أنس',
          kunya: 'أبو عبد الله',
          laqab: 'إمام دار الهجرة',
          yearBirthHijri: 93,
          yearDeathHijri: 179,
          birthplace: 'Медина',
          primaryResidence: 'Медина',
          reliabilityGrade: 'THIQA',
          reliabilityComment: 'Имам Медины, автор Муватты',
          transmittedCount: 1,
          createdAt: '2026-01-01',
        }),
      ),
      http.get(`${BASE}/api/v1/hadith/narrators/n1/transmitted`, () =>
        HttpResponse.json({
          items: [
            {
              id: 'h1',
              collectionId: null,
              primaryNumber: 1,
              normalizedMatn: 'إنما الأعمال بالنيات',
              status: 'CANONICAL',
              sourceId: null,
              createdAt: '2026-01-01',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    );
    renderAt('n1');
    await waitForApi(() => {
      expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    });
    expect(screen.getByText('Имам Медины, автор Муватты')).toBeInTheDocument();
    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
  });

  it('alminasa M3: tabaqa вместо generation, gradeText, сеть передатчиков', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators/n2`, () =>
        HttpResponse.json({
          id: 'n2',
          authorityId: null,
          nameAr: 'سفيان بن عيينة',
          kunya: null,
          laqab: null,
          yearBirthHijri: null,
          yearDeathHijri: null,
          birthplace: null,
          primaryResidence: null,
          // у alminasa-рави reliabilityComment=null, generation=null:
          reliabilityGrade: null,
          reliabilityComment: null,
          transmittedCount: 0,
          createdAt: '2026-01-01',
          tabaqa: 'الطبقة الثامنة',
          gradeText: 'ثقة حافظ فقيه إمام حجة',
          bornOnText: 'ولد سنة 107',
          diedOnText: 'توفي سنة 198',
          deathPlace: 'مكة',
          relations: [
            { relatedNarratorId: 'n-student', relatedName: 'الشافعي', role: 'STUDENT', cnt: 12 },
            { relatedNarratorId: null, relatedName: 'الزهري', role: 'SCHOLAR', cnt: 3 },
          ],
        }),
      ),
      http.get(`${BASE}/api/v1/hadith/narrators/n2/transmitted`, () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        }),
      ),
    );
    renderAt('n2');
    await waitForApi(() => {
      expect(screen.getByText('سفيان بن عيينة')).toBeInTheDocument();
    });
    // tabaqa как «поколение» (фолбэк отсутствующего generation)
    expect(screen.getByText('الطبقة الثامنة')).toBeInTheDocument();
    // gradeText как verbatim джарх (фолбэк reliabilityComment)
    expect(screen.getByText('ثقة حافظ فقيه إمام حجة')).toBeInTheDocument();
    // сеть передатчиков: resolved → линк, unresolved → текст
    expect(screen.getByRole('link', { name: /الشافعي/ })).toHaveAttribute(
      'href',
      '/hadith/narrators/n-student',
    );
    expect(screen.getByText('الزهري')).toBeInTheDocument();
  });
});
