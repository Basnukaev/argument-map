/**
 * Типы графа иснада (Hadith Explorer Phase 3). Бэкенд-контракт
 * GET /api/v1/hadith/hadiths/{id}/sanad-graph. types.ts ещё не
 * regenerated для hadith-домена, поэтому объявлено вручную здесь.
 */

export type NarratorRole = 'PROPHET' | 'COMPANION' | 'NARRATOR' | 'COLLECTOR';

export type ReliabilityGrade =
  | 'THIQA'
  | 'SADUQ'
  | 'MAQBUL'
  | 'DAIF'
  | 'MATRUK'
  | 'SAHABI'
  | 'UNKNOWN';

// type alias (не interface): React Flow v12 требует, чтобы data узла
// удовлетворяла Record<string, unknown> — это проходит для type-литералов,
// но не для interface (у них нет неявной индекс-сигнатуры).
export type NarratorData = {
  narratorId: string | null;
  nameAr: string;
  nameLatin: string | null;
  nameRu: string | null;
  kunya: string | null;
  laqab: string | null;
  yearBirthHijri: number | null;
  yearDeathHijri: number | null;
  birthplace: string | null;
  primaryResidence: string | null;
  deathPlace: string | null;
  reliabilityGrade: ReliabilityGrade | null;
  reliabilityComment: string | null;
  generation: string | null;
  /** Название сборника — только для узлов-составителей (COLLECTOR). */
  collection: string | null;
  tier: number;
};

export interface SanadGraphNodeDto {
  id: string;
  role: NarratorRole;
  data: NarratorData;
}

export interface SanadGraphEdgeDto {
  id: string;
  source: string;
  target: string;
  data: {
    transmissionPhrase: string | null;
    chainGrade: string | null;
    onPrimaryChain: boolean;
    sanadCount: number;
  };
}

export interface SanadSummaryDto {
  id: string;
  collectionRu: string | null;
  collectionAr: string | null;
  chainGrade: string | null;
  primaryChain: boolean;
  collectorNodeId: string | null;
}

export interface SanadGraphResponse {
  hadithId: string;
  nodes: SanadGraphNodeDto[];
  edges: SanadGraphEdgeDto[];
  sanads: SanadSummaryDto[];
}

/** Данные узла React Flow = NarratorData + role (для рендера карточки). */
export type SanadFlowNodeData = NarratorData & { role: NarratorRole };

/** Курируемая оценка хадиса учёным (из detail endpoint, metadata.grades). */
export interface HadithGrade {
  scholar: string | null;
  grade: string | null;
  note: string | null;
}

/** Обёртка PagedResponse<T> с бэка (GET-list endpoints). */
export interface Paged<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** NarratorResponse — каталог/деталь передатчика (علم الرجال). */
export interface NarratorResponseDto {
  id: string;
  authorityId: string | null;
  nameAr: string;
  kunya: string | null;
  laqab: string | null;
  yearBirthHijri: number | null;
  yearDeathHijri: number | null;
  birthplace: string | null;
  primaryResidence: string | null;
  reliabilityGrade: ReliabilityGrade | null;
  reliabilityComment: string | null;
  transmittedCount: number;
  createdAt: string;
}

/** Вариация текста хадиса (matn) из detail endpoint. */
export interface MatnDto {
  id: string;
  textAr: string;
  textRu: string | null;
  textEn: string | null;
  sourceBookId: string | null;
  printedNumber: number | null;
  pageNo: number | null;
  volume: number | null;
  isPrimary: boolean;
  divergenceSummary: string | null;
}

/** HadithResponse (thin) — для списка «передал хадисов». */
export interface HadithSummaryDto {
  id: string;
  primaryBookId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: string;
  sourceId: string | null;
  createdAt: string;
}
