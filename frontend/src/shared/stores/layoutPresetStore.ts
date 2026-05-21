import { create } from 'zustand';
import type { LayoutPreset } from '@/apps/argument-map/utils/elkLayout';

/**
 * Preset формы графа аргументации. Заменил собой LayoutAlgorithm
 * (`dagre`/`elk`) - пользователь больше не выбирает алгоритм, он
 * выбирает форму. Mapping preset → (algorithm, direction, config)
 * живёт в `elkLayout.ts`.
 *
 * - `tree-tb` (default) - канонический Sugiyama TOP→BOTTOM с
 *   type-constraints (QUESTION top, EVIDENCE bottom). Как Kialo,
 *   Rationale, Argdown.
 * - `tree-lr` - тот же layered, но LEFT→RIGHT. Для wide screens
 *   и длинных reasoning chains.
 * - `radial` - root в центре, концентрические кольца. Для 20+
 *   узлов и презентационных скриншотов.
 */
export type { LayoutPreset };

const STORAGE_KEY = 'argmap.layoutPreset';
const LEGACY_KEY = 'argmap.layoutAlgorithm';

const VALID_PRESETS: ReadonlySet<LayoutPreset> = new Set([
  'tree-tb',
  'tree-lr',
  'radial',
]);

function readPersisted(): LayoutPreset {
  if (typeof window === 'undefined') return 'tree-tb';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw && VALID_PRESETS.has(raw as LayoutPreset)) {
    return raw as LayoutPreset;
  }
  // Migration: старый LEGACY_KEY хранил 'dagre' | 'elk'. Маппим оба
  // на 'tree-tb' (новый default), очищаем legacy ключ
  if (typeof window !== 'undefined') {
    const legacy = window.localStorage.getItem(LEGACY_KEY);
    if (legacy) {
      window.localStorage.removeItem(LEGACY_KEY);
      window.localStorage.setItem(STORAGE_KEY, 'tree-tb');
    }
  }
  return 'tree-tb';
}

interface LayoutPresetState {
  preset: LayoutPreset;
  setPreset: (p: LayoutPreset) => void;
}

/**
 * Persist в localStorage под `argmap.layoutPreset`. Default `tree-tb` -
 * наиболее universal для argument map (sugiyama сверху вниз).
 */
export const useLayoutPresetStore = create<LayoutPresetState>((set) => ({
  preset: readPersisted(),
  setPreset: (p) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, p);
    }
    set({ preset: p });
  },
}));
