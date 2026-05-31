interface LogoProps {
  /** Размер квадратной марки в px (по умолчанию 28 — под h-7 в Header) */
  size?: number;
  className?: string;
}

/**
 * Бренд-марка платформы — 8-конечная звезда Rub el Hizb (۞, два
 * наложенных квадрата) с узлом-точкой в центре. Мотив объединяет три
 * лица платформы: графы аргументации, иснад-деревья хадисов, библиотеку.
 *
 * <p>Тайл рисуется через `currentColor` — цвет задаётся утилитой
 * `text-accent-*` на родителе, поэтому марка следует за accent-токеном
 * при смене темы. Статический `public/favicon.svg` дублирует геометрию
 * для вкладки браузера (там фиксированный indigo, тема не нужна).
 */
function Logo({ size = 28, className }: LogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      className={className}
      role="img"
      aria-hidden
      focusable="false"
    >
      <rect width="32" height="32" rx="7" fill="currentColor" />
      <g fill="#ffffff">
        <rect x="9" y="9" width="14" height="14" rx="1.6" />
        <rect x="9" y="9" width="14" height="14" rx="1.6" transform="rotate(45 16 16)" />
      </g>
      <circle cx="16" cy="16" r="3.2" fill="currentColor" />
      <circle cx="16" cy="16" r="1.5" fill="#ffffff" />
    </svg>
  );
}

export default Logo;
