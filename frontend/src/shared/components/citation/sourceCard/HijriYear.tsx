import { useT } from '@/shared/i18n';

type Props = {
  hijri: number | null | undefined;
  gregorian?: number | null | undefined;
};

/**
 * Год по хиджре с inline arabic indicator «هـ», опционально + григорианский:
 *   1420 هـ              - только хиджри
 *   1420 هـ  /  1999 г.  - оба (suffix зависит от локали - в ar `م.`, в ru `г.`)
 *
 * Returns null если ничего не задано
 */
export function HijriYear({ hijri, gregorian }: Props) {
  const t = useT();
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
      {gregorian != null && (
        <>
          {gregorian} {t('cite.year.gregorian_suffix')}
        </>
      )}
    </span>
  );
}
