import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { waitForUi } from '@/test/asyncHelpers';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import BookReaderPage from './BookReaderPage';

const BASE = 'http://test.local';

// PdfViewer lazy-грузит react-pdf + pdfjs-dist (тяжёлые, не работают в jsdom).
// Мокаем компонент простым маркером - нам важно лишь что PDF-панель смонтирована,
// а не реальный рендеринг страниц. initialBbox сериализуем в data-атрибут,
// чтобы тест мог проверить что deep-link ?bbox распарсился и долетел до prop.
vi.mock('@/shared/components/reader/PdfViewer', () => ({
  default: ({
    initialBbox,
    initialFileIndex,
  }: {
    initialBbox?: [number, number, number, number] | null;
    initialFileIndex?: number | null;
  }) => (
    <div
      data-testid="pdf-viewer"
      data-bbox={initialBbox ? initialBbox.join(',') : ''}
      data-file-index={initialFileIndex != null ? String(initialFileIndex) : ''}
    >
      PDF VIEWER
    </div>
  ),
}));

type ContentKind = 'TEXT_ONLY' | 'TEXT_AND_FILE' | 'FILE_ONLY';

interface BookFixtureOpts {
  contentKind?: ContentKind;
  /** Кол-во текстовых страниц. FILE_ONLY обычно 0 (скан без OCR). */
  pageCount?: number;
}

/**
 * Регистрирует MSW-handlers для одной книги: detail, pages, per-page content,
 * view-tracking POST. contentKind управляет тем какой reader-режим включится.
 */
function mockBook(opts: BookFixtureOpts = {}) {
  const { contentKind, pageCount = 1 } = opts;
  const pages = Array.from({ length: pageCount }, (_, i) => ({
    id: `pg-${i + 1}`,
    pageNumber: i + 1,
    printedPage: String(i + 1),
    part: '1',
  }));

  server.use(
    http.get(`${BASE}/api/v1/library/books/bk-1`, () =>
      HttpResponse.json({
        id: 'bk-1',
        title: 'Тестовая книга',
        language: 'ru',
        visibility: 'PUBLIC',
        ...(contentKind ? { contentKind } : {}),
        chapters: [],
      }),
    ),
    http.get(`${BASE}/api/v1/library/books/bk-1/pages`, () => HttpResponse.json(pages)),
    http.get(`${BASE}/api/v1/library/pages/:pageId`, ({ params }) =>
      HttpResponse.json({
        id: params.pageId,
        pageNumber: 1,
        textContent: '<p>Содержимое страницы</p>',
      }),
    ),
    // view-tracking POST (useViewTracking) - засчитываем, чтобы не было
    // unhandled request error
    http.post(`${BASE}/api/v1/library/books/bk-1/views`, () => HttpResponse.json({})),
  );
}

