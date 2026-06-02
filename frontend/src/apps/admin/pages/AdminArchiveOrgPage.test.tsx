import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminArchiveOrgPage from './AdminArchiveOrgPage';
import Toaster from '@/shared/components/ui/Toaster';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const PREVIEW_URL = `${BASE}/api/v1/admin/archive-org/preview`;
const IMPORT_URL = `${BASE}/api/v1/admin/archive-org/import`;

const ADMIN_USER = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  email: 'admin@argumentmap.local',
  role: 'ADMIN' as const,
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
};

/** Превью в НОВОЙ форме VolumeGroup: один файл на том (role/label/sizeBytes),
 *  без original/ocr — archive.org книги теперь PDF-only (FILE_ONLY). */
function preview() {
  return {
    archiveOrgId: 'fmhji',
    title: { value: 'فتح المجيد', source: 'archive_org' },
    author: { value: 'عبد الرحمن بن حسن', source: 'archive_org' },
    publisher: { value: '', source: 'missing' },
    place: { value: '', source: 'missing' },
    muhaqqiq: { value: '', source: 'missing' },
    edition: { value: '', source: 'missing' },
    yearHijri: { value: '', source: 'missing' },
    yearGregorian: { value: '', source: 'missing' },
    volumes: { value: '3', source: 'archive_org' },
    language: { value: 'Arabic', source: 'archive_org' },
    rawDescription: 'المؤلف: عبد الرحمن بن حسن · عدد المجلدات: ٣',
    files: [
      {
        role: 'cover',
        volumeNo: 0,
        name: 'fmhji0.pdf',
        label: 'Обложка',
        sizeBytes: 130000,
        downloadUrl: 'x',
      },
      {
        role: 'volume',
        volumeNo: 1,
        name: 'fmhji1.pdf',
        label: 'Том 1',
        sizeBytes: 19000000,
        downloadUrl: 'x',
      },
    ],
    coverOptions: [
      { kind: 'thumbnail', url: 'https://archive.org/services/img/fmhji' },
      { kind: 'cover_pdf_page' },
      { kind: 'upload' },
    ],
    hasPdf: true,
  };
}

/** Зеркало текущего pathname — для проверки навигации после «Открыть книгу». */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="location">{loc.pathname}</div>;
}

function renderPage() {
  return render(
    <MemoryRouter>
      <LocationProbe />
      <AdminArchiveOrgPage />
      <Toaster />
    </MemoryRouter>,
  );
}

