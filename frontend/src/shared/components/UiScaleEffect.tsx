import { useEffect } from 'react';
import {
  BASE_FONT_SIZE_PX,
  scaleMultiplier,
  useUiScaleStore,
} from '@/shared/stores/uiScaleStore';

/**
 * Side-effect компонент: применяет выбранный масштаб интерфейса как
 * base font-size на `<html>`. Весь UI на Tailwind rem-based, поэтому
 * один setProperty масштабирует всё дерево (nav, кнопки, карточки,
 * узлы графа, reader) - глобальный zoom by design.
 *
 * Аналогично ThemeEffect / FontPairEffect - применение через DOM-стиль,
 * не React re-render, поэтому смена пресета мгновенная без re-mount.
 */
export function UiScaleEffect() {
  const scale = useUiScaleStore((s) => s.scale);

  useEffect(() => {
    const px = BASE_FONT_SIZE_PX * scaleMultiplier(scale);
    document.documentElement.style.fontSize = `${px}px`;
  }, [scale]);

  return null;
}
