import { create } from 'zustand';

/**
 * Минимальный контракт для открытия SourceDetailPanel из любого места:
 * NodeDetailsPanel (SourceCard click), NodeCard inline citation marker,
 * BookReaderPage и т.д.
 *
 * `sourceId` обязателен (нужен для GET /api/v1/sources/{id} - полная
 * метадата автора/книги). `quote` и `context` опциональны - если caller
 * уже знает их (например NodeSourceResponse), панель показывает сразу
 * без второго round-trip
 */
export interface SourceDetailCitation {
  sourceId: string;
  /** ID связи (`node_source.id` / `question_source.id`) - reserved для
   *  будущего «Detach» action прямо из панели. MVP не использует */
  nodeSourceId?: string;
  /** Quote из node_source / question_source. Если нет - панель покажет
   *  только metadata + кнопку «Открыть полностью» */
  quote?: string;
  /** Context вокруг цитаты (node_source.context) */
  context?: string;
}

interface SourceDetailPanelState {
  /** Текущий открытый источник. `null` ⇒ панель закрыта */
  current: SourceDetailCitation | null;
  /** Computed: open = current !== null. Дублирующее поле для удобства
   *  селекторов / тестов */
  isOpen: boolean;
  /** Открыть панель с конкретным источником. Если уже открыта - меняет
   *  current (re-fetch внутри компонента) */
  openWith: (citation: SourceDetailCitation) => void;
  /** Закрыть панель. current сбрасывается в null чтобы предотвратить
   *  flash старого контента при следующем открытии */
  close: () => void;
}

/**
 * Zustand store для управления `SourceDetailPanel` глобально - множественные
 * компоненты (SourceCard, InlineCitationMarker, BookReader citations) открывают
 * одну mount'нутую панель в App.tsx. Без store пришлось бы prop-drilling'ить
 * setOpen handler через всё дерево или мониторить через ref
 *
 * Не persist'ится - открытое состояние не нужно сохранять между сессиями
 */
export const useSourceDetailPanelStore = create<SourceDetailPanelState>((set) => ({
  current: null,
  isOpen: false,
  openWith: (citation) => set({ current: citation, isOpen: true }),
  close: () => set({ current: null, isOpen: false }),
}));
