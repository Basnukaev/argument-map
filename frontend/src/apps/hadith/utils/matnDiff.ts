// Огласовки (харакат) снимаем при сопоставлении, чтобы diff показывал
// расхождения в СЛОВАХ, а не в диакритике. Явные codepoints (ASCII \u),
// чтобы НЕ задеть арабские цифры (U+0660-0669) и пунктуацию:
//   064B-0656 — танвин/фатха/дамма/касра/шадда/сукун + madda/hamza/subscript-alef
//   0670 — dagger alef · 06D6-06DC — кораничные малые знаки · 0640 — tatweel
const TASHKEEL = /[ً-ٰٖۖ-ۜـ]/g;

function normalize(word: string): string {
  return word.replace(TASHKEEL, '');
}

function tokenize(text: string): string[] {
  return text.trim().split(/\s+/).filter(Boolean);
}

export type DiffType = 'same' | 'add' | 'del';

export interface DiffOp {
  type: DiffType;
  text: string;
}

/**
 * Пословный diff варианта matn относительно базовой (основной) редакции.
 * LCS по нормализованным (без огласовок) токенам; рендерятся оригинальные
 * слова с диакритикой. `add` = слово есть в variant, отсутствует в base;
 * `del` = слово есть в base, опущено в variant; `same` = общее.
 */
export function wordDiff(base: string, variant: string): DiffOp[] {
  const a = tokenize(base);
  const b = tokenize(variant);
  const na = a.map(normalize);
  const nb = b.map(normalize);
  const m = a.length;
  const n = b.length;

  // dp[i][j] = длина LCS суффиксов a[i:] и b[j:]
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array<number>(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    const row = dp[i]!;
    const below = dp[i + 1]!;
    for (let j = n - 1; j >= 0; j--) {
      row[j] = na[i] === nb[j] ? below[j + 1]! + 1 : Math.max(below[j]!, row[j + 1]!);
    }
  }

  const ops: DiffOp[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (na[i] === nb[j]) {
      ops.push({ type: 'same', text: b[j]! });
      i++;
      j++;
    } else if (dp[i + 1]![j]! >= dp[i]![j + 1]!) {
      ops.push({ type: 'del', text: a[i]! });
      i++;
    } else {
      ops.push({ type: 'add', text: b[j]! });
      j++;
    }
  }
  while (i < m) {
    ops.push({ type: 'del', text: a[i]! });
    i++;
  }
  while (j < n) {
    ops.push({ type: 'add', text: b[j]! });
    j++;
  }
  return ops;
}

/**
 * Есть ли пословное расхождение (после нормализации огласовок). Для решения,
 * показывать ли кнопку diff: матны, отличающиеся только харакатом, считаются
 * одинаковыми — кнопка не нужна.
 */
export function hasWordDiff(base: string, variant: string): boolean {
  return wordDiff(base, variant).some((op) => op.type !== 'same');
}
