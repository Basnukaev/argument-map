import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminHadithImportPage from './AdminHadithImportPage';
import Toaster from '@/shared/components/ui/Toaster';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const CATALOG_URL = `${BASE}/api/v1/admin/alminasa/catalog`;
const IMPORT_STATUS_URL = `${BASE}/api/v1/admin/alminasa/import/status`;
const CRAWL_STATUS_URL = `${BASE}/api/v1/admin/alminasa/crawl/status`;
const NARRATORS_URL = `${BASE}/api/v1/admin/alminasa/import/narrators`;
const HADITHS_URL = `${BASE}/api/v1/admin/alminasa/import/hadiths`;
const DRY_RUN_146_1 = `${BASE}/api/v1/admin/alminasa/dry-run/146-1`;

const ADMIN_USER = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  email: 'admin@argumentmap.local',
  role: 'ADMIN' as const,
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
};

/** 12-сборниковый каталог (как отдаёт backend при пустом/наполненном staging). */
function catalog12() {
  const collections = [
    { bookId: 146, slug: 'bukhari', nameAr: 'صحيح البخاري', nameRu: 'Сахих аль-Бухари' },
    { bookId: 147, slug: 'muslim', nameAr: 'صحيح مسلم', nameRu: 'Сахих Муслим' },
    { bookId: 148, slug: 'abudawud', nameAr: 'سنن أبي داود', nameRu: 'Сунан Абу Дауд' },
    { bookId: 149, slug: 'tirmidhi', nameAr: 'سنن الترمذي', nameRu: 'Сунан ат-Тирмизи' },
    { bookId: 150, slug: 'nasai', nameAr: 'سنن النسائي', nameRu: 'Сунан ан-Насаи' },
    { bookId: 151, slug: 'ibnmajah', nameAr: 'سنن ابن ماجه', nameRu: 'Сунан Ибн Маджа' },
    { bookId: 152, slug: 'malik', nameAr: 'موطأ مالك', nameRu: 'Муватта Малика' },
    { bookId: 153, slug: 'ahmad', nameAr: 'مسند أحمد', nameRu: 'Муснад Ахмада' },
    { bookId: 154, slug: 'darimi', nameAr: 'سنن الدارمي', nameRu: 'Сунан ад-Дарими' },
    { bookId: 155, slug: 'bayhaqi', nameAr: 'السنن الكبرى', nameRu: 'ас-Сунан аль-Кубра' },
    { bookId: 156, slug: 'hakim', nameAr: 'المستدرك', nameRu: 'аль-Мустадрак' },
    { bookId: 157, slug: 'tabarani', nameAr: 'المعجم الكبير', nameRu: 'аль-Муджам аль-Кабир' },
  ];
  return collections.map((c) => ({ ...c, stagedCount: 0, mappedCount: 0 }));
}

function idleImportStatus() {
  return { status: 'IDLE' };
}

function crawlStatus(status: string) {
  return {
    status,
    fetchedCount: status === 'RUNNING' ? 100 : 0,
    totalHits: status === 'RUNNING' ? 1000 : null,
    lastSortId: status === 'RUNNING' ? '146-12' : null,
  };
}

function dryRun146() {
  return {
    externalId: '146-1',
    collectionSlug: 'bukhari',
    status: 'CANONICAL',
    hadithType: 'مرفوع',
    primaryNumber: 1,
    chapterAr: 'باب بدء الوحي',
    matnPreview: 'إنما الأعمال بالنيات',
    chain: [
      { position: 0, externalId: '5913', nameAr: 'عمر بن الخطاب', formula: 'عن' },
      { position: 1, externalId: '6001', nameAr: 'علقمة بن وقاص', formula: 'حدثنا' },
    ],
    editionsCount: 2,
    crossrefsCount: 1,
    rulingsCount: 2,
    explanationsCount: 1,
  };
}

