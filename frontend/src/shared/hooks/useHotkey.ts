import { useHotkeys, type HotkeyCallback, type Options } from 'react-hotkeys-hook';

/**
 * Стандартные опции по умолчанию для `useHotkey`:
 *
 * - `preventDefault: true` - чтобы браузерные accelerators не вмешивались
 * - `enableOnFormTags: false` - hotkeys не срабатывают пока фокус в
 *   input/textarea/select. Это default-поведение `react-hotkeys-hook` -
 *   указано явно для документации
 * - `useKey: true` - матчить по физической клавише (`event.code`,
 *   например `KeyK`) а не по символу (`event.key`). Решает баг #2
 *   когда Alt+K не срабатывал на русской раскладке: keyboard layout
 *   меняет `event.key` но `event.code` остаётся `KeyK` для одной и
 *   той же физической клавиши
 *
 * Все опции можно перекрыть через `opts` аргумент. См. документацию
 * `react-hotkeys-hook`: <https://react-hotkeys-hook.vercel.app/docs/api/use-hotkeys>
 */
const DEFAULT_OPTIONS: Options = {
  preventDefault: true,
  enableOnFormTags: false,
  useKey: true,
};

export type HotkeyOptions = Options;
export type { HotkeyCallback };

/**
 * Wrapper над `useHotkeys` из `react-hotkeys-hook`. Единая точка регистрации
 * keyboard shortcuts во фронте - см. `frontend/CLAUDE.md` секцию «Hotkeys» +
 * ADR-036 (decisions.md).
 *
 * Преимущества над `addEventListener('keydown')`:
 * - layout-independent (`useKey: true` использует `event.code` для буквенных)
 * - platform-aware модификатор `mod` (⌘ на Mac / Ctrl на Win/Linux) -
 *   стандартный способ кросс-платформенного submit-сочетания
 * - `meta` всегда строго ⌘ Mac / Win клавиша Linux (если нужна именно она)
 * - scopes для контекстного включения (`'graph'`, `'modal'`)
 * - не срабатывает в формах (input/textarea) по умолчанию
 *
 * @example Закрыть модалку по Escape:
 * ```ts
 * useHotkey('escape', onClose, { scope: 'modal', enableOnFormTags: true });
 * ```
 *
 * @example Submit формы по ⌘+↵ / Ctrl+↵ (работает в input):
 * ```ts
 * useHotkey('mod+enter', handleSubmit, { enableOnFormTags: true });
 * ```
 *
 * @example Открыть Command Palette по Alt+K (работает на любой раскладке):
 * ```ts
 * useHotkey('alt+k', togglePalette);
 * ```
 *
 * @param keys - комбинация клавиш в формате `react-hotkeys-hook`. Список через
 *   запятую - любая из комбинаций срабатывает. См. примеры выше
 * @param callback - обработчик. Получает `KeyboardEvent` (preventDefault уже
 *   вызван если опция не перекрыта)
 * @param opts - опции `react-hotkeys-hook`. Override `DEFAULT_OPTIONS`
 * @param deps - dependency array для callback (для useHotkeys второй
 *   "deps" аргумент). Если не передан - библиотека сама определяет
 *   через identity callback'а
 */
export function useHotkey(
  keys: string,
  callback: HotkeyCallback,
  opts?: HotkeyOptions,
  deps?: ReadonlyArray<unknown>,
) {
  return useHotkeys(keys, callback, { ...DEFAULT_OPTIONS, ...opts }, deps);
}
