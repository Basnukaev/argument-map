import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import HideToggle from './HideToggle';

const BASE = 'http://test.local';
const OVERRIDES_URL = `${BASE}/api/v1/admin/curation/overrides`;

afterEach(() => {
  useToastStore.getState().clear();
});

describe('HideToggle', () => {
  it('не-ADMIN: ничего не рендерит', () => {
    const { container } = render(
      <HideToggle
        entityTable="hd_rulings"
        entityId="r1"
        hiddenByAdmin={false}
        role="STUDENT"
        onChanged={() => {}}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('аноним (role undefined): ничего не рендерит', () => {
    const { container } = render(
      <HideToggle
        entityTable="hd_rulings"
        entityId="r1"
        hiddenByAdmin={false}
        role={undefined}
        onChanged={() => {}}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('ADMIN, запись видна: кнопка «Скрыть», форма требует непустую причину', async () => {
    render(
      <HideToggle
        entityTable="hd_rulings"
        entityId="r1"
        hiddenByAdmin={false}
        role="ADMIN"
        onChanged={() => {}}
      />,
    );

    // Старт — одна кнопка «Скрыть» (открывает форму).
    await userEvent.click(screen.getByRole('button', { name: 'Скрыть' }));

    // Форма раскрыта: textarea причины + кнопка submit заблокирована, пока пусто.
    const textarea = screen.getByRole('textbox');
    expect(textarea).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Скрыть' });
    expect(submit).toBeDisabled();

    // Ввели причину → submit разблокирован.
    await userEvent.type(textarea, 'спам');
    expect(submit).toBeEnabled();
  });

  it('ADMIN: скрыть → PUT с {fieldName:"__record__", hidden:true, reason} → onChanged + toast', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.put(OVERRIDES_URL, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'o1' });
      }),
    );
    let changed = false;

    render(
      <HideToggle
        entityTable="hd_explanations"
        entityId="e1"
        hiddenByAdmin={false}
        role="ADMIN"
        onChanged={() => {
          changed = true;
        }}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Скрыть' }));
    await userEvent.type(screen.getByRole('textbox'), 'дубликат');
    await userEvent.click(screen.getByRole('button', { name: 'Скрыть' }));

    await waitForApi(() => {
      expect(changed).toBe(true);
    });
    expect(body).toEqual({
      entityTable: 'hd_explanations',
      entityId: 'e1',
      fieldName: '__record__',
      hidden: true,
      reason: 'дубликат',
    });
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0]!.kind).toBe('success');
  });

  it('ADMIN, запись скрыта: кнопка «Показать снова» → DELETE …fieldName=__record__', async () => {
    let deletedUrl: string | null = null;
    server.use(
      http.delete(OVERRIDES_URL, ({ request }) => {
        deletedUrl = request.url;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    let changed = false;

    render(
      <HideToggle
        entityTable="hd_narrator_commentaries"
        entityId="c1"
        hiddenByAdmin
        hideReason="не относится"
        role="ADMIN"
        onChanged={() => {
          changed = true;
        }}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Показать снова' }));

    await waitForApi(() => {
      expect(changed).toBe(true);
    });
    const url = new URL(deletedUrl!);
    expect(url.searchParams.get('entityTable')).toBe('hd_narrator_commentaries');
    expect(url.searchParams.get('entityId')).toBe('c1');
    expect(url.searchParams.get('fieldName')).toBe('__record__');
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0]!.kind).toBe('success');
  });
});
