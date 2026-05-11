import { waitFor } from '@testing-library/react';

/**
 * waitFor с явным timeout для async-операций которые ждут API-моков
 * (MSW). Моки синхронные, поэтому 200ms - достаточно с запасом и
 * быстро при флэйке.
 *
 * Решает T-04 из cleanup audit'а: дефолтный waitFor (1000ms) был
 * слишком лениент - flaky tests, слишком медленный suite. Хороший
 * timeout = explicit signal "сколько максимум стоит ждать".
 */
export const waitForApi = (fn: () => void | Promise<void>) => waitFor(fn, { timeout: 200 });

/**
 * Для UI-изменений (transitions, animations) которые могут чуть
 * затянуться - даём 500ms. Используется реже чем waitForApi.
 */
export const waitForUi = (fn: () => void | Promise<void>) => waitFor(fn, { timeout: 500 });
