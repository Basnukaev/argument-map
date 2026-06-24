/**
 * Подпись-чип формулы передачи (عن / حدثنا / …) рисуем ТОЛЬКО для непустой
 * (не-пробельной) строки. turuq version-/merge-рёбра приходят с `null` или
 * пустой `transmissionPhrase`; пустой чип (фон `bg-elevated` + рамка + тень)
 * в PNG-экспорте вырождался в «чёрный квадратик» на точках ветвления/слияния
 * (бэклог turuq-graph). Текста в нём нет — не рендерим его вообще.
 *
 * Возвращает исходную строку (если есть что показать) или `null`.
 */
export function visibleTransmissionPhrase(
  phrase: string | null | undefined,
): string | null {
  return phrase && phrase.trim() !== '' ? phrase : null;
}
