/**
 * Discriminated union для async-state в компонентах. Заменяет
 * дублирование `LoadState`/`ViewState`/etc типов в 8+ компонентах
 * (F-10 audit). Discriminator `kind` совпадает с устоявшейся конвенцией
 * проекта.
 *
 * Использование:
 * ```ts
 * const [state, setState] = useState<AsyncState<Topic[]>>({ kind: 'loading' });
 * if (state.kind === 'success') console.log(state.data);
 * ```
 *
 * Компонент с дополнительными полями в success-state может использовать
 * свой union вместо AsyncState (например NodeCitationsSection нуждается
 * в lookup maps - там собственный SourcesState).
 */
export type AsyncState<T, E = string> =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; data: T }
  | { kind: 'error'; message: E };
