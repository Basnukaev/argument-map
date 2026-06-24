import { create } from 'zustand';

/**
 * Парирование шрифтов для всего UI. Каждая пара переопределяет
 * CSS-переменные --font-ui (sans / UI body) и --font-serif (serif /
 * заголовки и латинская prose). Арабский контент шрифтом пары НЕ
 * управляется - его задаёт отдельный контрол «Арабский шрифт» через
 * базовую переменную --font-ar (см. FontPairEffect).
 *
 * Все шрифты загружены upfront через @fontsource-variable импорты
 * в index.css, поэтому переключение пары в runtime - instant без FOUT.
 *
 * Имена 'X Variable' - конвенция fontsource для variable cuts.
 */
export interface FontPair {
  id: string;
  name: string;
  /** CSS value для --font-ui */
  ui: string;
  /** CSS value для --font-serif */
  serif: string;
  /** Описание характера пары - в UI tooltip */
  hint?: string;
}

const SYSTEM_STACK =
  "system-ui, -apple-system, 'Segoe UI', 'Roboto', sans-serif";
const SYSTEM_SERIF = "ui-serif, Georgia, 'Times New Roman', serif";

export const FONT_PAIRS: readonly FontPair[] = [
  {
    id: 'manrope-source-serif',
    name: 'Manrope + Source Serif',
    ui: "'Manrope Variable', system-ui, sans-serif",
    serif: "'Source Serif 4 Variable', Georgia, serif",
    hint: 'дефолт - geometric sans + редакционный serif',
  },
  {
    id: 'inter-lora',
    name: 'Inter + Lora',
    ui: "'Inter Variable', system-ui, sans-serif",
    serif: "'Lora Variable', Georgia, serif",
    hint: 'самая популярная пара - neutral sans + friendly serif',
  },
  {
    id: 'inter-source-serif',
    name: 'Inter + Source Serif',
    ui: "'Inter Variable', system-ui, sans-serif",
    serif: "'Source Serif 4 Variable', Georgia, serif",
    hint: 'classic editorial',
  },
  {
    id: 'inter-literata',
    name: 'Inter + Literata',
    ui: "'Inter Variable', system-ui, sans-serif",
    serif: "'Literata Variable', Georgia, serif",
    hint: 'Google Books reading - book-optimized serif с opsz',
  },
  {
    id: 'ibm-plex-source-serif',
    name: 'IBM Plex Sans + Source Serif',
    ui: "'IBM Plex Sans Variable', system-ui, sans-serif",
    serif: "'Source Serif 4 Variable', Georgia, serif",
    hint: 'corporate clean - технический документный feel',
  },
  {
    id: 'ibm-plex-literata',
    name: 'IBM Plex Sans + Literata',
    ui: "'IBM Plex Sans Variable', system-ui, sans-serif",
    serif: "'Literata Variable', Georgia, serif",
    hint: 'minimal tech + book serif',
  },
  {
    id: 'manrope-lora',
    name: 'Manrope + Lora',
    ui: "'Manrope Variable', system-ui, sans-serif",
    serif: "'Lora Variable', Georgia, serif",
  },
  {
    id: 'manrope-literata',
    name: 'Manrope + Literata',
    ui: "'Manrope Variable', system-ui, sans-serif",
    serif: "'Literata Variable', Georgia, serif",
    hint: 'soft sans + book serif',
  },
  {
    id: 'manrope-bitter',
    name: 'Manrope + Bitter',
    ui: "'Manrope Variable', system-ui, sans-serif",
    serif: "'Bitter Variable', Georgia, serif",
    hint: 'screen-optimized slab serif',
  },
  {
    id: 'inter-playfair',
    name: 'Inter + Playfair Display',
    ui: "'Inter Variable', system-ui, sans-serif",
    serif: "'Playfair Display Variable', Georgia, serif",
    hint: 'высокий контраст - magazine feel',
  },
  {
    id: 'manrope-only',
    name: 'Только Manrope',
    ui: "'Manrope Variable', system-ui, sans-serif",
    serif: "'Manrope Variable', system-ui, sans-serif",
    hint: 'UI и заголовки в одном sans - утилитарный минимализм',
  },
  {
    id: 'ibm-plex-only',
    name: 'Только IBM Plex Sans',
    ui: "'IBM Plex Sans Variable', system-ui, sans-serif",
    serif: "'IBM Plex Sans Variable', system-ui, sans-serif",
    hint: 'UI и заголовки в одном corporate sans',
  },
  {
    id: 'source-serif-only',
    name: 'Только Source Serif',
    ui: "'Source Serif 4 Variable', Georgia, serif",
    serif: "'Source Serif 4 Variable', Georgia, serif",
    hint: 'UI и заголовки в одном serif - literary feel',
  },
  {
    id: 'literata-only',
    name: 'Только Literata',
    ui: "'Literata Variable', Georgia, serif",
    serif: "'Literata Variable', Georgia, serif",
    hint: 'UI и заголовки в book-optimized serif',
  },
  {
    id: 'system',
    name: 'System (без загрузки)',
    ui: SYSTEM_STACK,
    serif: SYSTEM_SERIF,
    hint: 'нативные шрифты OS - максимальная скорость',
  },
] as const;

