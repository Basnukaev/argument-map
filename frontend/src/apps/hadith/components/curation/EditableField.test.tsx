import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import EditableField from './EditableField';

const BASE = 'http://test.local';
const PUT_URL = `${BASE}/api/v1/admin/curation/overrides`;

afterEach(() => {
  useToastStore.getState().clear();
});

const ENUM_OPTIONS = [
  { value: 'SAHIH', label: 'SAHIH' },
  { value: 'HASAN', label: 'HASAN' },
  { value: 'DAIF', label: 'DAIF' },
];

describe('EditableField', () => {
  it('не-ADMIN: значение показано plain, без карандаша', () => {
    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="status"
        value="CANONICAL"
        kind="text"
        role="STUDENT"
        onSaved={() => {}}
      />,
    );

    expect(screen.getByText('CANONICAL')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Исправить администратором' }),
    ).not.toBeInTheDocument();
  });

  it('аноним (role undefined): карандаша нет', () => {
    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="status"
        value="CANONICAL"
        kind="text"
        role={undefined}
        onSaved={() => {}}
      />,
    );

    expect(
      screen.queryByRole('button', { name: 'Исправить администратором' }),
    ).not.toBeInTheDocument();
  });

  it('ADMIN: виден карандаш', () => {
    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="status"
        value="CANONICAL"
        kind="text"
        role="ADMIN"
        onSaved={() => {}}
      />,
    );

    expect(
      screen.getByRole('button', { name: 'Исправить администратором' }),
    ).toBeInTheDocument();
  });

  it('ADMIN + enum: клик по карандашу → select с опциями', async () => {
    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="authenticity"
        value="HASAN"
        kind="enum"
        options={ENUM_OPTIONS}
        role="ADMIN"
        onSaved={() => {}}
      />,
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Исправить администратором' }),
    );

    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();
    // Все опции отрендерены.
    expect(screen.getByRole('option', { name: 'SAHIH' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'HASAN' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'DAIF' })).toBeInTheDocument();
    // Предзаполнено текущим значением.
    expect(select).toHaveValue('HASAN');
  });

  it('ADMIN text: правка → PUT с {entityTable, entityId, fieldName, value} → onSaved + toast', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.put(PUT_URL, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'o1', value: String(body.value) });
      }),
    );
    let savedCalled = false;

    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="hadith_type"
        value="مرفوع"
        kind="text"
        role="ADMIN"
        onSaved={() => {
          savedCalled = true;
        }}
      />,
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Исправить администратором' }),
    );
    const input = screen.getByRole('textbox');
    await userEvent.clear(input);
    await userEvent.type(input, 'موقوف');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      expect(savedCalled).toBe(true);
    });
    expect(body).toEqual({
      entityTable: 'hd_hadiths',
      entityId: 'h1',
      fieldName: 'hadith_type',
      value: 'موقوف',
    });
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0]!.kind).toBe('success');
  });

  it('«Отмена» → выход из режима правки без PUT', async () => {
    // Никаких PUT-handlers — запрос провалил бы тест.
    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="status"
        value="CANONICAL"
        kind="text"
        role="ADMIN"
        onSaved={() => {}}
      />,
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Исправить администратором' }),
    );
    expect(screen.getByRole('textbox')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Отмена' }));

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText('CANONICAL')).toBeInTheDocument();
  });

  it('ошибка PUT (403) → toast.error, остаётся в режиме правки', async () => {
    server.use(
      http.put(PUT_URL, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Forbidden', status: 403, detail: 'Доступ запрещён' },
          { status: 403 },
        ),
      ),
    );

    render(
      <EditableField
        entityTable="hd_hadiths"
        entityId="h1"
        fieldName="status"
        value="CANONICAL"
        kind="text"
        role="ADMIN"
        onSaved={() => {}}
      />,
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Исправить администратором' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0]!.kind).toBe('error');
    });
    // Остались в редакторе (PUT не прошёл).
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });
});
