import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Field from './Field';

describe('Field', () => {
  it('label связан с input через htmlFor / id (useId)', () => {
    const { container } = render(
      <Field label="Название">
        <Field.Input data-testid="input" />
      </Field>,
    );
    const input = screen.getByTestId('input');
    const label = container.querySelector('label');
    expect(label).not.toBeNull();
    expect(label).toHaveAttribute('for', input.id);
    expect(input.id).toBeTruthy();
  });

  it('required отображает звёздочку и проставляет aria-required', () => {
    render(
      <Field label="Title" required>
        <Field.Input data-testid="input" />
      </Field>,
    );
    expect(screen.getByText('*')).toBeInTheDocument();
    expect(screen.getByTestId('input')).toHaveAttribute('aria-required', 'true');
  });

  it('error отображает текст ошибки и проставляет aria-invalid на input', () => {
    render(
      <Field label="Title" error="Обязательное поле">
        <Field.Input data-testid="input" />
      </Field>,
    );
    expect(screen.getByText('Обязательное поле')).toBeInTheDocument();
    expect(screen.getByTestId('input')).toHaveAttribute('aria-invalid', 'true');
  });

  it('hint отображается под label', () => {
    render(
      <Field label="Title" hint="Краткая подсказка">
        <Field.Input data-testid="input" />
      </Field>,
    );
    expect(screen.getByText('Краткая подсказка')).toBeInTheDocument();
  });

  it('Field.Textarea получает aria-required и aria-invalid из контекста', () => {
    render(
      <Field label="Описание" required error="Ошибка">
        <Field.Textarea data-testid="ta" />
      </Field>,
    );
    const ta = screen.getByTestId('ta');
    expect(ta).toHaveAttribute('aria-required', 'true');
    expect(ta).toHaveAttribute('aria-invalid', 'true');
  });

  it('Field.Meta показывает left / right значения', () => {
    render(
      <Field label="Title">
        <Field.Input />
        <Field.Meta left="0 / 500" right="ok" />
      </Field>,
    );
    expect(screen.getByText('0 / 500')).toBeInTheDocument();
    expect(screen.getByText('ok')).toBeInTheDocument();
  });

  it('без required - aria-required отсутствует (не false)', () => {
    render(
      <Field label="Title">
        <Field.Input data-testid="input" />
      </Field>,
    );
    expect(screen.getByTestId('input')).not.toHaveAttribute('aria-required');
  });
});
