import { create } from 'zustand';

/**
 * Алгоритм автоматической раскладки графа аргументации.
 *
 * - `dagre` (default) - sync, лёгкий, отрабатывает мгновенно. Хорош
 *   для небольших и средних графов с прямой иерархией
 * - `elk` - async, ~200KB bundle, lazy import. Лучше для сложных
 *   графов с many edges - `ORTHOGONAL` routing разводит рёбра вокруг
 *   узлов и уменьшает crossings
 */
export type LayoutAlgorithm = 'dagre' | 'elk';

const STORAGE_KEY = 'argmap.layoutAlgorithm';

function readPersisted(): LayoutAlgorithm {
  if (typeof window === 'undefined') return 'dagre';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  return raw === 'elk' ? 'elk' : 'dagre';
}

interface LayoutAlgorithmState {
  algorithm: LayoutAlgorithm;
  setAlgorithm: (a: LayoutAlgorithm) => void;
}

/**
 * Persist в localStorage под `argmap.layoutAlgorithm`. Default `dagre`
 * чтобы новые/первые пользователи не получали неожиданное async-поведение
 */
export const useLayoutAlgorithmStore = create<LayoutAlgorithmState>((set) => ({
  algorithm: readPersisted(),
  setAlgorithm: (a) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, a);
    }
    set({ algorithm: a });
  },
}));
