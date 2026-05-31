import { wordDiff } from '@/apps/hadith/utils/matnDiff';

interface Props {
  /** Базовая (основная) редакция matn. */
  base: string;
  /** Сравниваемый вариант. */
  variant: string;
}

/**
 * Пословный diff matn'а: зелёным — слова, добавленные в этом варианте,
 * красным зачёркнутым — отсутствующие (есть в основной редакции). RTL,
 * наскх. diff дешёвый (~20 слов) — без memo (YAGNI).
 */
function MatnDiff({ base, variant }: Props) {
  const ops = wordDiff(base, variant);
  return (
    <p className="font-arabic text-lg leading-loose text-ink-900" dir="rtl">
      {ops.map((op, idx) => {
        const cls =
          op.type === 'add'
            ? 'rounded-sm bg-emerald-100 px-0.5 text-emerald-800'
            : op.type === 'del'
              ? 'rounded-sm bg-rose-100 px-0.5 text-rose-700 line-through'
              : '';
        // key с index намеренно: diff — производный, append-only, не
        // переупорядочиваемый список без естественного id (слова повторяются)
        return (
          <span key={`${idx}-${op.text}`} className={cls}>
            {op.text}{' '}
          </span>
        );
      })}
    </p>
  );
}

export default MatnDiff;
