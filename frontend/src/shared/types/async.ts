/**
 * Discriminated union для async-state в компонентах. Заменяет
 * дублирование `LoadState`/`ViewState` типов в 8+ компонентах
 * (F-10 audit).
 *
 * Использование:
 * ```ts
 * const [state, setState] = useState<AsyncState<Topic[]>>({ status: 'loading' });
 * if (state.status === 'success') { state.data... }
 * ```
 */
export type AsyncState<T, E = string> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: E };
