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
  /** Табака (поколение) из alminasa — фолбэк для generation в панели. */
  tabaqa: string | null;
  /** Verbatim джарх-та'диль из alminasa — фолбэк для reliabilityComment. */
  gradeText: string | null;
  /** Внешний id передатчика (alminasa) — для клик-резолва иснада из текста. */
  externalId: string | null;
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

/** Связь передатчика с другим (ученик / учитель) — сеть передачи. */
export interface NarratorRelationDto {
  relatedNarratorId: string | null;
  relatedName: string | null;
  role: string | null;
  cnt: number;
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
  /** alminasa: табака (поколение) — фолбэк для отсутствующего generation. */
  tabaqa: string | null;
  /** alminasa: verbatim джарх — фолбэк для reliabilityComment. */
  gradeText: string | null;
  /** alminasa: дата рождения прозой. */
  bornOnText: string | null;
  /** alminasa: дата смерти прозой. */
  diedOnText: string | null;
  /** alminasa: место смерти (поле домена). */
  deathPlace: string | null;
  /** Сеть передатчиков — только в narrator-detail (getOne), не в списке. */
  relations: NarratorRelationDto[] | null;
}

/** Вариация текста хадиса (matn) из detail endpoint. */
export interface MatnDto {
  id: string;
  textAr: string;
  textRu: string | null;
  textEn: string | null;
  collectionId: string | null;
  printedNumber: number | null;
  pageNo: number | null;
  volume: number | null;
  isPrimary: boolean;
  divergenceSummary: string | null;
}

/** HadithResponse (thin) — для списка «передал хадисов». */
export interface HadithSummaryDto {
  id: string;
  collectionId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: string;
  sourceId: string | null;
  createdAt: string;
}

/** Печатное издание (alminasa) — название + том/страница. */
export interface EditionDto {
  editionName: string | null;
  page: number | null;
  volume: number | null;
}

/**
 * Вердикт учёного (ruling). `source`='embedded' — вердикт прямо на этот
 * хадис; 'index' с relatedExternalId — вердикт на параллельную передачу.
 */
export interface RulingDto {
  rulerName: string | null;
  rulerDeathYear: number | null;
  rulingText: string | null;
  bookName: string | null;
  page: number | null;
  volume: number | null;
  source: string | null;
  relatedExternalId: string | null;
}

/** Шарх / иляль / гариб (kind различает тип). Текст может быть огромным. */
export interface ExplanationDto {
  kind: string | null;
  bookName: string | null;
  author: string | null;
  page: number | null;
  volume: number | null;
  text: string | null;
}

/** Такхридж/طرق — параллельная передача. resolved → relatedHadithId есть. */
export interface CrossrefDto {
  relatedExternalId: string | null;
  relatedHadithId: string | null;
  note: string | null;
}

/** Агрегат detail-эндпоинта GET /hadiths/{id}/detail (alminasa-обогащённый). */
export interface HadithDetailDto {
  id: string;
  collectionId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  status: string;
  sourceId: string | null;
  createdAt: string;
  hadithType: string | null;
  chapterAr: string | null;
  subChapterAr: string | null;
  fullTextAr: string | null;
  matns: MatnDto[];
  grades: HadithGrade[];
  editions: EditionDto[] | null;
  rulings: RulingDto[] | null;
  explanations: ExplanationDto[] | null;
  crossrefs: CrossrefDto[] | null;
}
