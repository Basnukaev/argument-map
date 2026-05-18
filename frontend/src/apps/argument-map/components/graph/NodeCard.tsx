import { useState } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { Languages, MoreHorizontal } from 'lucide-react';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import TypeChip from '@/shared/components/ui/TypeChip';
import InlineCitationBody from '@/apps/argument-map/components/citation/InlineCitationBody';
import { STATUS_TOKENS, type NodeStatus, type NodeType } from '@/shared/utils/designTokens';
import { hasArabicScript, useT } from '@/shared/i18n';
import {
  usePreferencesStore,
  type BilingualModePref,
} from '@/shared/stores/preferencesStore';
import type { components } from '@/shared/api/types';
import VoteWidget from './VoteWidget';

type NodeDto = components['schemas']['NodeResponse'];

export type NodeCardData = NodeDto;
export type NodeCardNode = Node<NodeCardData, 'argumentNode'>;

const MAX_PREVIEW_LEN = 220;

/**
 * Эффективный язык оригинала. Если бэк прислал явный originalLang -
 * используем его, иначе определяем по содержимому через hasArabicScript
 * (только так в MVP - 'ar' либо 'ru'). 'en' через эвристику не угадаем,
 * но это редкий кейс в проекте (контент в основном RU/AR).
 */
function resolveOriginalLang(content: string, explicitLang?: string): 'ar' | 'ru' | 'en' {
  if (explicitLang === 'ar' || explicitLang === 'ru' || explicitLang === 'en') {
    return explicitLang;
  }
  return hasArabicScript(content) ? 'ar' : 'ru';
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function NodeCard({ data, selected }: NodeProps<NodeCardNode>) {
  const t = useT();
  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const statusToken = STATUS_TOKENS[status];

  const fullContent = data.content ?? '';
  const translation = data.translation ?? '';
  const hasTranslation = translation.trim().length > 0;
  const originalLang = resolveOriginalLang(fullContent, data.originalLang);

  // Глобальный режим из preferencesStore - 'original' | 'translation' | 'both'.
  // Local override (через toggle в карточке) позволяет временно посмотреть
  // другой режим для отдельного узла, не меняя глобальную настройку
  const globalMode = usePreferencesStore((s) => s.bilingualMode);
  const [localOverride, setLocalOverride] = useState<BilingualModePref | null>(null);
  const effectiveMode: BilingualModePref = localOverride ?? globalMode;

  // Toggle показываем только если узел реально имеет перевод - иначе
  // переключать нечего. Если перевода нет, всегда рендерим original
  const showToggle = hasTranslation;
  const renderMode: BilingualModePref = hasTranslation ? effectiveMode : 'original';

  // Определяем какие блоки рендерить
  const showOriginal = renderMode === 'original' || renderMode === 'both';
  const showTranslation = (renderMode === 'translation' || renderMode === 'both') && hasTranslation;

  // первая строка трактуется как заголовок, остаток - как body. Если перенос
  // отсутствует - всё считается заголовком (короткие узлы выглядят чище)
  const newlineIndex = fullContent.indexOf('\n');
  const title =
    newlineIndex === -1 ? fullContent : fullContent.slice(0, newlineIndex);
  const body = newlineIndex === -1 ? '' : fullContent.slice(newlineIndex + 1).trim();
  const truncatedBody = truncate(body, MAX_PREVIEW_LEN);

  // Перевод: тоже разделяется на title/body по первому переносу. Для
  // короткого перевода без переноса - всё уходит в title-блок
  const translationNewlineIndex = translation.indexOf('\n');
  const translationTitle = translationNewlineIndex === -1
    ? translation
    : translation.slice(0, translationNewlineIndex);
  const translationBody = translationNewlineIndex === -1
    ? ''
    : translation.slice(translationNewlineIndex + 1).trim();
  const truncatedTranslationBody = truncate(translationBody, MAX_PREVIEW_LEN);

  // Стили для оригинала - арабский получает naskh + увеличенный leading.
  // Direction через dir="auto" - браузер сам определит по первому
  // strong-символу. Layout самой карточки остаётся LTR независимо от
  // содержимого
  const isOriginalArabic = originalLang === 'ar';
  const originalTitleClass = isOriginalArabic
    ? 'font-naskh text-sm font-semibold leading-[1.8] text-ink-900 text-pretty whitespace-pre-wrap break-words text-start'
    : 'text-sm font-semibold leading-snug text-ink-900 text-pretty whitespace-pre-wrap break-words text-start';
  const originalBodyClass = isOriginalArabic
    ? 'mt-1 font-naskh text-sm leading-[1.85] text-ink-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words text-start'
    : 'mt-1 text-xs leading-relaxed text-ink-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words text-start';

  // Перевод обычно на ru/en - regular sans-serif, обычный leading
  const translationTitleClass =
    'text-sm font-semibold leading-snug text-ink-900 text-pretty whitespace-pre-wrap break-words text-start';
  const translationBodyClass =
    'mt-1 text-xs leading-relaxed text-ink-600 line-clamp-2 text-pretty whitespace-pre-wrap break-words text-start';

  // Cyclический toggle: original → translation → both → original
  function cycleMode(): void {
    const next: Record<BilingualModePref, BilingualModePref> = {
      original: 'translation',
      translation: 'both',
      both: 'original',
    };
    setLocalOverride(next[effectiveMode]);
  }

  // Handle hit-area расширена до 28×28 через ::before inset-[-8px] - удобно
  // попадать мышкой даже в визуально-12×12 точки. Видимы только на hover/select
  const handleClass =
    '!w-3 !h-3 !bg-elevated !border-[1.5px] !border-accent-500 opacity-0 group-hover:opacity-100 transition-opacity cursor-crosshair before:absolute before:inset-[-8px] before:content-[""]';

  return (
    <div
      className={`group relative w-[280px] rounded-md border bg-elevated transition-shadow ${
        selected
          ? 'border-accent-500 ring-2 ring-accent-500/30 shadow-sh3'
          : 'border-border shadow-sh1 hover:border-border-strong hover:shadow-sh2'
      }`}
      title={fullContent}
    >
      <div
        data-testid="status-bar"
        className={`absolute left-0 top-0 bottom-0 w-[5px] rounded-l-md ${statusToken.bar}`}
        aria-hidden="true"
      />

      <Handle type="source" position={Position.Top} id="top" className={handleClass} />
      <Handle type="source" position={Position.Right} id="right" className={handleClass} />
      <Handle type="source" position={Position.Bottom} id="bottom" className={handleClass} />
      <Handle type="source" position={Position.Left} id="left" className={handleClass} />

      <div className="pl-4 pr-3 py-3">
        <div className="mb-1.5 flex items-center gap-2">
          <TypeChip type={nodeType} size="sm" />
          <span className="flex-1" />
          {showToggle && (
            <button
              type="button"
              tabIndex={-1}
              aria-label={t('node.bilingual.toggle_aria')}
              title={t(`node.bilingual.mode.${renderMode}` as const)}
              className={`text-ink-400 hover:text-accent-600 transition-colors ${
                localOverride ? 'text-accent-600' : ''
              }`}
              onClick={(e) => {
                e.stopPropagation();
                cycleMode();
              }}
            >
              <Languages size={14} aria-hidden="true" />
            </button>
          )}
          <StatusBadge status={status} size="sm" />
          <button
            type="button"
            tabIndex={-1}
            aria-label="actions"
            className="-mr-1 text-ink-400 hover:text-ink-700 transition-colors"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal size={14} aria-hidden="true" />
          </button>
        </div>

        {showOriginal && (
          <>
            {title ? (
              <p dir="auto" className={originalTitleClass}>
                {title}
              </p>
            ) : (
              <p className="text-sm italic text-ink-400">(...)</p>
            )}

            {truncatedBody && (
              <InlineCitationBody
                body={truncatedBody}
                citations={data.inlineCitations}
                dir="auto"
                className={`block ${originalBodyClass}`}
              />
            )}
          </>
        )}

        {showTranslation && (
          <div
            className={
              renderMode === 'both'
                ? 'mt-3 border-t border-border pt-2'
                : ''
            }
          >
            {renderMode === 'both' && (
              <div className="mb-1 text-[10px] uppercase tracking-wider text-ink-400">
                {t('node.bilingual.translation_label_in_card')}
              </div>
            )}
            {translationTitle && (
              <p dir="auto" className={translationTitleClass}>
                {translationTitle}
              </p>
            )}
            {truncatedTranslationBody && (
              <p dir="auto" className={translationBodyClass}>
                {truncatedTranslationBody}
              </p>
            )}
          </div>
        )}

        {(nodeType === 'ARGUMENT' || nodeType === 'EVIDENCE') && (
          <div className="mt-2 flex justify-end">
            <VoteWidget
              nodeId={data.id ?? ''}
              upvotes={data.voteUpvotes ?? 0}
              downvotes={data.voteDownvotes ?? 0}
              score={data.voteScore ?? 0}
              userVote={data.userVote ?? null}
            />
          </div>
        )}
      </div>
    </div>
  );
}

export default NodeCard;