export const DEFAULT_PAIR_ID = FONT_PAIRS[0]!.id;

/**
 * Арабские шрифты доступны отдельно от латинских/кириллических пар:
 * Manrope/Inter/Lora не имеют арабских глифов (или имеют плохие),
 * поэтому arabic-стек переключается независимо.
 */
export interface ArabicFont {
  id: string;
  name: string;
  /** CSS value для --font-ar (базовый арабский шрифт; --font-arabic — алиас) */
  value: string;
  hint?: string;
}

export const ARABIC_FONTS: readonly ArabicFont[] = [
  {
    id: 'amiri',
    name: 'Amiri',
    value: "'Amiri', 'Scheherazade New', 'Noto Naskh Arabic', serif",
    hint: 'дефолт - hand-revival Bulaq press, scholarly naskh',
  },
  {
    id: 'scheherazade',
    name: 'Scheherazade New',
    value: "'Scheherazade New', 'Amiri', 'Noto Naskh Arabic', serif",
    hint: 'academic naskh - SIL, расширенный отображение харакат',
  },
  {
    id: 'noto-naskh',
    name: 'Noto Naskh Arabic',
    value: "'Noto Naskh Arabic', 'Amiri', serif",
    hint: 'Google Noto - modern naskh, neutral feel',
  },
  {
    id: 'reem-kufi',
    name: 'Reem Kufi',
    value: "'Reem Kufi', 'Amiri', serif",
    hint: 'geometric kufi - современный display-стиль',
  },
  {
    id: 'cairo',
    name: 'Cairo',
    value: "'Cairo', 'Amiri', sans-serif",
    hint: 'geometric arabic sans - modern UI feel',
  },
  {
    id: 'tajawal',
    name: 'Tajawal',
    value: "'Tajawal', 'Amiri', sans-serif",
    hint: 'arabic sans от Boutros - neutral modern',
  },
  {
    id: 'el-messiri',
    name: 'El Messiri',
    value: "'El Messiri', 'Amiri', serif",
    hint: 'balanced naskh от Mohamed Gaber - читаемый для academic prose',
  },
  {
    id: 'markazi-text',
    name: 'Markazi Text',
    value: "'Markazi Text', 'Amiri', serif",
    hint: 'book-optimized arabic serif для long-form reading',
  },
  {
    id: 'mada',
    name: 'Mada',
    value: "'Mada', 'Amiri', sans-serif",
    hint: 'geometric sans от Khaled Hosny (автор Amiri) - modern variant',
  },
  {
    id: 'aref-ruqaa',
    name: 'Aref Ruqaa',
    value: "'Aref Ruqaa', 'Amiri', serif",
    hint: 'calligraphic ruqʿah - "handwritten manuscript" feel',
  },
] as const;

export const DEFAULT_ARABIC_FONT_ID = ARABIC_FONTS[0]!.id;

const STORAGE_KEY_PAIR = 'app.fontPair';
const STORAGE_KEY_WEIGHT = 'app.titleWeight';
const STORAGE_KEY_ARABIC = 'app.arabicFont';
const STORAGE_KEY_BODY_WEIGHT = 'app.bodyWeight';
const STORAGE_KEY_DENSITY = 'app.density';

