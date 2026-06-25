import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import NodeCitationsSection from './NodeCitationsSection';

// jsdom не реализует HTMLDialogElement.showModal/close (модалки внутри секции)
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  }
});

const BASE = 'http://test.local';
const NODE_ID = '11111111-1111-1111-1111-111111111111';
const SRC_PDF_LINK = '22222222-2222-2222-2222-222222222222';
const NODE_SOURCE_ID = '33333333-3333-3333-3333-333333333333';
const BOOK_ID = '44444444-4444-4444-4444-444444444444';

function pagedSources(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 100,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  };
}

/**
 * PDF_LINK-цитата (ADR-067, FILE_ONLY книга archive.org): LocationRef пуст,
 * локатор живёт в PdfRef. Backend (DtoMappers.toPdfRef) кладёт fileIndex /
 * pageNumber / bbox; location=null. Раньше фронт ошибочно классифицировал
 * её как «Свободную» (isLibraryMode пропускал PDF_LINK).
 */
function pdfLinkLink() {
  return {
    id: NODE_SOURCE_ID,
    nodeId: NODE_ID,
    sourceId: SRC_PDF_LINK,
    mode: 'PDF_LINK',
    quote: 'выделенный фрагмент',
    context: null,
    citation: {
      book: { id: BOOK_ID, title: 'Китаб аль-Умм' },
      location: null,
      pdf: {
        fileIndex: 2,
        pageNumber: 137,
        bbox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4 },
      },
    },
  };
}

function mockApi(links: unknown[], sources: unknown[] = []) {
  server.use(
    http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () => HttpResponse.json(links)),
    http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(pagedSources(sources))),
    http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(pagedSources([]))),
  );
}

function renderSection(over: Partial<Parameters<typeof NodeCitationsSection>[0]> = {}) {
  const onCountsChange = vi.fn();
  const result = render(
    <MemoryRouter>
      <NodeCitationsSection
        nodeId={NODE_ID}
        nodeContent="узел"
        onCountsChange={onCountsChange}
        {...over}
      />
    </MemoryRouter>,
  );
  return { ...result, onCountsChange };
}

/** Секция «Опора» свёрнута по умолчанию (defaultOpen=false) - раскрываем. */
async function expandSection() {
  await userEvent.click(screen.getByRole('button', { name: /Опора/ }));
}

describe('NodeCitationsSection - PDF_LINK цитата (ADR-067)', () => {
  it('рендерит PDF_LINK как библиотечную карточку, не «Свободную»', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    // SourceCard рисует chip «из библиотеки», freeform — бейдж «Свободная»
    expect(await screen.findByText('из библиотеки')).toBeInTheDocument();
    expect(screen.queryByText('Свободная')).not.toBeInTheDocument();
  });

  it('показывает локатор из PdfRef: страница, том и чип области', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    await screen.findByText('из библиотеки');
    // стр. 137 из pdf.pageNumber (LocationRef пуст)
    expect(screen.getByText('137')).toBeInTheDocument();
    expect(screen.getByText(/стр\./)).toBeInTheDocument();
    // том из pdf.fileIndex (сырой 0-based ordinal — показываем как есть)
    expect(screen.getByText(/Том\s*2/)).toBeInTheDocument();
    // чип «область» т.к. bbox задан
    expect(screen.getByText('область')).toBeInTheDocument();
  });

  it('показывает кнопку «Перейти к источнику» (deep-link собран)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    await screen.findByText('из библиотеки');
    expect(screen.getByRole('button', { name: 'Перейти к источнику' })).toBeInTheDocument();
  });

  it('считает PDF_LINK как библиотечную опору в onCountsChange (lib=1, free=0)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    const { onCountsChange } = renderSection();

    // onCountsChange зовётся при загрузке (до раскрытия секции)
    await waitForApi(() => expect(onCountsChange).toHaveBeenCalledWith({ lib: 1, free: 0 }));
  });

  it('detach-кнопка скрыта при canWrite=false', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection({ canWrite: false });
    await expandSection();

    await screen.findByText('из библиотеки');
    expect(screen.queryByRole('button', { name: /Отвязать опору/ })).not.toBeInTheDocument();
  });
});