async function enterUrlAndLoad() {
  const input = screen.getByRole('textbox', { name: /Ссылка на archive\.org/i });
  await userEvent.type(input, 'https://archive.org/details/fmhji');
  await userEvent.click(screen.getByRole('button', { name: /Загрузить превью/i }));
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

describe('AdminArchiveOrgPage', () => {
  it('загружает превью и рендерит gap-бейджи (из источника / нет в источнике)', async () => {
    server.use(http.get(PREVIEW_URL, () => HttpResponse.json(preview())));
    renderPage();

    await enterUrlAndLoad();

    // метаданные с провенансом: title из источника, мухаккик отсутствует
    await waitForApi(() => {
      expect(screen.getByText('Метаданные')).toBeInTheDocument();
    });
    // зелёные бейджи «из archive.org» (минимум title/author/volumes/language)
    expect(screen.getAllByText(/из archive\.org/i).length).toBeGreaterThan(0);
    // жёлтые бейджи «нет в источнике» (publisher/place/muhaqqiq/edition/years)
    expect(screen.getAllByText(/нет в источнике/i).length).toBeGreaterThan(0);
    // том 1 показывает label из сервера (один файл на том, без OCR)
    expect(screen.getByText('Том 1')).toBeInTheDocument();
    expect(screen.queryByText('OCR')).toBeNull();
  });

  it('импорт вызывает POST и блокирует форму success-карточкой; «Импортировать ещё» возвращает на стартовый экран', async () => {
    let imported = false;
    server.use(
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(IMPORT_URL, () => {
        imported = true;
        return HttpResponse.json({
          bookId: '11111111-1111-1111-1111-111111111111',
          archiveOrgId: 'fmhji',
          volumesRegistered: 3,
          coverSet: true,
          pagesExtracted: 0,
          alreadyExisted: false,
        });
      }),
    );
    renderPage();

    await enterUrlAndLoad();

    const importBtn = await screen.findByRole('button', { name: /^Импортировать$/i });
    await userEvent.click(importBtn);

    await waitForApi(() => {
      expect(imported).toBe(true);
    });

    // success-карточка: томов 3 + тип FILE_ONLY
    expect(await screen.findByText(/Импортировано: томов 3/i)).toBeInTheDocument();
    expect(screen.getByText(/только PDF/i)).toBeInTheDocument();

    // форма заблокирована: ни URL-инпута, ни редактируемых метаданных, ни кнопки превью
    expect(screen.queryByRole('textbox', { name: /Ссылка на archive\.org/i })).toBeNull();
    expect(screen.queryByText('Метаданные')).toBeNull();
    expect(screen.queryByRole('button', { name: /Загрузить превью/i })).toBeNull();

    // две action-кнопки
    const openBook = screen.getByRole('button', { name: /Открыть книгу/i });
    expect(openBook).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Импортировать ещё/i })).toBeInTheDocument();

    // «Импортировать ещё» → возврат на стартовый экран URL-инпута
    await userEvent.click(screen.getByRole('button', { name: /Импортировать ещё/i }));
    expect(screen.getByRole('textbox', { name: /Ссылка на archive\.org/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Открыть книгу/i })).toBeNull();
  });

  it('«Открыть книгу» навигирует на страницу созданной книги', async () => {
    server.use(
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(IMPORT_URL, () =>
        HttpResponse.json({
          bookId: '11111111-1111-1111-1111-111111111111',
          archiveOrgId: 'fmhji',
          volumesRegistered: 3,
          coverSet: true,
          pagesExtracted: 0,
          alreadyExisted: false,
        }),
      ),
    );
    renderPage();

    await enterUrlAndLoad();
    await userEvent.click(await screen.findByRole('button', { name: /^Импортировать$/i }));

    const openBook = await screen.findByRole('button', { name: /Открыть книгу/i });
    await userEvent.click(openBook);

    expect(screen.getByTestId('location')).toHaveTextContent(
      '/books/11111111-1111-1111-1111-111111111111',
    );
  });

  it('alreadyExisted показывает «уже была импортирована» с переходом на книгу', async () => {
    server.use(
      http.get(PREVIEW_URL, () => HttpResponse.json(preview())),
      http.post(IMPORT_URL, () =>
        HttpResponse.json({
          bookId: '22222222-2222-2222-2222-222222222222',
          archiveOrgId: 'fmhji',
          volumesRegistered: 0,
          coverSet: false,
          pagesExtracted: 0,
          alreadyExisted: true,
        }),
      ),
    );
    renderPage();

    await enterUrlAndLoad();
    await userEvent.click(await screen.findByRole('button', { name: /^Импортировать$/i }));

    // заголовок success-карточки (h2) — не путать с транзиентным toast
    expect(
      await screen.findByRole('heading', { name: /Эта книга уже была импортирована$/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Открыть книгу/i })).toBeInTheDocument();
  });

  it('hasPdf=false показывает «в этом item нет PDF»', async () => {
    server.use(http.get(PREVIEW_URL, () => HttpResponse.json({ ...preview(), hasPdf: false })));
    renderPage();

    await enterUrlAndLoad();

    await waitForApi(() => {
      expect(screen.getByText(/В этом item нет PDF/i)).toBeInTheDocument();
    });
  });

  it('404 показывает дружелюбное «item не найден»', async () => {
    server.use(
      http.get(PREVIEW_URL, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404, detail: 'item not found' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();

    await enterUrlAndLoad();

    await waitForApi(() => {
      expect(screen.getByText(/Item не найден на archive\.org/i)).toBeInTheDocument();
    });
  });
});
