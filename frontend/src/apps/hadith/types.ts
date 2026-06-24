/**
 * Типы графа иснада (Hadith Explorer Phase 3). Бэкенд-контракт
 * GET /api/v1/hadith/hadiths/{id}/sanad-graph. types.ts ещё не
 * regenerated для hadith-домена, поэтому объявлено вручную здесь.
 */

export type NarratorRole = 'PROPHET' | 'COMPANION' | 'NARRATOR' | 'COLLECTOR' | 'VERSION';

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

/**
 * Версия-узел (role='VERSION') — карточка параллельной передачи (конец цепи
 * = запись в сборнике). data у таких узлов null; смысловые поля — в version.
 *
 * type (не interface): как у NarratorData, React Flow v12 требует, чтобы
 * data узла удовлетворяла Record<string, unknown> — это проходит для
 * type-литералов (неявная индекс-сигнатура), но не для interface.
 */
export type VersionInfo = {
  hadithId: string;
  externalId: string;
  collectionSlug: string | null;
  collectionNameAr: string | null;
  collectionNameRu: string | null;
  printedNumber: number | null;
  matnPreview: string | null;
};

export interface SanadGraphNodeDto {
  id: string;
  role: NarratorRole;
  /** null у version-узлов (role='VERSION') — смысловые поля живут в version. */
  data: NarratorData | null;
  /** Заполнен только у version-узлов; null у передатчиков. */
  version: VersionInfo | null;
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

/** Роль передатчика — все роли кроме VERSION (тот рендерится отдельно). */
export type TransmitterRole = Exclude<NarratorRole, 'VERSION'>;

/**
 * Данные узла-передатчика React Flow = NarratorData + role (рендер карточки).
 * role исключает 'VERSION' — это дискриминант union'а SanadGraphNodeData
 * (version-узлы имеют data с бэка null и рендерятся VersionGraphNode).
 */
export type SanadFlowNodeData = NarratorData & { role: TransmitterRole };

/**
 * Данные version-узла React Flow = VersionInfo + role='VERSION'.
 * `isCurrent` — version.hadithId совпал с hadithId страницы (узел «вы здесь»,
 * не-кликабелен). Вычисляется при сборке узлов в SanadGraph.
 */
export type VersionFlowNodeData = VersionInfo & { role: 'VERSION'; isCurrent: boolean };

/** Объединённые данные узла графа: передатчик ИЛИ карточка-версия. */
export type SanadGraphNodeData = SanadFlowNodeData | VersionFlowNodeData;

/**
 * Оценка хадиса учёным (ADR-062 Option B): из `hadith_grades` (JOIN через
 * `hd_hadiths.source_id`), а не из прежнего `metadata.grades`. Структурная
 * форма с authority-FK и enum-grade. `grade` ∈ HadithGradeValue
 * (SAHIH/HASAN/DAIF/MAUDU); `note` = `hadith_grades.comment`.
 */
export interface HadithGrade {
  gradeId: string;
  scholarId: string;
  scholarName: string | null;
  scholarFullName: string | null;
  scholarDeathYearHijri: number | null;
  grade: HadithGradeValue;
  gradeCitation: string | null;
  note: string | null;
}

/** Whitelist оценок хадиса (зеркало backend HadithGradeValue). */
export type HadithGradeValue = 'SAHIH' | 'HASAN' | 'DAIF' | 'MAUDU';

/** Каталог авторитетов (ученые/издатели/...) — для autocomplete оценок. */
export interface AuthorityResponseDto {
  id: string;
  name: string;
  bio: string | null;
  era: string | null;
  madhab: string | null;
  createdAt: string;
  fullName: string | null;
  deathYearHijri: number | null;
  /** Семантическая роль: SCHOLAR/MUHAQQIQ/PUBLISHER/AUTHOR/OTHER. */
  type: string | null;
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

/**
 * Оценка учёного-критика О передатчике (джарх/таʿдиль) из риджаль-книг.
 * `commenter` — критик; `comments` — массив вердиктов (обычно 1, бывает >1);
 * bookName/author/page/volume — атрибуция (напр. تقريب التهذيب · ابن حجر · т.1 с.1218).
 */
export interface NarratorCommentaryDto {
  /** UUID записи — для ADMIN record-hide (курация Фаза 4.b). */
  id: string;
  commenter: string;
  commenterDeathYear: number | null;
  bookName: string | null;
  author: string | null;
  page: number | null;
  volume: number | null;
  comments: string[];
  /** ADMIN скрыл запись (курация 4.b) — обычный читатель её не получает. */
  hiddenByAdmin: boolean;
  /** Причина скрытия (видна только ADMIN). */
  hideReason: string | null;
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
  /** Оценки учёных о передатчике (джарх/таʿдиль) — только в getOne, null в списке. */
  commentaries?: NarratorCommentaryDto[] | null;
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
  /** ADMIN скрыл вариацию (курация Фаза 5) — обычный читатель её не получает. */
  hiddenByAdmin: boolean;
  /** Причина скрытия (видна только ADMIN). */
  hideReason: string | null;
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
  /** UUID записи — для ADMIN record-hide (курация Фаза 4.b). */
  id: string;
  rulerName: string | null;
  rulerDeathYear: number | null;
  rulingText: string | null;
  bookName: string | null;
  page: number | null;
  volume: number | null;
  source: string | null;
  relatedExternalId: string | null;
  /** UUID импортированной параллельной передачи (resolved) — для линка. */
  relatedHadithId: string | null;
  /** Имя сборника параллельной передачи (для бейджа). */
  relatedCollectionNameRu: string | null;
  /** ADMIN скрыл запись (курация 4.b) — обычный читатель её не получает. */
  hiddenByAdmin: boolean;
  /** Причина скрытия (видна только ADMIN). */
  hideReason: string | null;
}

/** Тип толкования: шарх (общий разбор), иляль (скрытые дефекты передачи),
 *  гариб (толкование редкого слова матна). Бэк шлёт одно из трёх; держим
 *  union для дискриминации в UI (группировка по kind), но допускаем
 *  null/неизвестное значение на случай legacy-данных. */
export type ExplanationKind = 'SHARH' | 'ILAL' | 'GHARIB';

/** Шарх / иляль / гариб (kind различает тип). Текст может быть огромным. */
export interface ExplanationDto {
  /** UUID записи — для ADMIN record-hide (курация Фаза 4.b). */
  id: string;
  kind: ExplanationKind | string | null;
  bookName: string | null;
  author: string | null;
  /** Год смерти автора толкования (г.х., nullable) — атрибуция эпохи; курируемое
   *  поле (whitelist author_death_year). Симметрично commenterDeathYear. */
  authorDeathYear: number | null;
  page: number | null;
  volume: number | null;
  text: string | null;
  /** Только GHARIB: редкое слово из матна (заголовок карточки), напр. أَبْعَدَ.
   *  null для SHARH/ILAL и для GHARIB без слова → фолбэк на book/author. */
  reference: string | null;
  /** ADMIN скрыл запись (курация 4.b) — обычный читатель её не получает. */
  hiddenByAdmin: boolean;
  /** Причина скрытия (видна только ADMIN). */
  hideReason: string | null;
}

/** Такхридж/طرق — параллельная передача. resolved → relatedHadithId есть. */
export interface CrossrefDto {
  relatedExternalId: string | null;
  relatedHadithId: string | null;
  /** Номера в печатном издании (распарсены бэком). */
  numbers: string[];
  collectionNameAr: string | null;
  collectionNameRu: string | null;
}

/** Агрегат detail-эндпоинта GET /hadiths/{id}/detail (alminasa-обогащённый). */
export interface HadithDetailDto {
  id: string;
  collectionId: string | null;
  primaryNumber: number | null;
  normalizedMatn: string;
  /** Ось ПРОВЕНАНСА: CANONICAL (Сахихайн) / VARIANT (параллельная). */
  status: string;
  /** Ось ДОСТОВЕРНОСТИ: SAHIH/HASAN/DAIF/MAUDU; null — вердиктов нет.
   *  Выведена бэком эвристикой по рулингам (приближённо). */
  authenticity: string | null;
  sourceId: string | null;
  createdAt: string;
  /** Свой alminasa-id хадиса (напр. "594-1") — для self-проверки вердиктов. */
  externalId: string | null;
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
