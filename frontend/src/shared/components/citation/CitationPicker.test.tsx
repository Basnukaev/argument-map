import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import CitationPicker from './CitationPicker';

// CitationPickerPdfRegion грузит react-pdf (тяжёлый, рисование не работает в
// jsdom — нет layout). Мокаем компонент кнопкой «set region», которая через
// onRegionChange отдаёт фиксированный регион — так тестируем submit-логику
// (PDF_LINK payload), а не сам pointer-drag (он покрыт unit-тестом pdfRegion).
vi.mock('./CitationPickerPdfRegion', () => ({
  default: ({
    onRegionChange,
  }: {
    onRegionChange: (r: {
      fileIndex: number;
      pageNumber: number;
      bbox: { x: number; y: number; width: number; height: number };
    } | null) => void;
  }) => (
    <button
      type="button"
      data-testid="mock-set-region"
      onClick={() =>
        onRegionChange({
          fileIndex: 2,
          pageNumber: 7,
          bbox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4 },
        })
      }
    >
      set region
    </button>
  ),
}));

const BASE = 'http://test.local';

const BOOKS_EMPTY_RESPONSE = { items: [], total: 0, page: 0, size: 100 };

const FILE_ONLY_BOOK_DETAIL = {
  id: 'book-file-only',
  title: 'Скан без текста',
  language: 'ar',
  contentKind: 'FILE_ONLY',
  chapters: [],
};

function renderPicker() {
  return render(
    <CitationPicker
      targetType="nodes"
      targetId="node-1"
      targetLabel="Тестовый узел"
      onClose={vi.fn()}
      onCreated={vi.fn()}
    />,
  );
}

describe('CitationPicker', () => {
  beforeEach(() => {
    // matchMedia stub — useIsMobile использует useSyncExternalStore.
    // desktop mode (matches=false для max-width:767px)
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function mockFileOnlyBook() {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json({
          items: [{ id: 'book-file-only', title: 'Скан без текста' }],
          total: 1,
          page: 0,
          size: 100,
        }),
      ),
      http.get(`${BASE}/api/v1/library/books/book-file-only`, () =>
        HttpResponse.json(FILE_ONLY_BOOK_DETAIL),
      ),
      http.get(`${BASE}/api/v1/library/books/book-file-only/pages`, () =>
        HttpResponse.json([]),
      ),
    );
  }

  it('для FILE_ONLY книги показывает REGION-режим (рисование области), не спиннер «Загрузка страницы»', async () => {
    mockFileOnlyBook();
    renderPicker();

    const bookButton = await screen.findByRole('button', { name: 'Скан без текста' });
    bookButton.click();

    // Вместо старого placeholder'а — компонент рисования области (мок).
    // CitationPickerPdfRegion lazy-загружается → findBy* с дефолтным
    // timeout'ом (Suspense fallback успевает смениться на мок).
    expect(await screen.findByTestId('mock-set-region')).toBeInTheDocument();
    // Старый текст недоступности больше не показывается
    expect(
      screen.queryByText(/Цитирование по фрагменту текста пока недоступно/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Загрузка страницы')).not.toBeInTheDocument();
  });

  it('для FILE_ONLY книги «Привести» disabled пока область не нарисована, затем POST шлёт PDF_LINK payload', async () => {
    mockFileOnlyBook();
    let captured: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/nodes/node-1/citations`, async ({ request }) => {
        captured = await request.json();
        return HttpResponse.json({}, { status: 201 });
      }),
    );

    const onCreated = vi.fn();
    render(
      <CitationPicker
        targetType="nodes"
        targetId="node-1"
        targetLabel="Тестовый узел"
        onClose={vi.fn()}
        onCreated={onCreated}
      />,
    );

    const bookButton = await screen.findByRole('button', { name: 'Скан без текста' });
    bookButton.click();

    const setRegionBtn = await screen.findByTestId('mock-set-region');

    // До рисования области «Привести» disabled
    const submitBtn = screen.getByRole('button', { name: 'Привести' });
    expect(submitBtn).toBeDisabled();

    // Рисуем область (мок отдаёт fileIndex=2, page=7, bbox)
    setRegionBtn.click();

    await waitForApi(() => {
      expect(screen.getByRole('button', { name: 'Привести' })).toBeEnabled();
    });

    screen.getByRole('button', { name: 'Привести' }).click();

    await waitForApi(() => {
      expect(captured).toEqual({
        bookId: 'book-file-only',
        pdfFileIndex: 2,
        pdfPageNumber: 7,
        pdfBbox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4 },
      });
    });
    expect(onCreated).toHaveBeenCalled();
  });

  it('для книги без contentKind (TEXT_ONLY) показывает reader, не сообщение о FILE_ONLY', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json({
          items: [{ id: 'book-text', title: 'Текстовая книга' }],
          total: 1,
          page: 0,
          size: 100,
        }),
      ),
      http.get(`${BASE}/api/v1/library/books/book-text`, () =>
        HttpResponse.json({
          id: 'book-text',
          title: 'Текстовая книга',
          language: 'ar',
          contentKind: 'TEXT_ONLY',
          chapters: [],
        }),
      ),
      http.get(`${BASE}/api/v1/library/books/book-text/pages`, () =>
        HttpResponse.json([{ id: 'page-1', pageNumber: 1 }]),
      ),
      http.get(`${BASE}/api/v1/library/pages/page-1`, () =>
        HttpResponse.json({ id: 'page-1', pageNumber: 1, textContent: 'بسم الله' }),
      ),
    );

    renderPicker();

    const bookButton = await screen.findByRole('button', { name: 'Текстовая книга' });
    bookButton.click();

    // Сообщение о FILE_ONLY недоступности НЕ должно появиться
    await waitForApi(() => {
      expect(
        screen.queryByText(/Эта книга — только PDF/),
      ).not.toBeInTheDocument();
    });
  });

  it('при загрузке списка книг показывает spinner', () => {
    // Не регистрируем handler — запрос зависнет
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () => new Promise(() => undefined)),
    );

    renderPicker();

    // Должен быть spinner в панели книг
    const spinners = document.querySelectorAll('[class*="animate-spin"]');
    expect(spinners.length).toBeGreaterThan(0);
  });

  it('при пустом списке книг показывает placeholder «Выберите книгу»', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books`, () =>
        HttpResponse.json(BOOKS_EMPTY_RESPONSE),
      ),
    );

    renderPicker();

    await waitForApi(() => {
      expect(screen.getByText(/Выберите книгу в списке слева/)).toBeInTheDocument();
    });
  });
});
