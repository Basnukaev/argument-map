import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import NarratorPanel from './NarratorPanel';
import type { SanadFlowNodeData } from '@/apps/hadith/types';

const PUT_URL = 'http://test.local/api/v1/admin/curation/overrides';

afterEach(() => {
  useToastStore.getState().clear();
});

const DATA: SanadFlowNodeData = {
  narratorId: 'n1',
  nameAr: 'مالك بن أنس',
  nameLatin: 'malik ibn anas',
  nameRu: 'Малик ибн Анас',
  kunya: 'أبو عبد الله',
  laqab: 'إمام دار الهجرة',
  yearBirthHijri: 93,
  yearDeathHijri: 179,
  birthplace: 'Медина',
  primaryResidence: 'Медина',
  deathPlace: 'Медина',
  reliabilityGrade: 'THIQA',
  reliabilityComment: 'Имам Медины, автор Муватты',
  generation: 'Атба ат-табиин',
  tabaqa: null,
  gradeText: null,
  externalId: null,
  collection: null,
  tier: 5,
  role: 'COLLECTOR',
};

describe('NarratorPanel', () => {
  it('показывает имя, перевод, оценку надёжности, кунью и биографию', () => {
    render(<NarratorPanel data={DATA} onClose={() => {}} />);
    expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    expect(screen.getByText('Малик ибн Анас')).toBeInTheDocument();
    expect(screen.getByText('ثقة')).toBeInTheDocument();
    expect(screen.getByText('أبو عبد الله')).toBeInTheDocument();
    expect(screen.getByText('Имам Медины, автор Муватты')).toBeInTheDocument();
    expect(screen.getByText(/93.*179/)).toBeInTheDocument();
  });

  it('вызывает onClose по кнопке закрытия', async () => {
    const onClose = vi.fn();
    render(<NarratorPanel data={DATA} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('textForm (клик из текста) → подпись «في الإسناد: …»', () => {
    // textForm отличается от канонического имени (الفاكهي vs الخزاعي кейс)
    render(<NarratorPanel data={DATA} onClose={() => {}} textForm="الفاكهي" />);
    // подпись с формой имени как в иснаде (label + textForm в одном узле)
    expect(screen.getByText(/في الإسناد:\s*الفاكهي/)).toBeInTheDocument();
  });

  it('без textForm (клик из графа) → подписи «في الإسناد» нет', () => {
    render(<NarratorPanel data={DATA} onClose={() => {}} />);
    expect(screen.queryByText(/في الإسناد/)).not.toBeInTheDocument();
  });

  // ── Курация Фаза 5.b: ADMIN inline-правка полей рави из панели графа ─────────

  it('не-ADMIN: панели правки полей рави нет (read-only)', () => {
    render(<NarratorPanel data={DATA} onClose={() => {}} role="STUDENT" onEdited={() => {}} />);
    expect(
      screen.queryByRole('button', { name: 'Исправить администратором' }),
    ).not.toBeInTheDocument();
  });

  it('ADMIN с overriddenFields → индикатор «отредактировано» + карандаши правки', () => {
    render(
      <NarratorPanel
        data={{ ...DATA, overriddenFields: ['reliability_grade'] }}
        onClose={() => {}}
        role="ADMIN"
        onEdited={() => {}}
      />,
    );
    // admin-индикатор «отредактировано» (label = «Правка полей»)
    expect(screen.getAllByLabelText('Правка полей').length).toBeGreaterThan(0);
    // карандаши правки полей (reliability_grade + kunya/laqab/tabaqa)
    expect(
      screen.getAllByRole('button', { name: 'Исправить администратором' }).length,
    ).toBeGreaterThan(0);
  });

  it('ADMIN правит reliability_grade → PUT с правильным fieldName → onEdited', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.put(PUT_URL, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'o1', value: String(body.value) });
      }),
    );
    const onEdited = vi.fn();
    render(<NarratorPanel data={DATA} onClose={() => {}} role="ADMIN" onEdited={onEdited} />);

    // Первый карандаш — над reliability_grade (enum, select).
    await userEvent.click(
      screen.getAllByRole('button', { name: 'Исправить администратором' })[0]!,
    );
    await userEvent.selectOptions(screen.getByRole('combobox'), 'DAIF');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      expect(onEdited).toHaveBeenCalled();
    });
    expect(body).toEqual({
      entityTable: 'hd_narrators',
      entityId: 'n1',
      fieldName: 'reliability_grade',
      value: 'DAIF',
    });
  });
});
