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
    // commentaries отсутствуют в ответе → секция «Оценки учёных» скрыта
    expect(screen.queryByText('Оценки учёных о передатчике')).not.toBeInTheDocument();
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

  it('narrator-commentary: секция «Оценки учёных» с критиком, вердиктом и атрибуцией', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators/n3`, () =>
        HttpResponse.json({
          id: 'n3',
          authorityId: null,
          nameAr: 'أبو هريرة الدوسي',
          kunya: null,
          laqab: null,
          yearBirthHijri: null,
          yearDeathHijri: null,
          birthplace: null,
          primaryResidence: null,
          reliabilityGrade: 'SAHABI',
          reliabilityComment: null,
          transmittedCount: 0,
          createdAt: '2026-01-01',
          relations: null,
          commentaries: [
            {
              commenter: 'ابن حجر',
              commenterDeathYear: 852,
              bookName: 'تقريب التهذيب',
              author: 'ابن حجر العسقلاني',
              page: 1218,
              volume: 1,
              comments: ['الصحابي الجليل ، حافظ الصحابة'],
            },
          ],
        }),
      ),
      http.get(`${BASE}/api/v1/hadith/narrators/n3/transmitted`, () =>
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
    renderAt('n3');
    await waitForApi(() => {
      expect(screen.getByText('أبو هريرة الدوسي')).toBeInTheDocument();
    });
    // секция отрендерилась (заголовок)
    expect(screen.getByText('Оценки учёных о передатчике')).toBeInTheDocument();
    // критик + вердикт видны
    expect(screen.getByText('ابن حجر')).toBeInTheDocument();
    expect(screen.getByText('الصحابي الجليل ، حافظ الصحابة')).toBeInTheDocument();
    // год смерти критика (интерполяция {year})
    expect(screen.getByText('ум. 852 г.х.')).toBeInTheDocument();
  });

  it('B4: серый verbatim-бар скрывается когда gradeText дублирует commentary', async () => {
    // Абу Хурайра: gradeText = «الصحابي الجليل حافظ الصحابة»,
    // первая commentary содержит тот же текст (с огласовками/запятой — нормализованно совпадёт).
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators/n4`, () =>
        HttpResponse.json({
          id: 'n4',
          authorityId: null,
          nameAr: 'أبو هريرة',
          kunya: null,
          laqab: null,
          yearBirthHijri: null,
          yearDeathHijri: 59,
          birthplace: null,
          primaryResidence: null,
          reliabilityGrade: 'SAHABI',
          reliabilityComment: null,
          transmittedCount: 0,
          createdAt: '2026-01-01',
          tabaqa: 'الصحابي الجليل',
          gradeText: 'الصحابي الجليل حافظ الصحابة',
          bornOnText: null,
          diedOnText: null,
          deathPlace: null,
          relations: null,
          commentaries: [
            {
              commenter: 'ابن حجر',
              commenterDeathYear: 852,
              bookName: 'تقريب التهذيب',
              author: 'ابن حجر العسقلاني',
              page: 1218,
              volume: 1,
              // Commentary содержит тот же эпитет (с запятой — после нормализации совпадёт)
              comments: ['الصحابي الجليل ، حافظ الصحابة'],
            },
          ],
        }),
      ),
      http.get(`${BASE}/api/v1/hadith/narrators/n4/transmitted`, () =>
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
    renderAt('n4');
    await waitForApi(() => {
      expect(screen.getByText('أبو هريرة')).toBeInTheDocument();
    });
    // Текст из commentary отображается в секции «Оценки учёных» (с атрибуцией)
    expect(screen.getByText('الصحابي الجليل ، حافظ الصحابة')).toBeInTheDocument();
    // gradeText дублирует commentary — серый бар скрыт (текст НЕ появляется дважды)
    // getAllByText вернёт ровно один экземпляр (в commentary), не два
    expect(screen.getAllByText(/الصحابي الجليل/).length).toBe(1);
    // tabaqa «الصحابي الجليل» является частью gradeText → поле «Поколение» скрыто
    expect(screen.queryByText('الصحابي الجليل')).not.toBeInTheDocument();
  });

  it('B4: tabaqa показывается когда это реальное поколение (не эпитет)', async () => {
    // Суфьян ибн Уяйна: tabaqa=«الطبقة الثامنة», gradeText=«ثقة حافظ» — не совпадают
    server.use(
      http.get(`${BASE}/api/v1/hadith/narrators/n5`, () =>
        HttpResponse.json({
          id: 'n5',
          authorityId: null,
          nameAr: 'سفيان بن عيينة',
          kunya: null,
          laqab: null,
          yearBirthHijri: 107,
          yearDeathHijri: 198,
          birthplace: null,
          primaryResidence: null,
          reliabilityGrade: 'THIQA',
          reliabilityComment: null,
          transmittedCount: 0,
          createdAt: '2026-01-01',
          tabaqa: 'الطبقة الثامنة',
          gradeText: 'ثقة حافظ فقيه إمام حجة',
          bornOnText: null,
          diedOnText: null,
          deathPlace: null,
          relations: null,
          commentaries: null,
        }),
      ),
      http.get(`${BASE}/api/v1/hadith/narrators/n5/transmitted`, () =>
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
    renderAt('n5');
    await waitForApi(() => {
      expect(screen.getByText('سفيان بن عيينة')).toBeInTheDocument();
    });
    // tabaqa ≠ gradeText → поле «Поколение» показывается
    expect(screen.getByText('الطبقة الثامنة')).toBeInTheDocument();
    // gradeText не дублируется commentary (commentaries=null) → серый бар показывается
    expect(screen.getByText('ثقة حافظ فقيه إمام حجة')).toBeInTheDocument();
  });
});
