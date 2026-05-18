import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import SourceDetailPanel from './SourceDetailPanel';
import { useSourceDetailPanelStore } from '@/shared/stores/sourceDetailPanelStore';

const BASE = 'http://test.local';

function renderPanel() {
  return render(
    <MemoryRouter>
      <SourceDetailPanel />
    </MemoryRouter>,
  );
}

describe('SourceDetailPanel', () => {
  beforeEach(() => {
    // matchMedia stub - useIsMobile подписан через useSyncExternalStore.
    // По умолчанию desktop (matches=false для max-width:767px)
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

    useSourceDetailPanelStore.setState({ current: null, isOpen: false });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('не рендерит ничего пока store закрыт', () => {
    const { container } = renderPanel();
    expect(container.firstChild).toBeNull();
  });

  it('после openWith - panel виден, делает GET /api/v1/sources/{id}', async () => {
    let receivedUrl: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/sources/src-1`, ({ request }) => {
        receivedUrl = request.url;
        return HttpResponse.json({
          id: 'src-1',
          sourceType: 'BOOK',
          title: 'Сахих аль-Бухари',
          bookId: 'book-1',
        });
      }),
    );

    renderPanel();
    act(() => {
      useSourceDetailPanelStore.getState().openWith({
        sourceId: 'src-1',
        quote: 'إنّما الأعمال بالنّيات',
      });
    });

    await waitForApi(() => {
      expect(receivedUrl).toContain('/api/v1/sources/src-1');
    });

    expect(screen.getByTestId('source-detail-panel')).toBeInTheDocument();
    expect(await screen.findByText('Сахих аль-Бухари')).toBeInTheDocument();
  });

  it('отображает quote section если quote есть в citation', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/src-q`, () =>
        HttpResponse.json({ id: 'src-q', sourceType: 'HADITH', title: 'Title' }),
      ),
    );

    renderPanel();
    act(() => {
      useSourceDetailPanelStore.getState().openWith({
        sourceId: 'src-q',
        quote: 'lorem ipsum dolor sit amet',
      });
    });

    expect(await screen.findByText('lorem ipsum dolor sit amet')).toBeInTheDocument();
  });

  it('показывает кнопку «Открыть полностью» для BOOK source с bookId', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/src-2`, () =>
        HttpResponse.json({
          id: 'src-2',
          sourceType: 'BOOK',
          title: 'Книга',
          bookId: 'b-42',
        }),
      ),
    );

    renderPanel();
    act(() => {
      useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-2' });
    });

    expect(await screen.findByTestId('source-detail-open-full')).toBeInTheDocument();
  });

  it('клик по close button вызывает store.close', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/src-3`, () =>
        HttpResponse.json({ id: 'src-3', sourceType: 'URL', title: 'X' }),
      ),
    );

    renderPanel();
    act(() => {
      useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-3' });
    });

    const closeBtn = await screen.findByRole('button', { name: 'Закрыть панель' });
    await userEvent.click(closeBtn);

    expect(useSourceDetailPanelStore.getState().isOpen).toBe(false);
    expect(useSourceDetailPanelStore.getState().current).toBeNull();
  });

  it('error при fetch - показывает текст ошибки', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/src-err`, () =>
        HttpResponse.json({ title: 'Not found' }, { status: 404 }),
      ),
    );

    renderPanel();
    act(() => {
      useSourceDetailPanelStore.getState().openWith({ sourceId: 'src-err' });
    });

    // formatApiError возвращает либо сообщение из problem json либо fallback
    await waitForApi(() => {
      expect(screen.getByTestId('source-detail-panel')).toBeInTheDocument();
    });
    // Должна появиться текстовая ошибка (через formatApiError fallback)
    const errEl = await screen.findByText(/Not found|Не удалось загрузить источник/);
    expect(errEl).toBeInTheDocument();
  });
});