export const DEFAULT_TITLE_WEIGHT = 600;
export const DEFAULT_BODY_WEIGHT = 400;
export const DEFAULT_DENSITY = 1;

function readPersistedPairId(): string {
  if (typeof window === 'undefined') return DEFAULT_PAIR_ID;
  const raw = window.localStorage.getItem(STORAGE_KEY_PAIR);
  if (raw && FONT_PAIRS.some((p) => p.id === raw)) return raw;
  return DEFAULT_PAIR_ID;
}

function readPersistedWeight(): number {
  if (typeof window === 'undefined') return 600;
  const raw = window.localStorage.getItem(STORAGE_KEY_WEIGHT);
  const n = raw ? Number(raw) : NaN;
  if (Number.isFinite(n) && n >= 300 && n <= 900) return n;
  return 600;
}

function readPersistedArabicId(): string {
  if (typeof window === 'undefined') return DEFAULT_ARABIC_FONT_ID;
  const raw = window.localStorage.getItem(STORAGE_KEY_ARABIC);
  if (raw && ARABIC_FONTS.some((f) => f.id === raw)) return raw;
  return DEFAULT_ARABIC_FONT_ID;
}

function readPersistedNumber(
  key: string,
  fallback: number,
  min: number,
  max: number,
): number {
  if (typeof window === 'undefined') return fallback;
  const raw = window.localStorage.getItem(key);
  const n = raw ? Number(raw) : NaN;
  if (Number.isFinite(n) && n >= min && n <= max) return n;
  return fallback;
}

interface FontPairState {
  pairId: string;
  /** Вес заголовков книг (Card.Title non-arabic). 300-900. */
  titleWeight: number;
  /** Вес body UI текста (нав, кнопки, лейблы). 300-700. */
  bodyWeight: number;
  /** Плотность UI - множитель для vertical rhythm. 0.85-1.15. */
  density: number;
  arabicFontId: string;
  setPair: (id: string) => void;
  setTitleWeight: (w: number) => void;
  setBodyWeight: (w: number) => void;
  setDensity: (d: number) => void;
  setArabicFont: (id: string) => void;
  resetAll: () => void;
}

export const useFontPairStore = create<FontPairState>((set) => ({
  pairId: readPersistedPairId(),
  titleWeight: readPersistedWeight(),
  bodyWeight: readPersistedNumber(
    STORAGE_KEY_BODY_WEIGHT,
    DEFAULT_BODY_WEIGHT,
    300,
    700,
  ),
  density: readPersistedNumber(STORAGE_KEY_DENSITY, DEFAULT_DENSITY, 0.85, 1.15),
  arabicFontId: readPersistedArabicId(),
  setPair: (id) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY_PAIR, id);
    }
    set({ pairId: id });
  },
  setTitleWeight: (w) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY_WEIGHT, String(w));
    }
    set({ titleWeight: w });
  },
  setBodyWeight: (w) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY_BODY_WEIGHT, String(w));
    }
    set({ bodyWeight: w });
  },
  setDensity: (d) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY_DENSITY, String(d));
    }
    set({ density: d });
  },
  setArabicFont: (id) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY_ARABIC, id);
    }
    set({ arabicFontId: id });
  },
  resetAll: () => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(STORAGE_KEY_PAIR);
      window.localStorage.removeItem(STORAGE_KEY_WEIGHT);
      window.localStorage.removeItem(STORAGE_KEY_BODY_WEIGHT);
      window.localStorage.removeItem(STORAGE_KEY_DENSITY);
      window.localStorage.removeItem(STORAGE_KEY_ARABIC);
    }
    set({
      pairId: DEFAULT_PAIR_ID,
      titleWeight: DEFAULT_TITLE_WEIGHT,
      bodyWeight: DEFAULT_BODY_WEIGHT,
      density: DEFAULT_DENSITY,
      arabicFontId: DEFAULT_ARABIC_FONT_ID,
    });
  },
}));

/** Утилита для получения текущей пары - по pairId возвращает FontPair объект. */
export function findPair(id: string): FontPair {
  return FONT_PAIRS.find((p) => p.id === id) ?? FONT_PAIRS[0]!;
}

export function findArabicFont(id: string): ArabicFont {
  return ARABIC_FONTS.find((f) => f.id === id) ?? ARABIC_FONTS[0]!;
}
