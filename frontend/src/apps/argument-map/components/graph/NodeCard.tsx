import { memo, useMemo, useState } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps, Node } from '@xyflow/react';
import { Languages } from 'lucide-react';
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
type TranslationRef = components['schemas']['NodeTranslationRef'];

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

/**
 * Default-перевод первый, далее по created_at ASC (backend сортирует).
 * Helper извлекает default либо первый (если default не помечен явно).
 */
function pickInitialTranslation(translations: TranslationRef[]): TranslationRef | null {
  if (translations.length === 0) return null;
  return translations.find((t) => t.isDefault) ?? translations[0] ?? null;
}

function NodeCard({ data, selected }: NodeProps<NodeCardNode>) {
  const t = useT();
  const status: NodeStatus = data.status ?? 'UNVERIFIED';
  const nodeType: NodeType = data.nodeType ?? 'CLAIM';
  const statusToken = STATUS_TOKENS[status];

  const fullContent = data.content ?? '';
  // wrap translations через useMemo чтобы стабильная ref для зависимых
  // useMemo/useEffect, иначе каждый render новый array даже для одних
  // и тех же данных - cascading renders
  const translations: TranslationRef[] = useMemo(() => data.translations ?? [], [data.translations]);
  const hasTranslations = translations.length > 0;
  const originalLang = resolveOriginalLang(fullContent, data.originalLang);

  // Выбранный перевод - либо default, либо первый. Может переключаться
  // через dropdown (если переводов >1). Храним только preferred id;
  // resolved-перевод вычисляем через find чтобы автоматически fallback
  // на default если предыдущий id больше не присутствует (после refetch
  // graph data). Никакого useEffect для sync нет - resolve чистый
  // computed value, нет race condition
  const initial = useMemo(() => pickInitialTranslation(translations), [translations]);
  const [preferredTranslationId, setPreferredTranslationId] = useState<string | null>(null);

  const selectedTranslation = useMemo(() => {
    if (preferredTranslationId) {
      const match = translations.find((t) => t.id === preferredTranslationId);
      if (match) return match;
    }
    return initial;
  }, [preferredTranslationId, translations, initial]);
  const selectedTranslationId = selectedTranslation?.id ?? null;
  const translation = selectedTranslation?.body ?? '';

  // Глобальный режим из preferencesStore - 'original' | 'translation' | 'both'.
  // Local override (через toggle в карточке) позволяет временно посмотреть
  // другой режим для отдельного узла, не меняя глобальную настройку
  const globalMode = usePreferencesStore((s) => s.bilingualMode);
  const [localOverride, setLocalOverride] = useState<BilingualModePref | null>(null);
  const effectiveMode: BilingualModePref = localOverride ?? globalMode;

  const [dropdownOpen, setDropdownOpen] = useState(false);

  // Toggle показываем только если узел реально имеет перевод - иначе
  // переключать нечего. Если перевода нет, всегда рендерим original
  const showToggle = hasTranslations;
  const renderMode: BilingualModePref = hasTranslations ? effectiveMode : 'original';

  // Определяем какие блоки рендерить
  const showOriginal = renderMode === 'original' || renderMode === 'both';
  const showTranslation = (renderMode === 'translation' || renderMode === 'both') && hasTranslations;

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

            {/* Translator dropdown - показываем только при >1 переводе.
                При одном переводе - просто label с именем переводчика */}
            {translations.length > 1 ? (
              <div className="relative mb-1.5">
                <button
                  type="button"
                  tabIndex={-1}
                  className="flex items-center gap-1 text-[10px] text-ink-500 hover:text-accent-600 transition-colors"
                  onClick={(e) => {
                    e.stopPropagation();
                    setDropdownOpen((v) => !v);
                  }}
                  aria-label={t('node.translations.dropdown_label')}
                >
                  <span>
                    {selectedTranslation?.translatorName
                      ? selectedTranslation.translatorName
                      : t('node.translations.dropdown_anonymous')}
                  </span>
                  <span className="text-ink-400">▾</span>
                </button>
                {dropdownOpen && (
                  <div
                    className="absolute z-10 top-full mt-1 right-0 min-w-[160px] rounded-md border border-border bg-elevated shadow-sh2 py-1"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {translations.map((tr) => (
                      <button
                        key={tr.id}
                        type="button"
                        tabIndex={-1}
                        className={`w-full text-start px-2 py-1 text-xs hover:bg-surface ${
                          tr.id === selectedTranslationId ? 'bg-accent-50 text-accent-700' : 'text-ink-700'
                        }`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (tr.id) {
                            setPreferredTranslationId(tr.id);
                          }
                          setDropdownOpen(false);
                        }}
                      >
                        <span>
                          {tr.translatorName ?? t('node.translations.dropdown_anonymous')}
                        </span>
                        <span className="ms-1 text-ink-400 uppercase">{tr.language}</span>
                        {tr.isDefault && <span className="ms-1 text-accent-500">★</span>}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              selectedTranslation?.translatorName && (
                <div className="mb-1 text-[10px] text-ink-400">
                  {selectedTranslation.translatorName}
                </div>
              )
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

export default memo(NodeCard);
