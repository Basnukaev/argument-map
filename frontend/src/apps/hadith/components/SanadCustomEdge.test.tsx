import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import { TransmissionPhraseChip } from './SanadCustomEdge';

const BASE = 'http://test.local';
const PATCH_URL = `${BASE}/api/v1/hadith/sanad-narrators/transmission-phrase`;

afterEach(() => {
  useToastStore.getState().clear();
});

/**
 * Курация Фаза 5.b: чип формулы передачи на ребре графа иснада. Чип
 * тестируется в изоляции (React Flow рисует подписи-рёбра через измерение
 * layout, которого нет в jsdom — тот же приём, что в SanadGraph.test и
 * sanadEdge.test). Проверяем ADMIN-гейт + PATCH body {hadithId, position, phrase}.
 */
describe('TransmissionPhraseChip', () => {
  it('не-ADMIN: read-only текст без карандаша', () => {
    render(
      <TransmissionPhraseChip
        phrase="حدثنا"
        dimmed={false}
        hadithId="h1"
        position={1}
        role="USER"
        onGraphEdited={vi.fn()}
      />,
    );
    expect(screen.getByText('حدثنا')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Исправить администратором' }),
    ).not.toBeInTheDocument();
  });

  it('version-ребро (position null): даже ADMIN не редактирует формулу', () => {
    render(
      <TransmissionPhraseChip
        phrase="عن"
        dimmed={false}
        hadithId="h1"
        position={null}
        role="ADMIN"
        onGraphEdited={vi.fn()}
      />,
    );
    expect(screen.getByText('عن')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Исправить администратором' }),
    ).not.toBeInTheDocument();
  });

  it('ADMIN: карандаш → редактор → PATCH с {hadithId, position, phrase}', async () => {
    let patchBody: { hadithId: string; position: number; phrase: string } | null = null;
    server.use(
      http.patch(PATCH_URL, async ({ request }) => {
        patchBody = (await request.json()) as typeof patchBody;
        return HttpResponse.json({ hadithId: 'h1', position: 1, phrase: 'أخبرنا' });
      }),
    );
    const onGraphEdited = vi.fn();
    render(
      <TransmissionPhraseChip
        phrase="حدثنا"
        dimmed={false}
        hadithId="h1"
        position={1}
        role="ADMIN"
        onGraphEdited={onGraphEdited}
      />,
    );

    // карандаш виден ADMIN'у → клик открывает инлайн-редактор
    await userEvent.click(screen.getByRole('button', { name: 'Исправить администратором' }));
    const input = screen.getByRole('textbox', { name: 'Исправить администратором' });
    await userEvent.clear(input);
    await userEvent.type(input, 'أخبرنا');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      expect(onGraphEdited).toHaveBeenCalledTimes(1);
    });
    // тело PATCH — стабильный ключ (hadithId, position) + новая формула
    expect(patchBody).toEqual({ hadithId: 'h1', position: 1, phrase: 'أخبرنا' });
  });

  it('ADMIN: индикатор «отредактировано» при overridden=true', () => {
    render(
      <TransmissionPhraseChip
        phrase="أخبرنا"
        dimmed={false}
        hadithId="h1"
        position={1}
        overridden
        role="ADMIN"
        onGraphEdited={vi.fn()}
      />,
    );
    // точка-маркер с aria-label «Правка полей»
    expect(screen.getByLabelText('Правка полей')).toBeInTheDocument();
  });
});
