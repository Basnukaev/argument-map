import type { ExplanationDto } from '@/apps/hadith/types';

/**
 * Подсветка гариб-слов в матне (бэклог-фича, План 8+). Hero-матн рендерится из
 * `normalizedMatn` — он УЖЕ агрессивно нормализован бэком (ArabicTextNormalizer:
 * сняты огласовки, свёрнуты أإآٱ→ا, ى→ي, ة→ه, хамза-носители, удалён татвиль).
 * GHARIB-`reference` приходит из alminasa С ОГЛАСОВКАМИ и в исходных формах
 * букв (напр. تَطْوَى, بِخَمِيلَةٍ). Чтобы сопоставить слово с уже-folded матном,
 * нормализуем reference ТЕМ ЖЕ конвейером и матчим по нормализованной форме,
 * а на экран отдаём оригинальный токен матна (он и так без диакритики).
 *
 * Матч пословный (последовательность токенов): reference бывает фразой (~5%
 * данных), одиночные слова — частный случай длины 1. Слова без совпадения в
 * матне просто не подсвечиваются (graceful — reference может ссылаться на
 * вариантную передачу, которой нет в hero-матне).
 */

// Огласовки / комбинируемые знаки U+064B–U+065F + надстрочный алиф U+0670 +
// татвиль U+0640. Зеркалит удаляемый бэком диапазон (ArabicTextNormalizer),
// явные codepoints — НЕ задеть арабские цифры (U+0660–U+0669) и пунктуацию.
const TASHKEEL = /[ً-ٰٟـ]/g;

/**
 * Пунктуация с ГРАНИЦ reference и токенов матна перед нормализацией/сравнением.
 * Арабская: ، ؛ ؟ ٪ «»; латинская: , . ; : ! ? Кавычки/скобки/эллипсис.
 * Остаётся частью токена только если внутри слова (не на границе).
 */
