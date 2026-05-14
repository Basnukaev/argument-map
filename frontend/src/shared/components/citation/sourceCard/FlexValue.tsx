import { hasArabicScript } from '@/shared/i18n';
import { Bdi } from './Bdi';

type Props = {
  text: string | null | undefined;
  size?: number;
  weight?: number;
};

/**
 * Рендерит значение с правильным шрифтом + direction для script:
 * - Arabic → Noto Naskh, `<span lang="ar">`
 * - Latin/Cyrillic → Inter, в `<bdi dir="ltr">` (читается LTR, позиция в RTL потоке)
 *
 * Returns null для пустых значений - вызывающий код не нужно guard'ить
 */
export function FlexValue({ text, size = 13, weight = 500 }: Props) {
  if (!text) return null;
  if (hasArabicScript(text)) {
    return (
      <span
        lang="ar"
        className="font-naskh text-slate-900"
        style={{ fontSize: size, fontWeight: weight }}
      >
        {text}
      </span>
    );
  }
  return (
    <Bdi>
      <span style={{ fontSize: size, fontWeight: weight }}>{text}</span>
    </Bdi>
  );
}
