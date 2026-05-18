import { Component, type ErrorInfo, type ReactNode } from 'react';

/**
 * Top-level error boundary. Ловит runtime errors в descendant'ах и
 * вместо blank screen показывает fallback UI с reload-кнопкой.
 *
 * Покрывает:
 *  - layout-ошибки React Flow / dagre / ELK на TopicGraphPage
 *  - tiptap extension errors на AdminPageEditorPage
 *  - react-pdf failures на BookReaderPage
 *  - любые непредвиденные render-исключения по дереву
 *
 * React 19 не имеет встроенного fallback - класс-компонент с
 * `getDerivedStateFromError` + `componentDidCatch` остаётся
 * единственным санкционированным способом.
 *
 * Logging пока console.error - в проде сюда можно подключить Sentry /
 * любой error reporter без изменения public API.
 */

interface Props {
  children: ReactNode;
  /** Опциональный custom fallback. По умолчанию - generic reload UI */
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  message: string | null;
}

class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message || 'Неизвестная ошибка' };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary] caught', error, info.componentStack);
  }

  handleReload = (): void => {
    if (typeof window !== 'undefined') window.location.reload();
  };

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    return (
      <div
        role="alert"
        className="flex min-h-screen items-center justify-center bg-canvas px-4"
      >
        <div className="max-w-md rounded-lg border border-ink-200 bg-surface p-6 shadow-sm">
          <h1 className="mb-2 text-xl font-semibold text-ink-900">
            Что-то пошло не так
          </h1>
          <p className="mb-4 text-sm text-ink-600">
            Произошла непредвиденная ошибка при отрисовке страницы.
            Попробуйте перезагрузить - если проблема повторяется,
            сообщите нам.
          </p>
          {this.state.message && (
            <pre className="mb-4 overflow-x-auto rounded bg-ink-50 p-2 text-xs text-ink-700">
              {this.state.message}
            </pre>
          )}
          <button
            type="button"
            onClick={this.handleReload}
            className="inline-flex items-center rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
          >
            Перезагрузить
          </button>
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
