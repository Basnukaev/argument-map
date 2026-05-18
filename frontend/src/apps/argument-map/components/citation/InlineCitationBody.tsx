import { Fragment, useMemo } from 'react';
import type { components } from '@/shared/api/types';
import { parseInlineCitations } from '@/apps/argument-map/utils/inlineCitations';
import InlineCitationMarker from './InlineCitationMarker';

type InlineCitationRef = components['schemas']['InlineCitationRef'];

interface Props {
  body: string;
  citations?: InlineCitationRef[];
  /** className на корневой wrapper - чтобы caller контролировал текст-стили */
  className?: string;
  /** dir attribute. Default "auto" - браузер сам определит по первому сильному
   *  символу. Caller может передать "ltr"/"rtl" если уверен */
  dir?: 'auto' | 'ltr' | 'rtl';
  /** Если true (по умолчанию) - whitespace-pre-wrap для сохранения переносов
   *  строк. Для inline-render внутри одной строки можно передать false */
  preserveWhitespace?: boolean;
}

/**
 * Wrapper для рендера plain-text body узла с поддержкой inline citation
 * маркеров `[N]`. Парсит body на text/citation сегменты, рендерит каждый
 * citation через <InlineCitationMarker>.
 *
 * Если в body нет ни одного маркера - рендер идентичен `<p>{body}</p>`
 * (no-op overhead).
 *
 * Использование:
 * ```tsx
 * <InlineCitationBody
 *   body="Доказательство [1] и [2]"
 *   citations={node.inlineCitations}
 *   className="text-sm leading-relaxed"
 * />
 * ```
 *
 * Mapping `[N]` → citation: по 1-based ordinal. Если ordinal не найден в
 * citations - <InlineCitationMarker> рендерится в dead-стиле (grey).
 */
function InlineCitationBody({
  body,
  citations,
  className,
  dir = 'auto',
  preserveWhitespace = true,
}: Props) {
  // parseInlineCitations - чистая функция, useMemo только потому что body
  // может быть длинным и пересоздание лишнее при ре-рендерах parent'а
  const segments = useMemo(() => parseInlineCitations(body), [body]);
  const citationByOrdinal = useMemo(() => {
    const map = new Map<number, InlineCitationRef>();
    if (!citations) return map;
    for (const c of citations) {
      if (c.ordinal != null) {
        map.set(c.ordinal, c);
      }
    }
    return map;
  }, [citations]);

  const wsClass = preserveWhitespace ? 'whitespace-pre-wrap' : '';

  return (
    <span dir={dir} className={`${wsClass} ${className ?? ''}`.trim()}>
      {segments.map((seg, i) => {
        if (seg.type === 'text') {
          return <Fragment key={i}>{seg.text}</Fragment>;
        }
        return (
          <InlineCitationMarker
            key={i}
            ordinal={seg.ordinal}
            citation={citationByOrdinal.get(seg.ordinal)}
          />
        );
      })}
    </span>
  );
}

export default InlineCitationBody;
