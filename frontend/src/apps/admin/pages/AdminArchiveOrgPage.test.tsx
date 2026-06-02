import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
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
      { role: 'cover', volumeNo: 0, original: { name: 'fmhji0.pdf', size: 130000, downloadUrl: 'x' } },
      {
        role: 'volume',
        volumeNo: 1,
        original: { name: 'fmhji1.pdf', size: 19000000, downloadUrl: 'x' },
        ocr: { name: 'fmhji1_text.pdf', size: 25000000, downloadUrl: 'x' },
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

function renderPage() {
  return render(
    <MemoryRouter>
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
    // том 1 показывает оригинал + OCR
    expect(screen.getByText('Том 1')).toBeInTheDocument();
    expect(screen.getByText('OCR')).toBeInTheDocument();
  });

  it('импорт вызывает POST и показывает success toast + ссылку на книгу', async () => {
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

    const importBtn = await screen.findByRole('button', { name: /Импортировать/i });
    await userEvent.click(importBtn);

    await waitForApi(() => {
      expect(imported).toBe(true);
    });
    expect(await screen.findByText(/Импортировано, томов 3/i)).toBeInTheDocument();
    // ссылка на созданную книгу
    expect(screen.getByRole('link', { name: /Открыть книгу/i })).toHaveAttribute(
      'href',
      '/books/11111111-1111-1111-1111-111111111111',
    );
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

  it('test-mode: чекбокс «извлечь текст» открывает поле числа страниц', async () => {
    server.use(http.get(PREVIEW_URL, () => HttpResponse.json(preview())));
    renderPage();

    await enterUrlAndLoad();

    await waitForApi(() => {
      expect(screen.getByText('Извлечение текста')).toBeInTheDocument();
    });

    // поле числа страниц скрыто пока extractText выключен
    expect(screen.queryByRole('spinbutton', { name: /Только N страниц/i })).toBeNull();

    await userEvent.click(screen.getByRole('checkbox', { name: /Извлечь текст/i }));

    expect(
      screen.getByRole('spinbutton', { name: /Только N страниц/i }),
    ).toBeInTheDocument();
  });
});
