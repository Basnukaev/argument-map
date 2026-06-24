import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import CurationFieldsPanel, { type CurationFieldSpec } from './CurationFieldsPanel';

const BASE = 'http://test.local';
const PUT_URL = `${BASE}/api/v1/admin/curation/overrides`;

afterEach(() => {
  useToastStore.getState().clear();
});

const FIELDS: CurationFieldSpec[] = [
  { label: 'Учёный', fieldName: 'ruler_name', value: 'البخاري', kind: 'text' },
  { label: 'Год смерти', fieldName: 'ruler_death_year', value: 256, kind: 'number' },
];

describe('CurationFieldsPanel', () => {
  it('не-ADMIN: панель не рендерится вовсе', () => {
    const { container } = render(
      <CurationFieldsPanel
        entityTable="hd_rulings"
        entityId="r1"
        fields={FIELDS}
        role="STUDENT"
        onChanged={() => {}}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('аноним (role undefined): панель не рендерится', () => {
    const { container } = render(
      <CurationFieldsPanel
        entityTable="hd_rulings"
        entityId="r1"
        fields={FIELDS}
        role={undefined}
        onChanged={() => {}}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('ADMIN: видит подписи полей + карандаш на каждом', () => {
    render(
      <CurationFieldsPanel
        entityTable="hd_rulings"
        entityId="r1"
        fields={FIELDS}
        role="ADMIN"
        onChanged={() => {}}
      />,
    );
    expect(screen.getByText('Учёный')).toBeInTheDocument();
    expect(screen.getByText('Год смерти')).toBeInTheDocument();
    expect(
      screen.getAllByRole('button', { name: 'Исправить администратором' }),
    ).toHaveLength(2);
  });

  it('ADMIN: правка поля → PUT с правильным fieldName → onChanged + toast', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.put(PUT_URL, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'o1', value: String(body.value) });
      }),
    );
    let changed = false;

    render(
      <CurationFieldsPanel
        entityTable="hd_rulings"
        entityId="r1"
        fields={FIELDS}
        role="ADMIN"
        onChanged={() => {
          changed = true;
        }}
      />,
    );

    // Правим первое поле (ruler_name).
    await userEvent.click(
      screen.getAllByRole('button', { name: 'Исправить администратором' })[0]!,
    );
    const input = screen.getByRole('textbox');
    await userEvent.clear(input);
    await userEvent.type(input, 'مسلم');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      expect(changed).toBe(true);
    });
    expect(body).toEqual({
      entityTable: 'hd_rulings',
      entityId: 'r1',
      fieldName: 'ruler_name',
      value: 'مسلم',
    });
  });
});
