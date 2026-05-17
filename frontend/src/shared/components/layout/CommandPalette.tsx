import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Book,
  Network,
  Plus,
  Search,
  Settings,
  Sparkles,
  Sun,
  Moon,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { useThemeStore } from '@/shared/stores/themeStore';
import Kbd from '@/shared/components/ui/Kbd';
import { useHotkey } from '@/shared/hooks/useHotkey';

interface Props {
  open: boolean;
  onClose: () => void;
}

interface Command {
  id: string;
  label: string;
  hint?: string;
  Icon: LucideIcon;
  run: () => void;
}

/**
 * Alt+K Command Palette. Глобальный поиск + быстрые навигационные команды.
 *
 * Реализация - "container/body split": body монтируется только при open=true.
 * Это идиома проекта (см. memory feedback_react_key_remount) - сброс query/
 * activeIdx происходит естественно через remount, без useEffect set-state
 * (eslint react-hooks/set-state-in-effect).
 *
 * Версия 1 - статические команды. Когда появится backend search для тем/книг -
 * дополнить динамической секцией результатов.
 */
function CommandPalette({ open, onClose }: Props) {
  if (!open) return null;
  return <CommandPaletteBody onClose={onClose} />;
}

function CommandPaletteBody({ onClose }: { onClose: () => void }) {
  const t = useT();
  const navigate = useNavigate();
  const toggleTheme = useThemeStore((s) => s.toggle);
  const theme = useThemeStore((s) => s.theme);
  const [query, setQuery] = useState('');
  const [activeIdx, setActiveIdx] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const allCommands: Command[] = [
    {
      id: 'goto-topics',
      label: t('palette.goto_topics'),
      hint: '/topics',
      Icon: Network,
      run: () => navigate('/topics'),
    },
    {
      id: 'goto-new-topic',
      label: t('palette.new_topic'),
      hint: '/topics/new',
      Icon: Plus,
      run: () => navigate('/topics/new'),
    },
    {
      id: 'goto-books',
      label: t('palette.goto_books'),
      hint: '/books',
      Icon: Book,
      run: () => navigate('/books'),
    },
    {
      id: 'goto-admin',
      label: t('palette.goto_admin'),
      hint: '/admin/shamela',
      Icon: Settings,
      run: () => navigate('/admin/shamela'),
    },
    {
      id: 'toggle-theme',
      label: theme === 'dark' ? t('palette.theme_light') : t('palette.theme_dark'),
      hint: t('palette.theme_hint'),
      Icon: theme === 'dark' ? Sun : Moon,
      run: toggleTheme,
    },
  ];

  const q = query.trim().toLowerCase();
  const commands = q
    ? allCommands.filter((c) => c.label.toLowerCase().includes(q))
    : allCommands;

  // Фокусируем input сразу при mount. requestAnimationFrame чтобы дать
  // диалогу попасть в DOM - без него focus() может потеряться
  useEffect(() => {
    const id = requestAnimationFrame(() => inputRef.current?.focus());
    return () => cancelAnimationFrame(id);
  }, []);

  // Hotkeys работают пока активен palette - фокус всегда в search input,
  // поэтому enableOnFormTags=true обязательно.
  useHotkey('escape', onClose, { enableOnFormTags: true });
  useHotkey(
    'arrowdown',
    () => setActiveIdx((i) => Math.min(commands.length - 1, i + 1)),
    { enableOnFormTags: true },
    [commands.length],
  );
  useHotkey(
    'arrowup',
    () => setActiveIdx((i) => Math.max(0, i - 1)),
    { enableOnFormTags: true },
  );
  useHotkey(
    'enter',
    () => {
      const cmd = commands[activeIdx];
      if (cmd) {
        cmd.run();
        onClose();
      }
    },
    { enableOnFormTags: true },
    [commands, activeIdx, onClose],
  );

  // При смене query - активный пункт всегда первый. Не setState из эффекта:
  // фильтрация и activeIdx считаются через derived activeIdx = Math.min(...)
  const safeActiveIdx = Math.min(activeIdx, Math.max(commands.length - 1, 0));

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t('palette.aria')}
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 px-4 pt-[15vh] backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl overflow-hidden rounded-lg border border-border bg-elevated shadow-sh4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 border-b border-border px-4">
          <Search size={16} className="text-ink-400" aria-hidden />
          <input
            ref={inputRef}
            type="search"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActiveIdx(0);
            }}
            placeholder={t('palette.placeholder')}
            className="h-12 flex-1 bg-transparent text-sm text-ink-900 outline-none placeholder:text-ink-400"
            aria-label={t('palette.aria')}
          />
          <Kbd>Esc</Kbd>
        </div>
        <ul role="listbox" className="max-h-80 overflow-y-auto py-1">
          {commands.length === 0 && (
            <li className="px-4 py-6 text-center text-sm text-ink-500">
              {t('palette.empty')}
            </li>
          )}
          {commands.map((cmd, i) => {
            const active = i === safeActiveIdx;
            return (
              <li key={cmd.id} role="option" aria-selected={active}>
                <button
                  type="button"
                  onMouseEnter={() => setActiveIdx(i)}
                  onClick={() => {
                    cmd.run();
                    onClose();
                  }}
                  className={`flex w-full items-center gap-3 px-4 py-2 text-start text-sm transition-colors ${
                    active ? 'bg-accent-50 text-accent-700' : 'text-ink-800'
                  }`}
                >
                  <cmd.Icon
                    size={15}
                    aria-hidden
                    className={active ? 'text-accent-600' : 'text-ink-500'}
                  />
                  <span className="flex-1">{cmd.label}</span>
                  {cmd.hint && (
                    <span className="font-mono text-xs text-ink-400">{cmd.hint}</span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
        <div className="flex items-center justify-between border-t border-border bg-bg px-4 py-2 text-xs text-ink-500">
          <span className="inline-flex items-center gap-2">
            <Kbd>↑</Kbd>
            <Kbd>↓</Kbd>
            {t('palette.nav_hint')}
          </span>
          <span className="inline-flex items-center gap-2">
            <Sparkles size={11} aria-hidden />
            <Kbd>↵</Kbd>
            {t('palette.run_hint')}
          </span>
        </div>
      </div>
    </div>
  );
}

export default CommandPalette;