function renderReader(entry = '/books/bk-1') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/books/:bookId" element={<BookReaderPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('BookReaderPage / content_kind режимы reader', () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it('FILE_ONLY (0 текстовых страниц) - сразу PDF-панель, без переключателя «Текст» и без вечного спиннера', async () => {
    mockBook({ contentKind: 'FILE_ONLY', pageCount: 0 });
    renderReader();

    // PdfViewer lazy-загружается через Suspense - даём чуть больше времени
    await waitForUi(() => {
      expect(screen.getByTestId('pdf-viewer')).toBeInTheDocument();
    });
    // PDF mode форсирован: переключателя режимов нет вовсе
    expect(screen.queryByRole('button', { name: 'Текст' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'PDF' })).not.toBeInTheDocument();
    // «Назад к тексту» отсутствует - текста нет
    expect(screen.queryByText(/Назад к тексту/i)).not.toBeInTheDocument();
    // нет бесконечного «Загрузка страницы» (text-загрузка не запускается)
    expect(screen.queryByText(/Загрузка страницы/i)).not.toBeInTheDocument();
  });

  it('TEXT_ONLY - текстовый режим, без кнопки PDF', async () => {
    mockBook({ contentKind: 'TEXT_ONLY', pageCount: 2 });
    renderReader();

    await waitForUi(() => {
      expect(screen.getByText(/Содержимое страницы/i)).toBeInTheDocument();
    });
    // switch скрыт - PDF недоступен
    expect(screen.queryByRole('button', { name: 'PDF' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('pdf-viewer')).not.toBeInTheDocument();
  });

  it('TEXT_AND_FILE - оба режима доступны (switch с двумя кнопками)', async () => {
    mockBook({ contentKind: 'TEXT_AND_FILE', pageCount: 2 });
    renderReader();

    await waitForUi(() => {
      expect(screen.getByText(/Содержимое страницы/i)).toBeInTheDocument();
    });
    // switch отрисован с обеими кнопками. Кнопка «PDF» в text-mode не уникальна
    // (switch + inline «📕 PDF» preview в PageView), поэтому проверяем что хотя
    // бы одна есть; «Текст» уникален - только в switch.
    expect(screen.getByRole('button', { name: 'Текст' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'PDF' }).length).toBeGreaterThanOrEqual(1);
  });

  it('contentKind undefined (legacy) - backward-compatible, оба режима', async () => {
    mockBook({ pageCount: 2 });
    renderReader();

    await waitForUi(() => {
      expect(screen.getByText(/Содержимое страницы/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: 'Текст' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'PDF' }).length).toBeGreaterThanOrEqual(1);
  });

  it('deep-link ?pdf=1&bbox=... парсится и передаётся в PdfViewer как initialBbox', async () => {
    // FILE_ONLY → сразу PDF-режим; ?bbox распарсится в [0.1,0.2,0.3,0.4]
    // и долетит до prop PdfViewer (мок сериализует его в data-bbox).
    mockBook({ contentKind: 'FILE_ONLY', pageCount: 0 });
    renderReader('/books/bk-1?pdf=1&pdfPageNumber=3&bbox=0.1,0.2,0.3,0.4');

    const viewer = await screen.findByTestId('pdf-viewer');
    expect(viewer).toHaveAttribute('data-bbox', '0.1,0.2,0.3,0.4');
  });

  it('без ?bbox - initialBbox в PdfViewer пустой (null)', async () => {
    mockBook({ contentKind: 'FILE_ONLY', pageCount: 0 });
    renderReader('/books/bk-1?pdf=1&pdfPageNumber=3');

    const viewer = await screen.findByTestId('pdf-viewer');
    expect(viewer).toHaveAttribute('data-bbox', '');
  });

  it('deep-link ?fileIndex=2 парсится и передаётся в PdfViewer как initialFileIndex', async () => {
    mockBook({ contentKind: 'FILE_ONLY', pageCount: 0 });
    renderReader('/books/bk-1?pdf=1&pdfPageNumber=3&fileIndex=2&bbox=0.1,0.2,0.3,0.4');

    const viewer = await screen.findByTestId('pdf-viewer');
    expect(viewer).toHaveAttribute('data-file-index', '2');
  });

  it('без ?fileIndex - initialFileIndex в PdfViewer пустой (null, single-volume дефолт)', async () => {
    mockBook({ contentKind: 'FILE_ONLY', pageCount: 0 });
    renderReader('/books/bk-1?pdf=1&pdfPageNumber=3');

    const viewer = await screen.findByTestId('pdf-viewer');
    expect(viewer).toHaveAttribute('data-file-index', '');
  });

  it('TEXT_ONLY с 0 страниц - показывает пустое состояние «Нет страниц», а не вечный спиннер', async () => {
    mockBook({ contentKind: 'TEXT_ONLY', pageCount: 0 });
    renderReader();

    await waitForUi(() => {
      expect(screen.getByText(/В этой книге пока нет страниц/i)).toBeInTheDocument();
    });
    // спиннер «Загрузка страницы» НЕ висит
    expect(screen.queryByText(/Загрузка страницы/i)).not.toBeInTheDocument();
  });
});
