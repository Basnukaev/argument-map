import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { HadithGradesSection } from './HadithGradesSection';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const SOURCE_ID = 'src-h-1';
const USER_ID = 'user-1';
const OTHER_USER_ID = 'user-2';

function setUser(id: string, role: 'USER' | 'ADMIN' = 'USER') {
  useAuthStore.setState({
    user: { id, username: 'tester', email: 't@x', role },
    accessToken: 'token',
    isLoading: false,
    initialized: true,
  });
}

describe('HadithGradesSection', () => {
  beforeEach(() => {
    setUser(USER_ID);
    // HTMLDialogElement polyfill в jsdom не реализует showModal/close
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function () {
        this.setAttribute('open', '');
      };
    }
    if (!HTMLDialogElement.prototype.close) {
      HTMLDialogElement.prototype.close = function () {
        this.removeAttribute('open');
      };
    }
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

  it('не рендерит ничего для не-HADITH source', () => {
    const { container } = render(<HadithGradesSection sourceId={SOURCE_ID} sourceType="BOOK" />);
    expect(container.firstChild).toBeNull();
  });

  it('после раскрытия с empty list показывает empty state и кнопку «Добавить первую оценку»', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/${SOURCE_ID}/grades`, () => HttpResponse.json([])),
    );

    render(<HadithGradesSection sourceId={SOURCE_ID} sourceType="HADITH" />);
    const toggle = await screen.findByRole('button', { name: /Оценки учёных/i });
    await userEvent.click(toggle);

    await waitForApi(() => {
      expect(screen.getByText(/Пока нет оценок учёных/i)).toBeInTheDocument();
    });
    expect(screen.getByTestId('hadith-grades-add-first')).toBeInTheDocument();
  });

  it('рендерит список оценок с правильными badge и edit/delete только для своих', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources/${SOURCE_ID}/grades`, () =>
        HttpResponse.json([
          {
            id: 'g-1',
            sourceId: SOURCE_ID,
            scholarId: 'auth-1',
            scholarName: 'Albani',
            scholarFullName: 'Muhammad Nasir al-Din al-Albani',
            scholarDeathYearHijri: 1420,
            grade: 'SAHIH',
            gradeCitation: 'Silsila Sahiha 1/123',
            comment: 'short note',
            createdBy: USER_ID,
            createdAt: '2026-05-18T10:00:00Z',
          },
          {
            id: 'g-2',
            sourceId: SOURCE_ID,
            scholarId: 'auth-2',
            scholarName: 'Bukhari',
            scholarFullName: 'Imam Bukhari',
            grade: 'DAIF',
            createdBy: OTHER_USER_ID,
            createdAt: '2026-05-18T11:00:00Z',
          },
        ]),
      ),
    );

    render(<HadithGradesSection sourceId={SOURCE_ID} sourceType="HADITH" />);
    await userEvent.click(await screen.findByRole('button', { name: /Оценки учёных/i }));

    expect(await screen.findByText('Muhammad Nasir al-Din al-Albani')).toBeInTheDocument();
    expect(screen.getByText('Imam Bukhari')).toBeInTheDocument();
    expect(screen.getByTestId('hadith-grade-badge-SAHIH')).toBeInTheDocument();
    expect(screen.getByTestId('hadith-grade-badge-DAIF')).toBeInTheDocument();

    // Edit/Delete только для своей оценки (1) - не для чужой (1)
    expect(screen.getAllByTestId('hadith-grade-edit')).toHaveLength(1);
    expect(screen.getAllByTestId('hadith-grade-delete')).toHaveLength(1);
  });

  it('ADMIN видит edit/delete на чужих оценках', async () => {
    setUser('admin-1', 'ADMIN');
    server.use(
      http.get(`${BASE}/api/v1/sources/${SOURCE_ID}/grades`, () =>
        HttpResponse.json([
          {
            id: 'g-other',
            sourceId: SOURCE_ID,
            scholarId: 'auth-x',
            scholarName: 'Other',
            grade: 'HASAN',
            createdBy: OTHER_USER_ID,
            createdAt: '2026-05-18T10:00:00Z',
          },
        ]),
      ),
    );

    render(<HadithGradesSection sourceId={SOURCE_ID} sourceType="HADITH" />);
    await userEvent.click(await screen.findByRole('button', { name: /Оценки учёных/i }));

    expect(await screen.findByTestId('hadith-grade-edit')).toBeInTheDocument();
    expect(screen.getByTestId('hadith-grade-delete')).toBeInTheDocument();
  });

  it('delete grade с confirm - DELETE запрос + refresh', async () => {
    vi.stubGlobal('confirm', () => true);

    let deleteCalled = false;
    let getCallNo = 0;
    server.use(
      http.get(`${BASE}/api/v1/sources/${SOURCE_ID}/grades`, () => {
        getCallNo += 1;
        if (getCallNo === 1) {
          return HttpResponse.json([
            {
              id: 'g-d',
              sourceId: SOURCE_ID,
              scholarId: 'auth-1',
              scholarName: 'Scholar',
              grade: 'SAHIH',
              createdBy: USER_ID,
              createdAt: '2026-05-18T10:00:00Z',
            },
          ]);
        }
        return HttpResponse.json([]);
      }),
      http.delete(`${BASE}/api/v1/sources/grades/g-d`, () => {
        deleteCalled = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    render(<HadithGradesSection sourceId={SOURCE_ID} sourceType="HADITH" />);
    await userEvent.click(await screen.findByRole('button', { name: /Оценки учёных/i }));
    const deleteBtn = await screen.findByTestId('hadith-grade-delete');
    await userEvent.click(deleteBtn);

    await waitForApi(() => {
      expect(deleteCalled).toBe(true);
    });
  });
});
