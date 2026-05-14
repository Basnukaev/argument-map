type Props = {
  hijri: number | null | undefined;
  gregorian?: number | null | undefined;
};

/**
 * Год по хиджре с inline arabic indicator «هـ», опционально + григорианский:
 *   1420 هـ              - только хиджри
 *   1420 هـ  /  1999 г.  - оба
 *
 * Returns null если ничего не задано
 */
export function HijriYear({ hijri, gregorian }: Props) {
  if (hijri == null && gregorian == null) return null;
  return (
    <span>
      {hijri != null && (
        <>
          {hijri}
          <span className="font-naskh">&thinsp;هـ</span>
        </>
      )}
      {hijri != null && gregorian != null && <>{'  /  '}</>}
      {gregorian != null && <>{gregorian} г.</>}
    </span>
  );
}
