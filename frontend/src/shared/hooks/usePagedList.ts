import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import type { AsyncState } from '@/shared/types/async';
import type { Paged } from './usePagedSearch';
import { getCached, setCached } from './queryCache';

interface Options {
  /**
   * Строит URL для запроса страницы `page` с учётом debounced-query.
   * `page` здесь — **0-based** (бэк-база, Spring Pageable). UI-страница
   * (?page= в URL) 1-based, hook сам отнимает 1 перед вызовом buildUrl.
   * Доп. фильтры читаются из замыкания — их значения нужно передать в
   * `deps`, чтобы сброс на стр.1 + refetch случился.
   */
  buildUrl: (page: number, debouncedQuery: string) => string;
  /** Задержка debounce поиска (мс). По умолчанию 300. */
  debounceMs?: number;
  /**
   * Доп. фильтры (statusFilter / grade и т.п.), смена которых должна
   * сбросить на стр.1 и перезапросить. Аналог dependency-array —
   * сравнивается по identity каждого элемента.
   */
  deps?: ReadonlyArray<unknown>;
  /** Сообщение об ошибке если пойманное исключение не Error-like. */
  fallbackError?: string;
}

interface Result<TItem> {
  state: AsyncState<Paged<TItem>>;
  searchInput: string;
  setSearchInput: (v: string) => void;
  /** Текущая страница, **1-based** (как в URL `?page=`). */
  page: number;
  /** Перейти на страницу `n` (1-based). Пишет `?page=n` в URL (replace). */
  goToPage: (n: number) => void;
}

/** Парсит `?page=` (1-based) в положительное целое; невалидное → 1. */
function parsePageParam(raw: string | null): number {
  if (raw === null) return 1;
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) && n >= 1 ? n : 1;
}

/**
 * Нумерованная пагинация + debounced-search для list-страниц (C20).
 * Семантика **REPLACE** (в отличие от Load-More `usePagedSearch`,
 * который аппендит): переход на страницу заменяет items, а номер
 * страницы синхронизирован с URL `?page=` (1-based, deep-linkable —
 * шарится / переживает перезагрузку).
 *
 * Что делает hook (data-fetching only, без JSX):
 * - debounce ввода: `searchInput` → debounced query через `debounceMs`;
 * - смена debounced-query / любого из `deps` → сброс на стр.1 (`?page=`
 *   убирается из URL) + запрос первой страницы;
 * - `goToPage(n)` пишет `?page=n` в URL (replace, без скролл-прыжка
 *   истории) и грузит ту страницу (REPLACE, не append);
 * - init страницы из `?page=` на mount (deep-link).
 *
 * Бэк-база 0-based (Spring Pageable, `page=0` = первая). UI-страница
 * 1-based — hook маппит `buildUrl(uiPage - 1, query)`.
 *
 * SWR (stale-while-revalidate): каждая страница кэшируется в
 * `queryCache` под своим ключом (`buildUrl(uiPage - 1, query)` — ключ
 * инкапсулирует query И все фильтры, т.к. buildUrl замыкается на те же
 * значения что и deps). Возврат на уже посещённую страницу показывает
 * её МГНОВЕННО (без спиннера), затем ревалидируется в фоне. Если кэша
 * для целевой страницы нет — показываем loading (REPLACE-семантика: items
 * другой страницы вводили бы в заблуждение).
 *
 * Стейл-гард: каждый запрос помнит «поколение» (bump'ается на смену
 * query/deps) И целевую страницу на момент issue; ответ применяется
 * только если оба совпадают с текущими — иначе игнорируется (ответ
 * устаревшей страницы/query не затирает свежий state).
 */
