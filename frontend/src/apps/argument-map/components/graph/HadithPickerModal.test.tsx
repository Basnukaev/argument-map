import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import HadithPickerModal from './HadithPickerModal';

const BASE = 'http://test.local';

function paged(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
  };
}

const COLLECTIONS = [
  {
    id: 'c1',
    slug: 'bukhari',
    nameAr: 'صحيح البخاري',
    nameEn: 'Sahih al-Bukhari',
    nameRu: 'Сахих аль-Бухари',
    totalHadith: 7563,
    hadithCount: 100,
  },
];

const HADITH = {
  id: 'h1',
  collectionId: 'c1',
  primaryNumber: 1,
  normalizedMatn: 'norm',
  previewMatn: 'إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ',
  status: 'CANONICAL',
  sourceId: null,
  createdAt: '2026-01-01',
};

describe('HadithPickerModal', () => {
  it('рендерит карточки хадисов с previewMatn и сборником', async () => {
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, () => HttpResponse.json(paged([HADITH]))),
    );
    render(<HadithPickerModal open onClose={() => {}} onSelect={() => {}} />);

    await waitForApi(() => {
      expect(screen.getByText(/إِنَّمَا الأَعْمَالُ/)).toBeInTheDocument();
    });
    expect(screen.getByText('№1')).toBeInTheDocument();
  });

  it('клик по карточке вызывает onSelect(hadithId) и закрывает модалку', async () => {
    const onSelect = vi.fn();
    const onClose = vi.fn();
    server.use(
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths`, () => HttpResponse.json(paged([HADITH]))),
    );
    render(<HadithPickerModal open onClose={onClose} onSelect={onSelect} />);

    await waitForApi(() => {
      expect(screen.getByText(/إِنَّمَا الأَعْمَالُ/)).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText(/إِنَّمَا الأَعْمَالُ/));

    await waitFor(() => {
      expect(onSelect).toHaveBeenCalledWith('h1');
      expect(onClose).toHaveBeenCalled();
    });
  });
});
