import { useCallback, useEffect, useRef, useState } from 'react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';

/**
 * Обёртка PagedResponse<T> с бэка (GET-list endpoints). Структурно
 * совпадает с `@/apps/hadith/types` Paged и inline PagedHadith —
 * hook живёт в shared/ и не зависит от apps/, поэтому объявлено здесь.
 */
export interface Paged<TItem> {
  items: TItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

interface Options {
  /**
   * Строит URL для запроса страницы `page` с учётом debounced-query.
   * Доп. фильтры (statusFilter / grade) читаются из замыкания — их
   * значения нужно передать в `deps`, чтобы refetch на page 0 случился.
   */
  buildUrl: (page: number, debouncedQuery: string) => string;
  /** Задержка debounce поиска (мс). По умолчанию 300. */
  debounceMs?: number;
  /**
   * Доп. фильтры (statusFilter / grade и т.п.), смена которых должна
   * перезапросить page 0. Аналог dependency-array — сравнивается
   * по identity каждого элемента.
   */
  deps?: ReadonlyArray<unknown>;
  /** Сообщение об ошибке если пойманное исключение не Error-like. */
  fallbackError?: string;
}

interface Result<TItem> {
  state: AsyncState<Paged<TItem>>;
  searchInput: string;
  setSearchInput: (v: string) => void;
  loadMore: () => void;
  loadingMore: boolean;
}

/**
 * Общая логика debounced-search + Load-More-пагинации для list-страниц
 * (HadithListPage, NarratorListPage, BookListPage были скопированы друг
 * с друга — этот hook убирает дрейф и централизует stale-append race fix).
 *
 * Что делает hook (data-fetching only, без JSX):
 * - debounce ввода: `searchInput` → debounced query через `debounceMs`;
 * - первая страница перезапрашивается при смене query / `deps`. НЕ
 *   сбрасываем в loading на refetch (старый список виден пока грузится
 *   новый — избегаем flash-to-spinner + react-hooks/set-state-in-effect);
 * - `loadMore` подгружает следующую страницу и аппендит к существующему
 *   списку. Stale-append race: если query/deps поменялись пока запрос
 *   page-N был in-flight, его ответ игнорируется (иначе stale page-N
 *   items приклеились бы к свежему page-0 списку).
 *
 * Страница владеет рендером инпута / <select> фильтра / карточек —
 * сюда переезжает только fetch/debounce/pagination state.
 */
export function usePagedSearch<TItem>(options: Options): Result<TItem> {
  const {
    buildUrl,
    debounceMs = 300,
    deps = [],
    fallbackError = 'Не удалось загрузить',
  } = options;

  const [state, setState] = useState<AsyncState<Paged<TItem>>>({ kind: 'loading' });
  // searchInput — то что печатает юзер; debouncedQuery — debounced
  // значение, которое реально триггерит запрос (без спама API).
  const [searchInput, setSearchInput] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [loadingMore, setLoadingMore] = useState(false);

  // Refs, которые читает loadMore (event handler) без замыкания на
  // конкретный рендер — callback стабилен (не пересоздаётся на каждый
  // success). react-hooks/refs запрещает мутировать ref в render-фазе,
  // поэтому синхронизируем зеркала в effect (после commit).
  // - generationRef: «поколение» активного запроса, bump'ается при
  //   каждом refetch page 0 (смена query / deps). loadMore запоминает
  //   поколение на момент issue и игнорирует свой ответ если поколение
  //   успело смениться → stale-append race fix.
  // - loadingMoreRef: sync-guard от двойного клика (state асинхронный).
  const generationRef = useRef(0);
  const buildUrlRef = useRef(buildUrl);
  const stateRef = useRef(state);
  const debouncedQueryRef = useRef(debouncedQuery);
  const loadingMoreRef = useRef(false);

  useEffect(() => {
    buildUrlRef.current = buildUrl;
    stateRef.current = state;
    debouncedQueryRef.current = debouncedQuery;
  });

  // Debounce: после debounceMs простоя sync'аем searchInput → debouncedQuery.
  useEffect(() => {
    const handle = setTimeout(() => {
      setDebouncedQuery(searchInput.trim());
    }, debounceMs);
    return () => clearTimeout(handle);
  }, [searchInput, debounceMs]);

  // Первая страница при смене debounced query / фильтров (deps).
  // НЕ сбрасываем в loading (flash-to-spinner + react-hooks
  // set-state-in-effect) — старый список виден пока грузится новый.
  // buildUrl читается напрямую (его identity завязана на те же фильтры
  // что и deps), refetch управляется debouncedQuery + deps. Spread deps
  // — динамический список фильтров, статически не верифицируется линтером.
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    generationRef.current += 1;
    const controller = new AbortController();
    apiGetRaw<Paged<TItem>>(buildUrl(0, debouncedQuery), {
      signal: controller.signal,
    })
      .then((paged) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'success', data: paged });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, fallbackError) });
      });
    return () => controller.abort();
  }, [debouncedQuery, fallbackError, ...deps]);
  /* eslint-enable react-hooks/exhaustive-deps */

  /**
   * Load More — подгружает следующую страницу, аппендит к existing list.
   * Стабильный callback (передаётся в onClick), читает state/query из ref.
   */
  const loadMore = useCallback(() => {
    const current = stateRef.current;
    if (current.kind !== 'success' || !current.data.hasNext) return;
    if (loadingMoreRef.current) return;
    // Запоминаем поколение на момент issue — ответ применяем только
    // если оно не сменилось (query/deps те же).
    const issuedGeneration = generationRef.current;
    const nextPage = current.data.page + 1;
    loadingMoreRef.current = true;
    setLoadingMore(true);
    apiGetRaw<Paged<TItem>>(buildUrlRef.current(nextPage, debouncedQueryRef.current))
      .then((resp) => {
        // Stale-append guard: query/deps поменялись пока запрос был
        // in-flight — игнорируем, иначе stale page-N приклеится к
        // свежему page-0 списку.
        if (issuedGeneration !== generationRef.current) return;
        setState((prev) =>
          prev.kind === 'success'
            ? {
                kind: 'success',
                data: { ...resp, items: [...prev.data.items, ...resp.items] },
              }
            : prev,
        );
      })
      .catch((e: unknown) => {
        if (issuedGeneration !== generationRef.current) return;
        setState({ kind: 'error', message: formatApiError(e, fallbackError) });
      })
      .finally(() => {
        loadingMoreRef.current = false;
        if (issuedGeneration === generationRef.current) setLoadingMore(false);
      });
  }, [fallbackError]);

  return { state, searchInput, setSearchInput, loadMore, loadingMore };
}
