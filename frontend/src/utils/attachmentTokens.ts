import {
  BookOpen,
  ScrollText,
  Library,
  FileText,
  ExternalLink,
  type LucideIcon,
} from 'lucide-react';
import type { components } from '@/api/types';

type SourceDto = components['schemas']['SourceResponse'];
type NodeAuthorityDto = components['schemas']['NodeAuthorityResponse'];

export type SourceType = NonNullable<SourceDto['sourceType']>;
export type Stance = NonNullable<NodeAuthorityDto['stance']>;

export const SOURCE_TYPE_LABEL: Record<SourceType, string> = {
  QURAN: 'аят',
  HADITH: 'хадис',
  BOOK: 'книга',
  ARTICLE: 'статья',
  URL: 'ссылка',
};

export const SOURCE_TYPE_ICON: Record<SourceType, LucideIcon> = {
  QURAN: BookOpen,
  HADITH: ScrollText,
  BOOK: Library,
  ARTICLE: FileText,
  URL: ExternalLink,
};

export const SOURCE_TYPE_HINT: Record<SourceType, string> = {
  QURAN: 'аят Корана',
  HADITH: 'хадис из сборника, обязателен grade reliability',
  BOOK: 'цитата из книги, том и страница в citation',
  ARTICLE: 'научная или популярная статья',
  URL: 'ссылка на внешний ресурс',
};

export const SOURCE_TYPE_ORDER: readonly SourceType[] = [
  'QURAN',
  'HADITH',
  'BOOK',
  'ARTICLE',
  'URL',
] as const;

export const STANCE_LABEL: Record<Stance, string> = {
  HOLDS: 'Поддерживает',
  OPPOSES: 'Возражает',
  NEUTRAL: 'Нейтрально',
};

export const STANCE_BADGE_STYLES: Record<Stance, string> = {
  HOLDS: 'bg-emerald-100 text-emerald-800',
  OPPOSES: 'bg-red-100 text-red-800',
  NEUTRAL: 'bg-slate-100 text-slate-700',
};

export const STANCE_RADIO_STYLES: Record<
  Stance,
  { selected: string; idle: string }
> = {
  HOLDS: {
    selected: 'border-emerald-500 bg-emerald-50/60 ring-1 ring-emerald-400',
    idle: 'border-slate-300 hover:bg-slate-50',
  },
  OPPOSES: {
    selected: 'border-red-500 bg-red-50/60 ring-1 ring-red-400',
    idle: 'border-slate-300 hover:bg-slate-50',
  },
  NEUTRAL: {
    selected: 'border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400',
    idle: 'border-slate-300 hover:bg-slate-50',
  },
};

export const STANCE_ORDER: readonly Stance[] = ['HOLDS', 'OPPOSES', 'NEUTRAL'] as const;
