import { useEffect } from 'react';
import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { waitForUi } from '@/test/asyncHelpers';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import PdfViewer from './PdfViewer';

const BASE = 'http://test.local';

// react-pdf (+ pdfjs-dist worker) не работает в jsdom: тянет canvas, Web
// Worker, Range-запросы. Мокаем минимально:
// - Document: рендерит children и в effect зовёт onLoadSuccess({numPages})
//   (имитирует загрузку PDF), чтобы numPages выставился и clamp-логика
//   отработала как в проде. onLoadSuccess в effect, а не в render - иначе
//   setState родителя во время render Document'а (React warning).
// - Page: маркер вместо реального canvas; bbox-overlay рендерится как
//   sibling рядом с ним внутри обёртки PdfViewer, мок Page его не трогает.
// - pdfjs: заглушка GlobalWorkerOptions (PdfViewer присваивает workerSrc
//   на module-eval).
vi.mock('react-pdf', () => ({
  Document: ({
    children,
    onLoadSuccess,
  }: {
    children: React.ReactNode;
    onLoadSuccess?: (arg: { numPages: number }) => void;
  }) => {
    useEffect(() => {
      onLoadSuccess?.({ numPages: 10 });
    }, [onLoadSuccess]);
    return <div data-testid="pdf-document">{children}</div>;
  },
  Page: ({ pageNumber }: { pageNumber: number }) => (
    <div data-testid="pdf-page">page {pageNumber}</div>
  ),
  pdfjs: { GlobalWorkerOptions: { workerSrc: '' } },
}));

// CSS-импорты react-pdf (AnnotationLayer.css / TextLayer.css) - заглушки,
// jsdom их не парсит.
vi.mock('react-pdf/dist/Page/AnnotationLayer.css', () => ({}));
vi.mock('react-pdf/dist/Page/TextLayer.css', () => ({}));

/** Один не-cover PDF-файл с метаданными - минимум для /pdf/info. */
function mockPdfInfo() {
  server.use(
    http.get(`${BASE}/api/v1/library/books/bk-1/pdf/info`, () =>
      HttpResponse.json({
        hasCover: false,
        files: [{ index: 0, label: '01_123', isCover: false, pageCount: 10 }],
      }),
    ),
  );
}

/** Два не-cover тома - чтобы появился volume selector (multi-volume). */
function mockMultiVolumePdfInfo() {
  server.use(
    http.get(`${BASE}/api/v1/library/books/bk-1/pdf/info`, () =>
      HttpResponse.json({
        hasCover: false,
        files: [
          { index: 0, label: '01_123', isCover: false, pageCount: 10 },
          { index: 1, label: '02_456', isCover: false, pageCount: 10 },
        ],
      }),
    ),
  );
}

describe('PdfViewer / bbox-подсветка цитаты', () => {
  // jsdom не реализует Element.scrollIntoView - нужен для volume Select.
  beforeAll(() => {
    if (!Element.prototype.scrollIntoView) {
      Element.prototype.scrollIntoView = function () {};
    }
  });

  beforeEach(() => {
    server.resetHandlers();
  });

  it('рендерит overlay-подсветку когда initialBbox задан и страница совпадает с deep-link', async () => {
    mockPdfInfo();
    render(
      <PdfViewer
        bookId="bk-1"
        initialPrintedPage={3}
        initialBbox={[0.1, 0.2, 0.3, 0.4]}
      />,
    );

    const overlay = await screen.findByTestId('pdf-bbox-highlight');
    expect(overlay).toBeInTheDocument();
    // Позиционирование в процентах от обёртки <Page> → масштабируется с zoom.
    expect(overlay).toHaveStyle({
      left: '10%',
      top: '20%',
      width: '30%',
      height: '40%',
    });
  });

  it('не рендерит overlay когда initialBbox не задан', async () => {
    mockPdfInfo();
    render(<PdfViewer bookId="bk-1" initialPrintedPage={3} />);

    // Дожидаемся рендера страницы, затем убеждаемся что overlay отсутствует.
    await waitForUi(() => {
      expect(screen.getByTestId('pdf-page')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('pdf-bbox-highlight')).not.toBeInTheDocument();
  });

  it('подсветка исчезает после смены тома (не всплывает на стр.1 чужого тома)', async () => {
    // deepLinkPage=1 (initialPrintedPage=1): подсветка видна на стр.1 тома 1.
    // После смены тома changePage(1) оставляет pageNumber=1=deepLinkPage,
    // но bbox принадлежит исходному тому → не должна всплыть снова.
    mockMultiVolumePdfInfo();
    render(
      <PdfViewer
        bookId="bk-1"
        initialPrintedPage={1}
        initialBbox={[0.1, 0.2, 0.3, 0.4]}
      />,
    );

    // Подсветка видна на стр.1 первого тома.
    expect(await screen.findByTestId('pdf-bbox-highlight')).toBeInTheDocument();

    // Переключаем том: открываем Select и кликаем «Том 2».
    // Внутри role=option лежит <button> с onClick - кликаем по тексту.
    await userEvent.click(screen.getByRole('button', { name: 'Выбор тома' }));
    await userEvent.click(await screen.findByText('Том 2'));

    // Подсветка исчезла, хотя страница снова стр.1 (=deepLinkPage).
    await waitForUi(() => {
      expect(screen.queryByTestId('pdf-bbox-highlight')).not.toBeInTheDocument();
    });
  });
});
