import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import CitationPicker from './CitationPicker';

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

  it('для FILE_ONLY книги показывает сообщение о недоступности, не спиннер «Загрузка страницы»', async () => {
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

    renderPicker();

    // Дождаться загрузки списка книг и клик по книге
    const bookButton = await screen.findByRole('button', { name: 'Скан без текста' });
    bookButton.click();

    // Должно появиться сообщение о недоступности для FILE_ONLY
    await waitForApi(() => {
      expect(
        screen.getByText(
          /Эта книга — только PDF.*Цитирование по фрагменту текста пока недоступно/,
        ),
      ).toBeInTheDocument();
    });

    // Спиннер «Загрузка страницы» (reader.page_loading) НЕ должен быть виден
    // (нет бесконечной крутилки ожидания страниц)
    expect(screen.queryByText('Загрузка страницы')).not.toBeInTheDocument();
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
