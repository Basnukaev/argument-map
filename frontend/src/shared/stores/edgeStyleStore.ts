import { create } from 'zustand';

/**
 * Стиль отрисовки рёбер на графе аргументации. Отдельный концепт от
 * preset формы (см. `layoutPresetStore`): preset решает где лежат
 * узлы, edgeStyle - какими линиями их соединять.
 *
 * - `orthogonal` (default для tree-presets) - Г-образные пути со
 *   скруглёнными углами по bend points из ELK. Match'ит exact
 *   routing layout-движка - технически точно, читается как
 *   блок-схема.
 * - `smooth` - bezier кривые между handles. Мягче визуально,
 *   меньше внимания к точному path'у движка. Канонично для
 *   радиальной раскладки и для пользователей предпочитающих
 *   органичный look.
 */
export type EdgeStyle = 'orthogonal' | 'smooth';

const STORAGE_KEY = 'argmap.edgeStyle';

function readPersisted(): EdgeStyle {
  if (typeof window === 'undefined') return 'orthogonal';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  return raw === 'smooth' ? 'smooth' : 'orthogonal';
}

interface EdgeStyleState {
  edgeStyle: EdgeStyle;
  setEdgeStyle: (s: EdgeStyle) => void;
}

export const useEdgeStyleStore = create<EdgeStyleState>((set) => ({
  edgeStyle: readPersisted(),
  setEdgeStyle: (s) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, s);
    }
    set({ edgeStyle: s });
  },
}));
