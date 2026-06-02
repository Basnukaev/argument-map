import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminSunnahPage from './AdminSunnahPage';
import Toaster from '@/shared/components/ui/Toaster';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const COLLECTIONS_URL = `${BASE}/api/v1/admin/sunnah/collections`;
const HADITHS_URL = `${BASE}/api/v1/admin/sunnah/collections/bukhari/hadiths`;
const PREVIEW_URL = `${BASE}/api/v1/admin/sunnah/preview/bukhari/1`;
const IMPORT_URL = `${BASE}/api/v1/admin/sunnah/import/bukhari/1`;
const EXTRACT_URL = `${BASE}/api/v1/admin/sunnah/extract-isnad`;

const ADMIN_USER = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  email: 'admin@argumentmap.local',
  role: 'ADMIN' as const,
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
};

function collections() {
  return [
    { name: 'bukhari', titleEn: 'Sahih al-Bukhari', titleAr: 'صحيح البخاري', totalHadith: 7563, availableHadith: 100 },
  ];
}

function collectionsWithPartialDump() {
  return [
    { name: 'bukhari', titleEn: 'Sahih al-Bukhari', titleAr: 'صحيح البخاري', totalHadith: 7563, availableHadith: 100 },
    { name: 'nawawi40', titleEn: 'Nawawi 40', titleAr: 'الأربعون النووية', totalHadith: 42, availableHadith: 0 },
  ];
}

