import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { server } from '@/test/server';
import { invalidateCache } from '@/shared/hooks/queryCache';

// React Flow (@xyflow/system) использует d3-drag для перетаскивания узлов.
// При mousedown d3-drag вызывает nodrag(event.view) -> view.document, но в
// jsdom @testing-library/user-event создаёт MouseEvent с view === null
// (initUIEvent → assignProps возвращает null для отсутствующего view).
// Итог: uncaught TypeError "Cannot read properties of null (reading
// 'document')" в фоновом event-handler'е во ВСЕХ graph-тестах, кликающих по
// .react-flow__node (bulkActions.test.tsx и др.). Тесты при этом проходят
// (drag нам не нужен — проверяем bulk-действия, не перетаскивание), но
// vitest считает uncaught-исключения и order-зависимо помечает суите как
// failed ("Errors N").
//
// Fix (тест-only): мокаем d3-drag no-op'ом. drag() остаётся chainable
// builder'ом (.on/.filter/.clickDistance/... → возвращают себя), но при
// selection.call(instance) НЕ навешивает mousedown.drag listener'ы — значит
// nodrag() никогда не вызывается. Поведение узлов/selection в RF не зависит
// от d3-drag (RF сам обрабатывает pointer-события для выделения), только
// drag-to-reposition отключается — в jsdom он всё равно не работает.
// dragDisable/dragEnable (nodrag/yesdrag) тоже no-op на случай прямого вызова.
// См. docs/gotchas.md «d3-drag + jsdom: event.view === null».
vi.mock('d3-drag', () => {
  function drag() {
    // Chainable builder: selection-decorator, который ничего не навешивает.
    const instance = (() => instance) as unknown as Record<string, unknown> & (() => unknown);
    const chainable = () => instance;
    for (const method of [
      'on',
      'filter',
      'container',
      'subject',
      'touchable',
      'clickDistance',
    ]) {
      instance[method] = chainable;
    }
    return instance;
  }
  return {
    drag,
    dragDisable: () => {},
    dragEnable: () => {},
  };
});

vi.stubEnv('VITE_API_URL', 'http://test.local');
vi.stubEnv('VITE_DEV_USER_ID', '00000000-0000-0000-0000-000000000001');

// React Flow требует ResizeObserver и DOMMatrix - jsdom их не предоставляет
class ResizeObserverMock {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverMock);

// jsdom не реализует window.matchMedia. useIsMobile (shared/hooks/useViewport)
// и любые компоненты использующие его (Modal, NodeDetailsPanel) фейлятся
// в тестах без polyfill. По умолчанию - desktop viewport (matches=false для
// max-width media query). Тесты которым нужно эмулировать mobile -
// переопределяют через свой beforeEach с custom factory
if (typeof window !== 'undefined' && !window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string): MediaQueryList =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList,
  });
}

// HTMLDialogElement.showModal / .close в jsdom (≤24) не реализованы.
// Polyfill через open attribute - достаточно для Modal smoke-tests
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
}

// Node 24 + undici 7 регрессия: undici (встроенная в Node 24 как
// node:internal/deps/undici/undici) валидирует `signal instanceof AbortSignal`
// против СВОЕГО internal prototype, который недоступен из user-space - ни через
// globalThis.AbortSignal, ни через jsdom.window.AbortSignal, ни через native
// Node global. Любой user-created AbortSignal проваливает instanceof и fetch
// кидает TypeError "RequestInit: Expected signal to be an instance of
// AbortSignal" ещё на этапе Request construction.
//
// См. github.com/nodejs/undici/issues/2596 и nodejs/node/issues/56644.
//
// До этого фикса: `apiGet(path, { signal: controller.signal })` в компонентах
// падал в catch с этим TypeError, все 12 async UI-тестов (TopicListPage 3 +
// TopicGraphPage 4 + NodeDetailsPanel 5) видели error state вместо ожидаемого
// success/empty.
//
// Fix (тест-only): monkey-patch globalThis.fetch чтобы strip signal из
// RequestInit перед вызовом - в тестах abort-логика не нужна, MSW handlers
// синхронные. В prod fetch работает нормально (там сам browser fetch без
// undici validation).
// Применяется в beforeAll - после того как msw setupServer() установит свой
// interceptor на globalThis.fetch (это происходит в server.listen()). Наша
// обёртка идёт ПОСЛЕ msw, поэтому msw interceptor отрабатывает первым,
// получает strip-signal request, и handler matching работает корректно.
function wrapFetchStripSignal(): void {
  const current = globalThis.fetch;
  globalThis.fetch = function wrappedFetch(
    this: unknown,
    input: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> {
    if (init?.signal) {
      const { signal: _signal, ...rest } = init;
      return current.call(this, input, rest);
    }
    return current.call(this, input, init);
  } as typeof fetch;
}

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  wrapFetchStripSignal();
});
afterEach(() => {
  server.resetHandlers();
  // SWR-кэш (shared/hooks/queryCache) module-scoped — утекает между
  // тестами иначе: один тест закэширует path, следующий по тому же path
  // получит stale-hit вместо чистого loading/error. Чистим после каждого.
  invalidateCache();
});
afterAll(() => server.close());
