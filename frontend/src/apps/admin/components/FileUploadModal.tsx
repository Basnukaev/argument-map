import { useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { Upload, FileText, ChevronDown, ChevronRight } from 'lucide-react';
import FormModal from '@/shared/components/ui/FormModal';
import Field from '@/shared/components/ui/Field';
import Select from '@/shared/components/ui/Select';
import { apiPostMultipart, ApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import { toast } from '@/shared/stores/toastStore';
import { useT, useNumberFormat, hasArabicScript } from '@/shared/i18n';
import type { DictKey } from '@/shared/i18n';
import AcademicMetadataFields, {
  EMPTY_ACADEMIC_METADATA,
  parseIntOrNull,
  type AcademicMetadataValues,
} from '@/shared/components/citation/AcademicMetadataFields';

type FileImportResponse = components['schemas']['FileImportResponse'];
type Language = 'ar' | 'ru' | 'en';

const ALL_LANGUAGES: ReadonlyArray<Language> = ['ar', 'ru', 'en'];
const LANGUAGE_LABEL_KEY: Record<Language, DictKey> = {
  ar: 'admin.file_upload.lang_ar',
  ru: 'admin.file_upload.lang_ru',
  en: 'admin.file_upload.lang_en',
};

interface Props {
  open: boolean;
  onClose: () => void;
  /** Опциональный callback после успешной загрузки (для refresh dashboard) */
  onUploaded?: (response: FileImportResponse) => void;
}

/**
 * Admin upload модалка для PDF файлов в library (Этап 16.f).
 * С Этапа 16.g - collapsible секция «Академические данные» (мухаккик /
 * издатель / место / номер издания / годы хиджра/григориан). Если хотя
 * бы одно academic поле заполнено - backend через 13-args
 * BookService.createBook создаёт книгу с FK на справочники (mirror
 * паттерна 2-step flow в AddSourceModal 20.e). Иначе - default путь
 * без academic FK (как раньше в 16.b/f).
 *
 * Поля контракта: file (PDF, ≤50MB) + опциональные title, language,
 * description + 6 academic. При успехе - toast с action «Открыть книгу» +
 * onUploaded callback.
 */
function FileUploadModal({ open, onClose, onUploaded }: Props) {
  const t = useT();
  const navigate = useNavigate();
  const formatNumber = useNumberFormat();
  const inputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [language, setLanguage] = useState<Language>('ar');
  const [description, setDescription] = useState('');
  const [academic, setAcademic] = useState<AcademicMetadataValues>(
    EMPTY_ACADEMIC_METADATA,
  );
  const [academicOpen, setAcademicOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setFile(null);
    setTitle('');
    setLanguage('ar');
    setDescription('');
    setAcademic(EMPTY_ACADEMIC_METADATA);
    setAcademicOpen(false);
    setError(null);
    setSubmitting(false);
    // input.value сбросить чтобы повторный выбор того же файла сработал
    if (inputRef.current) inputRef.current.value = '';
  };

  const handleClose = () => {
    if (submitting) return;
    reset();
    onClose();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null;
    setFile(picked);
    setError(null);
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!file) return;

    setSubmitting(true);
    setError(null);

    const formData = new FormData();
    formData.append('file', file);
    if (title.trim()) formData.append('title', title.trim());
    formData.append('language', language);
    if (description.trim()) formData.append('description', description.trim());
    // 16.g: добавляем только non-empty academic поля. Backend различает
    // отсутствие param vs пустое значение - для optional строк отсутствие
    // оставляет FK null, для integer-полей отсутствие = null
    if (academic.muhaqqiq.trim()) {
      formData.append('muhaqqiqName', academic.muhaqqiq.trim());
    }
    if (academic.publisher.trim()) {
      formData.append('publisherName', academic.publisher.trim());
    }
    if (academic.place.trim()) {
      formData.append('publicationPlaceName', academic.place.trim());
    }
    const edition = parseIntOrNull(academic.edition);
    if (edition != null) {
      formData.append('editionNumber', String(edition));
    }
    const yearHijri = parseIntOrNull(academic.yearHijri);
    if (yearHijri != null) {
      formData.append('publishedYearHijri', String(yearHijri));
    }
    const yearGregorian = parseIntOrNull(academic.yearGregorian);
    if (yearGregorian != null) {
      formData.append('publishedYearGregorian', String(yearGregorian));
    }

    try {
      const response = await apiPostMultipart<FileImportResponse>(
        '/api/v1/library/imports/file',
        formData,
      );
      const pages = response.pageCount ?? 0;
      const bookId = response.bookId;
      toast.success(
        t('admin.file_upload.success_toast').replace('{pages}', formatNumber(pages)),
        bookId
          ? {
              label: t('admin.file_upload.open_book'),
              onClick: () => navigate(`/books/${bookId}`),
            }
          : undefined,
      );
      onUploaded?.(response);
      reset();
      onClose();
    } catch (e) {
      setError(mapErrorMessage(e, t));
      setSubmitting(false);
    }
  };

  const languageOptions = ALL_LANGUAGES.map((lang) => ({
    value: lang,
    label: t(LANGUAGE_LABEL_KEY[lang]),
  }));

  return (
    <FormModal
      open={open}
      onClose={handleClose}
      title={t('admin.file_upload.title')}
      maxWidth="max-w-lg"
      onSubmit={handleSubmit}
      submitting={submitting}
      submitDisabled={!file}
      submitLabel={t('admin.file_upload.submit')}
      submittingLabel={t('admin.file_upload.submitting')}
      submitIcon={Upload}
      error={error}
    >
      <p className="text-xs text-ink-500 leading-snug">
        {t('admin.file_upload.subtitle')}
      </p>

      <Field
        label={t('admin.file_upload.file_label')}
        hint={t('admin.file_upload.file_help')}
        required
      >
        <FilePicker
          file={file}
          inputRef={inputRef}
          onChange={handleFileChange}
          chooseLabel={t('admin.file_upload.choose_file')}
          changeLabel={t('admin.file_upload.change_file')}
        />
      </Field>

      <Field
        label={t('admin.file_upload.field_title')}
        hint={t('admin.file_upload.field_title_hint')}
      >
        <Field.Input
          type="text"
          value={title}
          onChange={(ev) => setTitle(ev.target.value)}
          dir="auto"
          maxLength={500}
          className={hasArabicScript(title) ? 'font-naskh' : ''}
        />
      </Field>

      <Field label={t('admin.file_upload.field_language')}>
        <Select
          value={language}
          onChange={(v) => setLanguage(v as Language)}
          options={languageOptions}
          ariaLabel={t('admin.file_upload.field_language')}
          className="w-full"
        />
      </Field>

      <Field
        label={t('admin.file_upload.field_description')}
      >
        <Field.Textarea
          rows={2}
          value={description}
          onChange={(ev) => setDescription(ev.target.value)}
          dir="auto"
          maxLength={2000}
          className={hasArabicScript(description) ? 'font-naskh' : ''}
        />
      </Field>

      <AcademicSection
        open={academicOpen}
        onToggle={() => setAcademicOpen((v) => !v)}
        values={academic}
        onChange={setAcademic}
        disabled={submitting}
      />
    </FormModal>
  );
}

interface AcademicSectionProps {
  open: boolean;
  onToggle: () => void;
  values: AcademicMetadataValues;
  onChange: (next: AcademicMetadataValues) => void;
  disabled: boolean;
}

/**
 * Сворачиваемая секция academic полей (16.g). По умолчанию свёрнута -
 * базовый сценарий upload не требует ввода тахкика. Toggle через
 * `<button aria-expanded>` для accessibility, иконка ChevronRight/Down
 * визуально + i18n текст toggle меняется по состоянию.
 *
 * Использует shared <AcademicMetadataFields/> - тот же что в
 * BookEditModal и SourceCreateForm (Этап 20.e). Не дублирует.
 */
function AcademicSection({
  open,
  onToggle,
  values,
  onChange,
  disabled,
}: AcademicSectionProps) {
  const t = useT();
  const Icon = open ? ChevronDown : ChevronRight;
  return (
    <div className="rounded-md border border-border bg-ink-50/40 p-3">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center gap-2 text-start text-xs font-semibold text-ink-800 outline-none focus-visible:ring-2 focus-visible:ring-accent-500/30"
      >
        <Icon size={14} className="text-ink-500 shrink-0" aria-hidden />
        <span>
          {open
            ? t('admin.file_upload.academic.hide_section')
            : t('admin.file_upload.academic.show_section')}
        </span>
      </button>
      {open && (
        <div className="mt-3 space-y-2">
          <p className="text-xs text-ink-500">{t('admin.file_upload.academic.help')}</p>
          <AcademicMetadataFields
            values={values}
            onChange={onChange}
            disabled={disabled}
          />
        </div>
      )}
    </div>
  );
}

interface FilePickerProps {
  file: File | null;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  chooseLabel: string;
  changeLabel: string;
}

/**
 * Стилизованный file picker: визуальный label обёртывает скрытый input.
 * Клик на label или Enter/Space (нативное поведение input file) открывает
 * браузерный picker. Файл preview - filename (bdi для LTR-чисел) + size.
 */
function FilePicker({ file, inputRef, onChange, chooseLabel, changeLabel }: FilePickerProps) {
  const formatNumber = useNumberFormat();
  const sizeText = file ? formatBytes(file.size, formatNumber) : null;

  return (
    <div className="space-y-2">
      <label
        className="flex items-center justify-center gap-2 h-20 rounded-md border-2 border-dashed border-ink-200 bg-ink-50 hover:border-accent-500 hover:bg-accent-50/40 cursor-pointer transition-colors focus-within:ring-2 focus-within:ring-accent-500/30 focus-within:border-accent-500"
      >
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,.pdf"
          onChange={onChange}
          className="sr-only"
          aria-label={file ? changeLabel : chooseLabel}
        />
        <FileText size={18} className="text-ink-500" aria-hidden />
        <span className="text-sm font-medium text-ink-700">
          {file ? changeLabel : chooseLabel}
        </span>
      </label>

      {file && (
        <div className="flex items-center justify-between gap-3 px-3 py-2 rounded-sm bg-ok-100 border border-ok-500/30 text-xs text-ok-700">
          <span className="font-medium truncate" dir="auto">
            <bdi>{file.name}</bdi>
          </span>
          {sizeText && (
            <span className="font-mono shrink-0">
              <bdi dir="ltr">{sizeText}</bdi>
            </span>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Форматирует размер файла в KB/MB - локаль-aware числа через
 * useNumberFormat. KB до 1024, дальше MB. Запасной 'B' для совсем
 * мелких. Без greedy precision: 1.4 MB лучше чем 1.42 MB
 */
function formatBytes(bytes: number, formatNumber: (n: number) => string): string {
  if (bytes < 1024) return `${formatNumber(bytes)} B`;
  if (bytes < 1024 * 1024) return `${formatNumber(Math.round(bytes / 1024))} KB`;
  const mb = bytes / (1024 * 1024);
  // toFixed(1) даёт точку - заменяем через формат: округляем до десятой
  // и форматируем как число с десятыми
  const rounded = Math.round(mb * 10) / 10;
  return `${formatNumber(rounded)} MB`;
}

/**
 * Маппит ошибки бэка в локализованные сообщения. Backend Problem Details:
 *  - 413 payload-too-large    → too_large
 *  - 415 unsupported-media-type → wrong_format
 *  - 422 file-import-error     → corrupt_pdf
 * Network / unknown → generic / network
 */
function mapErrorMessage(e: unknown, t: (k: DictKey) => string): string {
  if (e instanceof ApiError) {
    if (e.status === 413 || e.is('payload-too-large')) {
      return t('admin.file_upload.error_too_large');
    }
    if (e.status === 415 || e.is('unsupported-media-type')) {
      return t('admin.file_upload.error_wrong_format');
    }
    if (e.status === 422 || e.is('file-import-error')) {
      return t('admin.file_upload.error_corrupt_pdf');
    }
    return e.problem.detail || e.problem.title || t('admin.file_upload.error_generic');
  }
  if (e instanceof TypeError) {
    // fetch failed / abort / network
    return t('admin.file_upload.error_network');
  }
  return t('admin.file_upload.error_generic');
}

export default FileUploadModal;