function browsePage() {
  return {
    items: [
      { number: '1', textArSnippet: 'إنما الأعمال بالنيات', textEnSnippet: 'Actions are by intentions', alreadyImported: false },
      { number: '2', textArSnippet: 'بينما نحن جلوس', textEnSnippet: 'While we were sitting', alreadyImported: true },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  };
}

function preview() {
  return {
    collection: 'bukhari',
    primaryNumber: 1,
    status: 'CANONICAL',
    matnAr: 'إنما الأعمال بالنيات وإنما لكل امرئ ما نوى',
    matnEn: 'Actions are judged by intentions',
    normalizedMatn: 'إنما الأعمال بالنيات',
    grades: [{ scholar: 'al-Bukhari', grade: 'SAHIH' }],
    structure: {
      bookNumber: '1',
      bookNameAr: 'بدء الوحي',
      bookNameEn: 'Revelation',
      chapterId: 'c1',
      chapterTitleAr: 'كيف كان بدء الوحي',
      chapterTitleEn: 'How the Revelation started',
    },
    isnad: null,
    importable: true,
    alreadyImported: false,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminSunnahPage />
      <Toaster />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  useAuthStore.setState({
    user: ADMIN_USER,
    accessToken: 'fake-jwt',
    isLoading: false,
    initialized: true,
  });
});

describe('AdminSunnahPage', () => {
  it('рендерит сборники и список хадисов с бейджами импорта', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Импорт хадисов \(Sunnah\)/i })).toBeInTheDocument();
    });

    // хадис №1 — новый, №2 — уже импортирован
    await waitForApi(() => {
      expect(screen.getByText('№1')).toBeInTheDocument();
    });
    expect(screen.getByText('новый')).toBeInTheDocument();
    expect(screen.getByText('импортирован')).toBeInTheDocument();
  });

  it('клик по хадису открывает превью с matn, статусом и оценками', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('№1')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('№1'));

    // превью-панель: статус CANONICAL + английский matn + оценка учёного
    await waitForApi(() => {
      expect(screen.getByText('CANONICAL')).toBeInTheDocument();
    });
    expect(screen.getByText(/Actions are judged by intentions/i)).toBeInTheDocument();
    expect(screen.getByText('al-Bukhari')).toBeInTheDocument();
    expect(screen.getByText('SAHIH')).toBeInTheDocument();
    // вместо старого placeholder'а — кнопка извлечения иснада через ИИ
    expect(
      screen.getByRole('button', { name: /Извлечь иснад/i }),
    ).toBeInTheDocument();
  });

  it('импорт хадиса вызывает POST и показывает success toast', async () => {
    let imported = false;
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(IMPORT_URL, () => {
        imported = true;
        return HttpResponse.json({
          collectionName: 'Sahih al-Bukhari',
          inserted: 1,
          skippedExisting: 0,
          skippedInvalid: 0,
        });
      }),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('№1')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('№1'));

    const importBtn = await screen.findByRole('button', { name: /Импортировать этот хадис/i });
    await userEvent.click(importBtn);

    await waitForApi(() => {
      expect(imported).toBe(true);
    });
    expect(await screen.findByText(/Хадис импортирован/i)).toBeInTheDocument();
  });

  it('извлечение иснада без настроенного AI показывает note «AI не настроен»', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(EXTRACT_URL, () =>
        HttpResponse.json({ llmEnabled: false, isnadFound: false, graph: null, cleanedMatn: null }),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('№1')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('№1'));

    const extractBtn = await screen.findByRole('button', { name: /Извлечь иснад/i });
    await userEvent.click(extractBtn);

    // note вместо краша/тоста
    expect(await screen.findByText(/AI не настроен/i)).toBeInTheDocument();
  });

  it('извлечение иснада с графом рендерит SanadGraph (узлы видны)', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(EXTRACT_URL, () =>
        HttpResponse.json({
          llmEnabled: true,
          isnadFound: true,
          cleanedMatn: 'إنما الأعمال بالنيات',
          graph: {
            hadithId: 'preview',
            nodes: [
              { id: 'prophet', role: 'PROPHET', data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
              {
                id: 'n1',
                role: 'NARRATOR',
                data: {
                  narratorId: '1',
                  nameAr: 'أبو هريرة',
                  nameRu: 'Абу Хурайра',
                  reliabilityGrade: 'SAHABI',
                  tier: 1,
                },
              },
            ],
            edges: [
              {
                id: 'e0',
                source: 'prophet',
                target: 'n1',
                data: { transmissionPhrase: 'عن', chainGrade: 'SAHIH', onPrimaryChain: true, sanadCount: 1 },
              },
            ],
            sanads: [
              {
                id: 's1',
                collectionRu: 'Сахих аль-Бухари',
                collectionAr: 'صحيح البخاري',
                chainGrade: 'SAHIH',
                primaryChain: true,
                collectorNodeId: 'n1',
              },
            ],
          },
        }),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('№1')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('№1'));

    const extractBtn = await screen.findByRole('button', { name: /Извлечь иснад/i });
    await userEvent.click(extractBtn);

    // SanadGraph отрендерил узел передатчика из извлечённого графа
    expect(await screen.findByText('Абу Хурайра')).toBeInTheDocument();
    // и cleanedMatn под графом
    expect(screen.getByText(/Текст матна \(без иснада\)/i)).toBeInTheDocument();
  });

  it('503 показывает дружелюбный экран «Импорт Sunnah не настроен»', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Service Unavailable',
            status: 503,
            detail: 'sunnah dump not configured',
          },
          { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText(/Импорт Sunnah не настроен/i)).toBeInTheDocument();
    });
  });

  it('кнопка импорта disabled для уже импортированного хадиса', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(`${BASE}/api/v1/admin/sunnah/preview/bukhari/2`, () =>
        HttpResponse.json({ ...preview(), primaryNumber: 2, alreadyImported: true }),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('№2')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByText('№2'));

    const importBtn = await screen.findByRole('button', { name: /Уже импортирован/i });
    expect(importBtn).toBeDisabled();
    // флаг «уже импортирован» в превью
    const flags = screen.getAllByText(/Уже импортирован/i);
    expect(flags.length).toBeGreaterThan(0);
  });

  it('чип показывает availableHadith, а не totalHadith', async () => {
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collections())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
    );
    renderPage();

    // bukhari: availableHadith=100, totalHadith=7563
    // чип должен показывать 100, а не 7563
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Sahih al-Bukhari/i })).toBeInTheDocument();
    });
    const chip = screen.getByRole('button', { name: /Sahih al-Bukhari/i });
    expect(chip).toHaveTextContent('100');
    expect(chip).not.toHaveTextContent('7563');
  });

  it('сборник с availableHadith=0 и totalHadith>0 показывает partial-dump сообщение', async () => {
    const nawawi40HadithsUrl = `${BASE}/api/v1/admin/sunnah/collections/nawawi40/hadiths`;
    server.use(
      http.get(COLLECTIONS_URL, () => HttpResponse.json(collectionsWithPartialDump())),
      http.get(HADITHS_URL, () => HttpResponse.json(browsePage())),
      http.get(nawawi40HadithsUrl, () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        }),
      ),
    );
    renderPage();

    // Ждём загрузки чипов
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Nawawi 40/i })).toBeInTheDocument();
    });

    // Кликаем на nawawi40 (availableHadith=0, totalHadith=42)
    await userEvent.click(screen.getByRole('button', { name: /Nawawi 40/i }));

    // Ждём empty-state с partial-dump сообщением
    await waitForApi(() => {
      expect(screen.getByText(/42 хадис/i)).toBeInTheDocument();
    });
    // Сообщение должно содержать упоминание Бухари и объяснение
    expect(screen.getByText(/Сахих аль-Бухари/i)).toBeInTheDocument();
    // Не должно показываться generic сообщение
    expect(screen.queryByText(/В этом сборнике нет хадисов/i)).not.toBeInTheDocument();
  });
});
