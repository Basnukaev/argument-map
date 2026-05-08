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

export type SourceType = NonNullable<SourceDto['sourceType']>;

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