export function usePagedList<TItem>(options: Options): Result<TItem> {
  const {
    buildUrl,
    debounceMs = 300,
    deps = [],
    fallbackError = 'Не удалось загрузить',
  } = options;

  const [searchParams, setSearchParams] = useSearchParams();
  // Страница (1-based) — source of truth в URL. Локальное зеркало для
  // рендера + чтобы effect реагировал на смену param (deep-link / back/forward).
  const page = parsePageParam(searchParams.get('page'));

  // Lazy init из SWR-кэша: ключ — целевая страница (page-1 в бэк-базе) с
  // пустым query (debouncedQuery стартует ''). Есть кэш → мгновенный success.
  const [state, setState] = useState<AsyncState<Paged<TItem>>>(() => {
    const cached = getCached<Paged<TItem>>(buildUrl(page - 1, ''));
    return cached !== undefined
      ? { kind: 'success', data: cached.data }
      : { kind: 'loading' };
  });
  const [searchInput, setSearchInput] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');

  // generationRef bump'ается при смене query/deps — фиксирует «эпоху»
  // фильтров. Stale-guard в fetch-effect сверяет его + целевую страницу.
  const generationRef = useRef(0);
  // Зеркала для goToPage (event handler) — читает без замыкания на рендер.
  const setSearchParamsRef = useRef(setSearchParams);
  useEffect(() => {
    setSearchParamsRef.current = setSearchParams;
  });

  // Debounce: после debounceMs простоя sync'аем searchInput → debouncedQuery.
  useEffect(() => {
    const handle = setTimeout(() => {
      setDebouncedQuery(searchInput.trim());
    }, debounceMs);
    return () => clearTimeout(handle);
  }, [searchInput, debounceMs]);

  // Смена debounced-query / фильтров (deps) → сброс на стр.1 (убираем
  // `?page=` из URL, replace). НО на mount сбрасывать нельзя — иначе
  // теряется deep-linked `?page=N`. Сравниваем со СНИМКОМ предыдущих
  // query/deps вместо «скип первого прогона» ref: последнее ломается
  // StrictMode-double-invoke (dev монтирует эффект дважды — на 2-м прогоне
  // ref уже false → ложный сброс страницы, ?page= терялся). prev-снимок
  // идемпотентен: первый прогон лишь запоминает значения (без сброса),
  // повторный (StrictMode) видит те же значения → тоже не сбрасывает.
  // Сброс — только при РЕАЛЬНОЙ смене значения (!Object.is).
  const prevQueryDepsRef = useRef<readonly unknown[] | null>(null);
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    const current = [debouncedQuery, ...deps];
    const prev = prevQueryDepsRef.current;
    prevQueryDepsRef.current = current;
    // Mount: запомнили снимок, deep-linked ?page= сохраняем.
    if (prev === null) return;
    const unchanged =
      prev.length === current.length &&
      prev.every((v, i) => Object.is(v, current[i]));
    if (unchanged) return;
    // Реальная смена query/фильтров = новая эпоха выдачи: сброс на стр.1.
    setSearchParamsRef.current(
      (p) => {
        const next = new URLSearchParams(p);
        next.delete('page');
        return next;
      },
      { replace: true },
    );
  }, [debouncedQuery, ...deps]);
  /* eslint-enable react-hooks/exhaustive-deps */

  // Запрос текущей страницы при смене page / debounced-query / deps.
  // bump'аем generation тут (а не в query-effect), чтобы stale-guard
  // покрывал и смену страницы, и смену фильтров единообразно.
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    generationRef.current += 1;
    const issuedGeneration = generationRef.current;
    const issuedPage = page;
    const backendPage = page - 1;
    const cacheKey = buildUrl(backendPage, debouncedQuery);

    const cached = getCached<Paged<TItem>>(cacheKey);
    if (cached !== undefined) {
      // SWR: мгновенно показываем закэшированную страницу (без flash-to-
      // spinner), затем ревалидируем в фоне.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setState({ kind: 'success', data: cached.data });
    } else {
      // Нет кэша для целевой страницы → loading. REPLACE-семантика:
      // показывать items другой страницы было бы дезориентирующе.
      setState({ kind: 'loading' });
    }

    const controller = new AbortController();
    apiGetRaw<Paged<TItem>>(cacheKey, { signal: controller.signal })
      .then((paged) => {
        if (controller.signal.aborted) return;
        // Stale-guard: эпоха фильтров или целевая страница сменились пока
        // запрос был in-flight — игнорируем (иначе stale-ответ затёр бы
        // свежий state).
        if (
          issuedGeneration !== generationRef.current ||
          issuedPage !== page
        ) {
          return;
        }
        setCached(cacheKey, paged);
        setState({ kind: 'success', data: paged });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        if (
          issuedGeneration !== generationRef.current ||
          issuedPage !== page
        ) {
          return;
        }
        // Не затираем валидный кэш error-экраном.
        if (getCached<Paged<TItem>>(cacheKey) !== undefined) return;
        setState({ kind: 'error', message: formatApiError(e, fallbackError) });
      });
    return () => controller.abort();
  }, [page, debouncedQuery, fallbackError, ...deps]);
  /* eslint-enable react-hooks/exhaustive-deps */

  /**
   * Перейти на страницу `n` (1-based). Стабильный callback (читает
   * setSearchParams из ref). Пишет `?page=n` (replace — без накопления
   * истории), стр.1 убирает param (чистый URL). Загрузку инициирует
   * fetch-effect выше (реагирует на смену `page`).
   */
  const goToPage = useCallback((n: number) => {
    setSearchParamsRef.current(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (n <= 1) {
          next.delete('page');
        } else {
          next.set('page', String(n));
        }
        return next;
      },
      { replace: true },
    );
  }, []);

  return { state, searchInput, setSearchInput, page, goToPage };
}
