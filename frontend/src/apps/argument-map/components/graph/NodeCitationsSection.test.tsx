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
const SRC_FREE = '55555555-5555-5555-5555-555555555555';
const NODE_SOURCE_ID = '33333333-3333-3333-3333-333333333333';
const NODE_SOURCE_FREE = '66666666-6666-6666-6666-666666666666';
const NODE_SOURCE_HADITH = '77777777-7777-7777-7777-777777777777';
const BOOK_ID = '44444444-4444-4444-4444-444444444444';
const HADITH_ID = '88888888-8888-8888-8888-888888888888';

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

/** Хадис-опора — привязка через мост Source, link.hadith заполнен. */
function hadithLink() {
  return {
    id: NODE_SOURCE_HADITH,
    nodeId: NODE_ID,
    sourceId: null,
    mode: 'LEGACY',
    quote: null,
    context: null,
    hadith: {
      hadithId: HADITH_ID,
      primaryNumber: 6,
      collectionName: 'Муватта',
      previewMatn: 'إنما الأعمال بالنيات',
      status: 'CANONICAL',
    },
  };
}

/** Свободная опора (mode LEGACY, без library-привязки, без хадиса). */
function freeformLink() {
  return {
    id: NODE_SOURCE_FREE,
    nodeId: NODE_ID,
    sourceId: SRC_FREE,
    mode: 'LEGACY',
    quote: 'цитата по памяти',
    context: null,
    citation: null,
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
  it('классифицирует PDF_LINK как библиотечную опору (группа «Книги», не «Свободные»)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    // Заголовок группы «Книги» появляется, «Свободные» — нет
    expect(await screen.findByText('Книги')).toBeInTheDocument();
    expect(screen.queryByText('Свободные')).not.toBeInTheDocument();
  });

  it('свёрнутая строка показывает локатор из PdfRef: «Том 2 · стр. 137 · ▢ область»', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    await screen.findByText('Книги');
    // Локатор — единая плоская строка в свёрнутом ряду
    expect(screen.getByText(/Том\s*2/)).toBeInTheDocument();
    expect(screen.getByText(/стр\.\s*137/)).toBeInTheDocument();
    expect(screen.getByText(/область/)).toBeInTheDocument();
  });

  it('свёрнутая строка даёт deep-link кнопку «Перейти к источнику»', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    await screen.findByText('Книги');
    expect(
      screen.getByRole('button', { name: 'Перейти к источнику' }),
    ).toBeInTheDocument();
  });

  it('раскрытие строки показывает полную карточку SourceCard (chip «из библиотеки»)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection();
    await expandSection();

    // По умолчанию строка свёрнута — chip карточки не виден
    await screen.findByText('Книги');
    expect(screen.queryByText('из библиотеки')).not.toBeInTheDocument();

    // Клик по строке (заголовок книги) раскрывает полную карточку
    await userEvent.click(screen.getByText('Китаб аль-Умм'));
    expect(await screen.findByText('из библиотеки')).toBeInTheDocument();
  });

  it('считает PDF_LINK как библиотечную опору в onCountsChange (lib=1, free=0)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    const { onCountsChange } = renderSection();

    // onCountsChange зовётся при загрузке (до раскрытия секции)
    await waitForApi(() => expect(onCountsChange).toHaveBeenCalledWith({ lib: 1, free: 0 }));
  });

  it('detach-кнопка скрыта при canWrite=false (даже в раскрытой строке)', async () => {
    mockApi([pdfLinkLink()], [{ id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' }]);
    renderSection({ canWrite: false });
    await expandSection();

    await userEvent.click(await screen.findByText('Китаб аль-Умм'));
    await screen.findByText('из библиотеки');
    expect(screen.queryByRole('button', { name: /Отвязать опору/ })).not.toBeInTheDocument();
  });
});

describe('NodeCitationsSection - группировка по типу источника', () => {
  it('3 смешанные опоры рендерят 3 заголовка группы с верными счётчиками', async () => {
    mockApi(
      [hadithLink(), pdfLinkLink(), freeformLink()],
      [
        { id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' },
        { id: SRC_FREE, sourceType: 'URL', title: 'аль-Фикх' },
      ],
    );
    renderSection();
    await expandSection();

    // Все три заголовка присутствуют
    expect(await screen.findByText('Хадисы')).toBeInTheDocument();
    expect(screen.getByText('Книги')).toBeInTheDocument();
    expect(screen.getByText('Свободные')).toBeInTheDocument();

    // Счётчики групп — (1) у каждой
    const counts = screen.getAllByText('1');
    expect(counts.length).toBeGreaterThanOrEqual(3);

    // Заголовки строк видны в свёрнутом виде
    expect(screen.getByText('Муватта')).toBeInTheDocument();
    expect(screen.getByText('Китаб аль-Умм')).toBeInTheDocument();
    expect(screen.getByText('аль-Фикх')).toBeInTheDocument();
  });

  it('строки свёрнуты по умолчанию, разворачиваются по клику (хадис → matn)', async () => {
    mockApi([hadithLink()], []);
    renderSection();
    await expandSection();

    await screen.findByText('Хадисы');
    // matn хадиса не виден в свёрнутом ряду
    expect(screen.queryByText('إنما الأعمال بالنيات')).not.toBeInTheDocument();

    await userEvent.click(screen.getByText('Муватта'));
    expect(await screen.findByText('إنما الأعمال بالنيات')).toBeInTheDocument();
  });

  it('хадис-строка показывает локатор №6 и кнопку «Открыть хадис»', async () => {
    mockApi([hadithLink()], []);
    renderSection();
    await expandSection();

    await screen.findByText('Хадисы');
    expect(screen.getByText('№6')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Открыть хадис' })).toBeInTheDocument();
  });

  it('пустые группы не рендерятся (только хадис → нет «Книги»/«Свободные»)', async () => {
    mockApi([hadithLink()], []);
    renderSection();
    await expandSection();

    await screen.findByText('Хадисы');
    expect(screen.queryByText('Книги')).not.toBeInTheDocument();
    expect(screen.queryByText('Свободные')).not.toBeInTheDocument();
  });

  it('считает смешанный набор корректно в onCountsChange (lib=1, free=2)', async () => {
    // хадис без library-mode + свободная = 2 free; PDF_LINK = 1 lib
    mockApi(
      [hadithLink(), pdfLinkLink(), freeformLink()],
      [
        { id: SRC_PDF_LINK, sourceType: 'BOOK', title: 'Китаб аль-Умм' },
        { id: SRC_FREE, sourceType: 'URL', title: 'аль-Фикх' },
      ],
    );
    const { onCountsChange } = renderSection();

    await waitForApi(() => expect(onCountsChange).toHaveBeenCalledWith({ lib: 1, free: 2 }));
  });
});
