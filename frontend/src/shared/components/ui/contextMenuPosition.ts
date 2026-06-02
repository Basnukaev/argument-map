/**
 * Зажимает координаты контекстного меню в пределах viewport. Если меню
 * вылезает за правый/нижний край — сдвигаем его так, чтобы оно целиком
 * поместилось (с небольшим отступом `margin`). Никогда не уходит за
 * левый/верхний край (>= margin). Чистая функция — тестируется изолированно.
 *
 * Вынесено из ContextMenu.tsx отдельным модулем: один компонент — один
 * файл (react-refresh/only-export-components), хелпер шарится с тестом.
 */
export function clampMenuPosition(
  x: number,
  y: number,
  menuWidth: number,
  menuHeight: number,
  viewportWidth: number,
  viewportHeight: number,
  margin = 8,
): { left: number; top: number } {
  // Доступное место с учётом отступа. Если меню шире viewport — прижимаем
  // к левому краю (Math.max ниже не даст уйти в минус).
  const maxLeft = Math.max(margin, viewportWidth - menuWidth - margin);
  const maxTop = Math.max(margin, viewportHeight - menuHeight - margin);
  return {
    left: Math.min(Math.max(x, margin), maxLeft),
    top: Math.min(Math.max(y, margin), maxTop),
  };
}