/** Базовые handlers: каталог + статусы IDLE. Конкретные тесты доопределяют POST/dry-run. */
function baseHandlers(crawl = 'IDLE') {
  return [
    http.get(CATALOG_URL, () => HttpResponse.json(catalog12())),
    http.get(IMPORT_STATUS_URL, () => HttpResponse.json(idleImportStatus())),
    http.get(CRAWL_STATUS_URL, () => HttpResponse.json(crawlStatus(crawl))),
  ];
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminHadithImportPage />
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

describe('AdminHadithImportPage', () => {
  it('рендерит каталог из 12 сборников', async () => {
    server.use(...baseHandlers());
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });
    // 12 русских названий рендерятся как строки таблицы
    expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
    expect(screen.getByText('аль-Муджам аль-Кабир')).toBeInTheDocument();
    const mapButtons = screen.getAllByRole('button', { name: /Маппинг$/i });
    expect(mapButtons).toHaveLength(12);
  });

  it('кнопки маппинга disabled когда краулер RUNNING', async () => {
    server.use(...baseHandlers('RUNNING'));
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });

    // per-book кнопки «Маппинг» заблокированы
    const mapButtons = screen.getAllByRole('button', { name: /Маппинг$/i });
    expect(mapButtons.length).toBeGreaterThan(0);
    mapButtons.forEach((btn) => expect(btn).toBeDisabled());

    // глобальные «Импорт рави» и «Маппинг всех сборников» тоже disabled
    expect(screen.getByRole('button', { name: /Импорт рави/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Маппинг всех сборников/i })).toBeDisabled();
  });

  it('запуск импорта рави вызывает POST и переводит статус в RUNNING', async () => {
    let posted = false;
    server.use(
      ...baseHandlers(),
      http.post(NARRATORS_URL, () => {
        posted = true;
        return HttpResponse.json({ status: 'RUNNING', kind: 'NARRATORS', processedSoFar: 0 }, { status: 202 });
      }),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Импорт рави/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: /Импорт рави/i }));

    await waitForApi(() => {
      expect(posted).toBe(true);
    });
    // статус-секция показывает live RUNNING после ответа POST
    expect(await screen.findByText(/Импорт выполняется/i)).toBeInTheDocument();
  });

  it('409 при запуске импорта показывает toast «импорт уже идёт»', async () => {
    server.use(
      ...baseHandlers(),
      http.post(HADITHS_URL, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/alminasa-import-already-running',
            title: 'Импорт уже идёт',
            status: 409,
          },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Маппинг всех сборников/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: /Маппинг всех сборников/i }));

    expect(await screen.findByText(/Импорт уже идёт/i)).toBeInTheDocument();
  });

  it('dry-run happy path рендерит цепь иснада и counts', async () => {
    server.use(
      ...baseHandlers(),
      http.get(DRY_RUN_146_1, () => HttpResponse.json(dryRun146())),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });

    const input = screen.getByPlaceholderText('146-1');
    await userEvent.type(input, '146-1');
    await userEvent.click(screen.getByRole('button', { name: /^Превью$/i }));

    // матн + звенья цепи рендерятся
    expect(await screen.findByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.getByText('عمر بن الخطاب')).toBeInTheDocument();
    expect(screen.getByText('علقمة بن وقاص')).toBeInTheDocument();
  });

  it('dry-run 404 показывает inline «не найден в staging» (не toast)', async () => {
    server.use(
      ...baseHandlers(),
      http.get(DRY_RUN_146_1, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/alminasa-staging-not-found',
            title: 'Not found',
            status: 404,
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('صحيح البخاري')).toBeInTheDocument();
    });

    const input = screen.getByPlaceholderText('146-1');
    await userEvent.type(input, '146-1');
    await userEvent.click(screen.getByRole('button', { name: /^Превью$/i }));

    expect(await screen.findByText(/не найден в staging/i)).toBeInTheDocument();
  });

  it('после POST импорта статус-секция немедленно отражает ответ сервера (рефетч)', async () => {
    // POST возвращает уже завершённую сводку (быстрый прогон): страница
    // подставляет её сразу, без ожидания таймера поллинга. Проверяем факт
    // рефетча статуса после POST, не углубляясь в setInterval-механику.
    server.use(
      ...baseHandlers(),
      http.post(HADITHS_URL, () =>
        HttpResponse.json(
          {
            status: 'IDLE',
            kind: 'ALL',
            hadithsProcessed: 10,
            hadithsFailed: 0,
            narratorsProcessed: 0,
            crossrefsResolved: 3,
            relationsResolved: 1,
            failures: [],
          },
          { status: 202 },
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Маппинг всех сборников/i })).toBeInTheDocument();
    });

    // изначально статус IDLE без прогонов — показывается «не запускался»
    expect(screen.getByText(/Импорт ещё не запускался/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Маппинг всех сборников/i }));

    // после POST сводка последнего прогона отрендерилась (crossrefs=3)
    await waitForApi(() => {
      expect(screen.queryByText(/Импорт ещё не запускался/i)).not.toBeInTheDocument();
    });
    expect(screen.getByText('Crossref')).toBeInTheDocument();
  });
});
