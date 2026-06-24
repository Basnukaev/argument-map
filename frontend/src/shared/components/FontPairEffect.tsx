import { useEffect } from 'react';
import {
  findArabicFont,
  findPair,
  useFontPairStore,
} from '@/shared/stores/fontPairStore';

/**
 * Side-effect компонент: применяет выбранную пару шрифтов и weight
 * заголовков как inline CSS-переменные на `<html>`. Это переопределяет
 * базовые значения из styles/tokens.css.
 *
 * Сделано через CSS variables, а не через React re-render всех текстов:
 * один setProperty - мгновенное применение во всём DOM без повторной
 * mount-фазы. Аналогично ThemeEffect.
 */
export function FontPairEffect() {
  const pairId = useFontPairStore((s) => s.pairId);
  const titleWeight = useFontPairStore((s) => s.titleWeight);
  const bodyWeight = useFontPairStore((s) => s.bodyWeight);
  const density = useFontPairStore((s) => s.density);
  const arabicFontId = useFontPairStore((s) => s.arabicFontId);

  useEffect(() => {
    const pair = findPair(pairId);
    const html = document.documentElement;
    html.style.setProperty('--font-ui', pair.ui);
    html.style.setProperty('--font-serif', pair.serif);
  }, [pairId]);

  useEffect(() => {
    document.documentElement.style.setProperty(
      '--title-weight',
      String(titleWeight),
    );
  }, [titleWeight]);

  useEffect(() => {
    document.documentElement.style.setProperty(
      '--body-weight',
      String(bodyWeight),
    );
  }, [bodyWeight]);

  useEffect(() => {
    document.documentElement.style.setProperty(
      '--density-scale',
      String(density),
    );
  }, [density]);

  useEffect(() => {
    const af = findArabicFont(arabicFontId);
    // Выставляем БАЗОВУЮ переменную --font-ar (не алиас --font-arabic):
    // tokens.css определяет `--font-arabic: var(--font-ar)`, поэтому
    // одной записью каскадим во ВСЁ, что читает арабский шрифт —
    // --font-arabic / font-naskh utility, .prose-arabic, орнаменты
    // ayah-box (tiptap.css). Это делает контрол «Арабский шрифт»
    // единственным источником истины для арабского контента книги.
    document.documentElement.style.setProperty('--font-ar', af.value);
  }, [arabicFontId]);

  return null;
}