const PUNCT_BOUNDARY = /^[،؛؟٪«»,.;:!?()[\]"'…]+|[،؛؟٪«»,.;:!?()[\]"'…]+$/g;

/** Срезать граничную пунктуацию с обеих сторон строки. */
function trimPunct(s: string): string {
  return s.replace(PUNCT_BOUNDARY, '');
}

/**
 * Свести арабское слово к той же нормализованной форме, что бэк применяет к
 * normalized_matn — иначе токены с огласовкой/исходной формой буквы не сойдутся.
 * NFKC раскрывает presentation forms и лигатуры в канонические буквы.
 */
export function normalizeArabic(word: string): string {
  const folded = word
    .normalize('NFKC')
    .replace(TASHKEEL, '')
    .replace(/[آأإٱ]/g, 'ا') // آأإٱ → ا
    .replace(/ى/g, 'ي') // ى → ي
    .replace(/ة/g, 'ه') // ة → ه
    .replace(/ؤ/g, 'و') // ؤ → و
    .replace(/ئ/g, 'ي') // ئ → ي
    .replace(/ء/g, ''); // одиночная хамза ء — удаляем
  return folded;
}

/**
 * Сегмент рендера матна: либо обычный текст (`gharib` отсутствует), либо
 * подсвеченное гариб-слово с привязанным толкованием (`gharib`).
 * `trailPunct` — пунктуация после слова, рендерится вне кнопки-подсветки.
 */
export interface MatnSegment {
  /** Текст сегмента — оригинальный (как в матне), с пробелами для plain-кусков. */
  text: string;
  /** Толкование гариба для подсветки; null → обычный текст. */
  gharib: ExplanationDto | null;
  /** Стабильный ключ для React-списка (offset слова в матне). */
  key: string;
  /**
   * Пунктуация, срезанная с конца последнего токена гариб-сегмента.
   * Рендерится вне кнопки (чтобы подсветка не захватывала знак препинания).
   * Только для сегментов с gharib !== null.
   */
  trailPunct?: string;
}

interface Token {
  /** Оригинальный токен (как в матне). */
  raw: string;
  /** Нормализованная форма (для сопоставления) — без граничной пунктуации. */
  norm: string;
  /** Whitespace перед токеном (сохраняется в выводе дословно). */
  lead: string;
  /** Ведущая пунктуация токена (часть raw, срезана перед нормализацией). */
  leadPunct: string;
  /** Хвостовая пунктуация токена (часть raw, срезана перед нормализацией). */
  trailPunct: string;
}

/**
 * Разбить строку токена на (leadPunct, word, trailPunct).
 * Граничная пунктуация выделяется явным захватом в regex: ведущая → group 1,
 * слово → group 2, хвостовая → group 3. Если строка целиком пунктуация —
 * всё идёт в leadPunct, word и trailPunct пусты.
 */
function splitPunct(raw: string): { leadPunct: string; word: string; trailPunct: string } {
  const m = raw.match(/^([،؛؟٪«»,.;:!?()[\]"'…]*)(.*?)([،؛؟٪«»,.;:!?()[\]"'…]*)$/su);
  if (!m) return { leadPunct: '', word: raw, trailPunct: '' };
  return { leadPunct: m[1] ?? '', word: m[2] ?? '', trailPunct: m[3] ?? '' };
}

/** Разбить матн на токены-слова, сохраняя ведущие пробелы и граничную пунктуацию. */
function tokenize(matn: string): Token[] {
  const tokens: Token[] = [];
  // Слово = непрерывный run не-пробелов; lead = пробелы перед ним.
  const re = /(\s*)(\S+)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(matn)) !== null) {
    const raw = m[2] ?? '';
    const { leadPunct, word, trailPunct } = splitPunct(raw);
    tokens.push({
      lead: m[1] ?? '',
      raw,
      norm: normalizeArabic(word),
      leadPunct,
      trailPunct,
    });
  }
  return tokens;
}

/**
 * Построить сегменты hero-матна с подсветкой вхождений гариб-слов. Каждый
 * GHARIB с непустым `reference` → искомая последовательность нормализованных
 * токенов; первое совпадение в матне (для одного reference) НЕ потребляет
 * другие — подсвечиваются ВСЕ вхождения. При нескольких толкованиях на одно
 * слово берём первое (заголовок-слово один, остальные доступны в секции «غريب»).
 *
 * Пустой `gharib` или ни одного совпадения → вернётся один plain-сегмент со
 * всем матном (graceful — рендер не ломается).
 */
export function buildMatnSegments(matn: string, gharib: ExplanationDto[]): MatnSegment[] {
  const plain: MatnSegment[] = [{ text: matn, gharib: null, key: 'matn' }];
  if (!matn || gharib.length === 0) return plain;

  // Нормализованная фраза (через пробел) → толкование. Первое толкование на
  // данную фразу выигрывает (Map.set не перезапишет — guard ниже).
  const byPhrase = new Map<string, { exp: ExplanationDto; norm: string; len: number }>();
  for (const exp of gharib) {
    if (!exp.reference) continue;
    const norm = tokenize(exp.reference)
      .map((t) => t.norm)
      .filter(Boolean)
      .join(' ');
    if (!norm) continue;
    if (!byPhrase.has(norm)) {
      byPhrase.set(norm, { exp, norm, len: norm.split(' ').length });
    }
  }
  if (byPhrase.size === 0) return plain;

  const tokens = tokenize(matn);
  const segments: MatnSegment[] = [];
  let buf = ''; // накопитель plain-текста (с разделителями)

  const flushPlain = () => {
    if (buf) {
      segments.push({ text: buf, gharib: null, key: `p${segments.length}` });
      buf = '';
    }
  };

  let i = 0;
  let matched = false;
  while (i < tokens.length) {
    // Жадно пробуем самую длинную фразу с текущей позиции (multi-word редки,
    // но reference-фраза должна побеждать одиночное слово).
    let hit: { exp: ExplanationDto; len: number } | null = null;
    for (const cand of byPhrase.values()) {
      if (cand.len > tokens.length - i) continue;
      const windowNorm = tokens
        .slice(i, i + cand.len)
        .map((t) => t.norm)
        .join(' ');
      if (windowNorm === cand.norm && (!hit || cand.len > hit.len)) {
        hit = cand;
      }
    }

    if (hit) {
      flushPlain();
      const slice = tokens.slice(i, i + hit.len);
      const firstToken = slice[0]!;
      const lastToken = slice[slice.length - 1]!;

      // Ведущий пробел перед сегментом
      const lead = firstToken.lead;
      if (lead) {
        segments.push({ text: lead, gharib: null, key: `l${segments.length}` });
      }

      // Ведущая пунктуация первого токена рендерится вне подсветки
      if (firstToken.leadPunct) {
        segments.push({ text: firstToken.leadPunct, gharib: null, key: `lp${segments.length}` });
      }

      // Текст гариб-сегмента: clean words без граничной пунктуации
      const text = slice
        .map((t, idx) => {
          const clean = trimPunct(t.raw);
          // Для не-первых токенов сохраняем пробел (lead) перед словом
          return idx === 0 ? clean : t.lead + clean;
        })
        .join('');

      // Хвостовая пунктуация последнего токена
      const trailPunct = lastToken.trailPunct;

      segments.push({ text, gharib: hit.exp, key: `g${i}`, trailPunct });
      matched = true;
      i += hit.len;
    } else {
      buf += tokens[i]!.lead + tokens[i]!.raw;
      i += 1;
    }
  }
  flushPlain();

  return matched ? segments : plain;
}
