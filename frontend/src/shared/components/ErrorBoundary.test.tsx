import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import ErrorBoundary from './ErrorBoundary';

function Boom({ throwError }: { throwError: boolean }) {
  if (throwError) throw new Error('test boom');
  return <div>ok</div>;
}

describe('ErrorBoundary', () => {
  let consoleError: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    // подавить React error logs в выхлопе теста (ожидаемое поведение)
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    consoleError.mockRestore();
  });

  it('рендерит детей если ошибки нет', () => {
    render(
      <ErrorBoundary>
        <Boom throwError={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('ok')).toBeInTheDocument();
  });

  it('показывает fallback с сообщением при render-ошибке', () => {
    render(
      <ErrorBoundary>
        <Boom throwError={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Что-то пошло не так/)).toBeInTheDocument();
    expect(screen.getByText(/test boom/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Перезагрузить/ })).toBeInTheDocument();
  });

  it('использует custom fallback если передан', () => {
    render(
      <ErrorBoundary fallback={<div>my fallback</div>}>
        <Boom throwError={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('my fallback')).toBeInTheDocument();
  });

  it('кнопка Перезагрузить вызывает window.location.reload', async () => {
    const reload = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, reload },
    });
    render(
      <ErrorBoundary>
        <Boom throwError={true} />
      </ErrorBoundary>,
    );
    await userEvent.click(screen.getByRole('button', { name: /Перезагрузить/ }));
    expect(reload).toHaveBeenCalledOnce();
  });
});
