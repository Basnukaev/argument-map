import Kbd from '@/shared/components/ui/Kbd';

interface Props {
  /**
   * Комбинация клавиш в формате `react-hotkeys-hook`:
   * `'meta+enter'`, `'alt+k'`, `'escape'`. Можно перечислять одну
   * комбинацию (для кросс-платформенного отображения логичнее писать
   * семантический `'meta+enter'` - на не-Mac платформах он сам
   * отрендерится как `Ctrl+↵`)
   *
   * Несколько комбинаций через запятую (`'meta+enter,ctrl+enter'`)
   * не имеют смысла для отображения - бери первую и доверяй platform
   * detection в `ShortcutHint`
   */
  keys: string;
  /** Опциональный класс - для контейнера */
  className?: string;
}

const MAC_GLYPHS: Record<string, string> = {
  mod: '⌘',
  meta: '⌘',
  cmd: '⌘',
  alt: '⌥',
  option: '⌥',
  shift: '⇧',
  ctrl: '⌃',
  control: '⌃',
  enter: '↵',
  return: '↵',
  escape: 'Esc',
  esc: 'Esc',
  backspace: '⌫',
  delete: 'Del',
  del: 'Del',
  tab: '⇥',
  space: '␣',
  up: '↑',
  down: '↓',
  left: '←',
  right: '→',
};

const WIN_LABELS: Record<string, string> = {
  mod: 'Ctrl',
  meta: 'Win',
  cmd: 'Ctrl',
  alt: 'Alt',
  option: 'Alt',
  shift: 'Shift',
  ctrl: 'Ctrl',
  control: 'Ctrl',
  enter: '↵',
  return: '↵',
  escape: 'Esc',
  esc: 'Esc',
  backspace: '⌫',
  delete: 'Del',
  del: 'Del',
  tab: '⇥',
  space: '␣',
  up: '↑',
  down: '↓',
  left: '←',
  right: '→',
};

function isMacPlatform(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform);
}

function formatKey(rawKey: string, mac: boolean): string {
  const key = rawKey.trim().toLowerCase();
  const map = mac ? MAC_GLYPHS : WIN_LABELS;
  if (map[key]) return map[key];
  // буквенные клавиши: показать заглавной
  if (key.length === 1) return key.toUpperCase();
  // f-keys, цифры, прочее - как пришло
  return key.charAt(0).toUpperCase() + key.slice(1);
}

/**
 * Отображение hotkey combination как набора `<Kbd>` элементов с
 * platform-aware glyph'ами. На Mac (`navigator.platform` содержит
 * `Mac`) `meta` рендерится как `⌘`, на Win/Linux - как `Ctrl`. То же
 * для `alt`/`option`/`shift`
 *
 * @example В footer'е формы:
 * ```tsx
 * <ShortcutHint keys="meta+enter" />  // Mac: ⌘ ↵, Win/Linux: Ctrl ↵
 * ```
 *
 * @example В тултипе кнопки поиска:
 * ```tsx
 * <ShortcutHint keys="alt+k" />  // Mac: ⌥ K, Win/Linux: Alt K
 * ```
 */
function ShortcutHint({ keys, className }: Props) {
  const mac = isMacPlatform();
  // берём первую комбинацию (`'meta+enter,ctrl+enter'` → `'meta+enter'`)
  const primary = keys.split(',')[0] ?? '';
  const parts = primary.split('+');
  return (
    <span className={`inline-flex items-center gap-1 ${className ?? ''}`}>
      {parts.map((p, i) => (
        <Kbd key={`${p}-${i}`}>{formatKey(p, mac)}</Kbd>
      ))}
    </span>
  );
}

export default ShortcutHint;
