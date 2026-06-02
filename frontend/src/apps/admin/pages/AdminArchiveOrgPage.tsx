import { useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  CircleSlash,
  Download,
  FileText,
  Globe,
  Image as ImageIcon,
  Layers,
  Loader2,
  ScanLine,
  Upload,
} from 'lucide-react';
import Header from '@/shared/components/layout/Header';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Field from '@/shared/components/ui/Field';
import { apiGetRaw, apiPostRaw, ApiError, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { hasArabicScript, useT, useNumberFormat, type DictKey } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type Preview = components['schemas']['ArchiveOrgPreview'];
type ImportRequest = components['schemas']['ArchiveOrgImportRequest'];
type ImportResponse = components['schemas']['ArchiveOrgImportResponse'];
type ProvenanceField = components['schemas']['ProvenanceField'];
type VolumeGroup = components['schemas']['VolumeGroup'];
type CoverOption = components['schemas']['CoverOption'];

/** Состояние загрузки превью по URL. 400/404/502 ловим явно по статусу. */
type PreviewState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'success'; data: Preview };

/**
 * Редактируемая форма поля метаданных. Prefilled из preview.value, но
 * редактируема — админ дообогащает то, чего нет в источнике (source==='missing').
 * Ключи совпадают с полями ArchiveOrgImportRequest где это применимо.
 */
interface FormState {
  title: string;
  author: string;
  language: string;
  muhaqqiqName: string;
  publisherName: string;
  placeName: string;
  editionNumber: string;
  yearHijri: string;
  yearGregorian: string;
  description: string;
}

const EMPTY_FORM: FormState = {
  title: '',
  author: '',
  language: '',
  muhaqqiqName: '',
  publisherName: '',
  placeName: '',
  editionNumber: '',
  yearHijri: '',
  yearGregorian: '',
  description: '',
};

/**
 * AdminArchiveOrgPage (route `/admin/archive-org`) — gap-aware импорт книги из
 * archive.org по ссылке.
 *
 * Философия (спека 2026-06-02): вставляешь URL → видишь как item ляжет в наш
 * формат. Импорт заполняет ВСЕ «наши» поля; чего нет в источнике — фронт явно
 * сигналит жёлтым бейджем «нет в источнике, дообогати». Это переиспользуемый
 * gap-enrichment паттерн (тот же PreviewDTO-с-провенансом позже для shamela/
 * sunnah/alminasa).
 *
 * Flow: URL-инпут → GET preview (без записи) → редактируемые метаданные с
 * провенанс-бейджами + список томов (оригинал/OCR) + выбор обложки +
 * test-mode извлечения → POST import → success toast + ссылка на книгу.
 */
function AdminArchiveOrgPage() {
  const t = useT();
  const [url, setUrl] = useState('');
  const [state, setState] = useState<PreviewState>({ kind: 'idle' });

  // Форма + выбор обложки + test-mode живут на уровне страницы, инициализируются
  // когда приходит успешное превью (через ремонт PreviewSection по key).
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [coverKind, setCoverKind] = useState<string>('thumbnail');
  const [coverUrl, setCoverUrl] = useState<string | undefined>(undefined);
  const [extractText, setExtractText] = useState(false);
  const [testModePages, setTestModePages] = useState('');

  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportResponse | null>(null);

  const loadPreview = async () => {
    const trimmed = url.trim();
    if (!trimmed) return;
    setState({ kind: 'loading' });
    setImportResult(null);
    try {
      const data = await apiGetRaw<Preview>(
        `/api/v1/admin/archive-org/preview?url=${encodeURIComponent(trimmed)}`,
      );
      setState({ kind: 'success', data });
      // Инициализируем форму prefilled-значениями из провенанса.
      setForm(formFromPreview(data));
      // Дефолт обложки — первый thumbnail вариант если есть, иначе первый доступный.
      const opts = data.coverOptions ?? [];
      const thumb = opts.find((o) => o.kind === 'thumbnail') ?? opts[0];
      setCoverKind(thumb?.kind ?? 'thumbnail');
      setCoverUrl(thumb?.url ?? undefined);
      setExtractText(false);
      setTestModePages('');
    } catch (e) {
      setState({ kind: 'error', message: formatArchiveError(e, t) });
    }
  };

  const doImport = async () => {
    if (state.kind !== 'success') return;
    setImporting(true);
    try {
      const body: ImportRequest = {
        url: url.trim(),
        title: orUndefined(form.title),
        author: orUndefined(form.author),
        language: orUndefined(form.language),
        description: orUndefined(form.description),
        muhaqqiqName: orUndefined(form.muhaqqiqName),
        publisherName: orUndefined(form.publisherName),
        placeName: orUndefined(form.placeName),
        editionNumber: parseIntOrUndefined(form.editionNumber),
        yearHijri: parseIntOrUndefined(form.yearHijri),
        yearGregorian: parseIntOrUndefined(form.yearGregorian),
        coverKind,
        coverUrl,
        extractText,
        testModePages:
          extractText && testModePages.trim() !== ''
            ? parseIntOrUndefined(testModePages)
            : undefined,
      };
      const res = await apiPostRaw<ImportResponse>('/api/v1/admin/archive-org/import', body);
      setImportResult(res);
      if (res.alreadyExisted) {
        toast.info(t('admin.archiveorg.import_already_existed'));
      } else {
        toast.success(
          t('admin.archiveorg.import_done').replace(
            '{count}',
            String(res.volumesRegistered ?? 0),
          ),
        );
      }
    } catch (e) {
      toast.error(formatArchiveError(e, t));
    } finally {
      setImporting(false);
    }
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1100px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6">
          <div className="mb-1 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
            <Globe size={13} aria-hidden /> {t('admin.archiveorg.eyebrow')}
          </div>
          <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
            {t('admin.archiveorg.title')}
          </h1>
          <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
            {t('admin.archiveorg.subtitle')}
          </p>
        </header>

        {/* URL input row */}
        <section className="mb-6">
          <form
            className="flex flex-col gap-2 sm:flex-row sm:items-end"
            onSubmit={(e) => {
              e.preventDefault();
              void loadPreview();
            }}
          >
            <div className="min-w-0 flex-1">
              <Field label={t('admin.archiveorg.url_label')} hint={t('admin.archiveorg.url_hint')}>
                <Field.Input
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://archive.org/details/fmhji"
                  dir="ltr"
                  inputMode="url"
                />
              </Field>
            </div>
            <div className="shrink-0">
              <Button
                type="submit"
                icon={state.kind === 'loading' ? undefined : Download}
                disabled={url.trim() === '' || state.kind === 'loading'}
              >
                {state.kind === 'loading' && (
                  <Loader2 size={15} className="animate-spin" aria-hidden />
                )}
                {t('admin.archiveorg.load_preview')}
              </Button>
            </div>
          </form>
        </section>

        {state.kind === 'error' && (
          <Card className="mb-5 border-err-500/40 bg-err-100 p-5">
            <div className="flex items-start gap-3 text-err-700">
              <AlertCircle size={20} className="mt-0.5 shrink-0" aria-hidden />
              <div className="text-sm">{state.message}</div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && !state.data.hasPdf && <NoPdfState />}

        {state.kind === 'success' && state.data.hasPdf && (
          <PreviewSection
            // key — пересоздать секцию (и сбросить collapsible-состояние)
            // при загрузке нового item.
            key={state.data.archiveOrgId ?? url}
            data={state.data}
            form={form}
            onFormChange={setForm}
            coverKind={coverKind}
            onCoverChange={(kind, u) => {
              setCoverKind(kind);
              setCoverUrl(u);
            }}
            extractText={extractText}
            onExtractTextChange={setExtractText}
            testModePages={testModePages}
            onTestModePagesChange={setTestModePages}
            importing={importing}
            importResult={importResult}
            onImport={doImport}
          />
        )}
      </div>
    </main>
  );
}

// ====================================================================
//                          Preview section
// ====================================================================

interface PreviewSectionProps {
  data: Preview;
  form: FormState;
  onFormChange: (next: FormState) => void;
  coverKind: string;
  onCoverChange: (kind: string, url: string | undefined) => void;
  extractText: boolean;
  onExtractTextChange: (next: boolean) => void;
  testModePages: string;
  onTestModePagesChange: (next: string) => void;
  importing: boolean;
  importResult: ImportResponse | null;
  onImport: () => void;
}

function PreviewSection({
  data,
  form,
  onFormChange,
  coverKind,
  onCoverChange,
  extractText,
  onExtractTextChange,
  testModePages,
  onTestModePagesChange,
  importing,
  importResult,
  onImport,
}: PreviewSectionProps) {
  const t = useT();
  const update = (patch: Partial<FormState>) => onFormChange({ ...form, ...patch });

  return (
    <div className="flex flex-col gap-6">
      {/* Метаданные с gap-бейджами */}
      <section className="overflow-hidden rounded-lg border border-border bg-elevated">
        <SectionHeader icon={FileText} title={t('admin.archiveorg.section_metadata')} />
        <div className="flex flex-col gap-4 px-4 py-4">
          <ProvenanceInput
            label={t('admin.archiveorg.field_title')}
            field={data.title}
            value={form.title}
            onChange={(v) => update({ title: v })}
          />
          <ProvenanceInput
            label={t('admin.archiveorg.field_author')}
            field={data.author}
            value={form.author}
            onChange={(v) => update({ author: v })}
          />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <ProvenanceInput
              label={t('admin.archiveorg.field_muhaqqiq')}
              field={data.muhaqqiq}
              value={form.muhaqqiqName}
              onChange={(v) => update({ muhaqqiqName: v })}
            />
            <ProvenanceInput
              label={t('admin.archiveorg.field_language')}
              field={data.language}
              value={form.language}
              onChange={(v) => update({ language: v })}
            />
            <ProvenanceInput
              label={t('admin.archiveorg.field_publisher')}
              field={data.publisher}
              value={form.publisherName}
              onChange={(v) => update({ publisherName: v })}
            />
            <ProvenanceInput
              label={t('admin.archiveorg.field_place')}
              field={data.place}
              value={form.placeName}
              onChange={(v) => update({ placeName: v })}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <ProvenanceInput
              label={t('admin.archiveorg.field_edition')}
              field={data.edition}
              value={form.editionNumber}
              onChange={(v) => update({ editionNumber: v })}
              type="number"
            />
            <ProvenanceInput
              label={t('admin.archiveorg.field_year_hijri')}
              field={data.yearHijri}
              value={form.yearHijri}
              onChange={(v) => update({ yearHijri: v })}
              type="number"
            />
            <ProvenanceInput
              label={t('admin.archiveorg.field_year_gregorian')}
              field={data.yearGregorian}
              value={form.yearGregorian}
              onChange={(v) => update({ yearGregorian: v })}
              type="number"
            />
          </div>

          {/* volumes count — read-only provenance, авто из файлов */}
          {data.volumes && (
            <div className="flex items-center gap-2 text-sm text-ink-700">
              <span className="font-semibold text-ink-900">
                {t('admin.archiveorg.field_volumes')}:
              </span>
              <span className="tabular-nums">{data.volumes.value || '—'}</span>
              <FieldProvenanceBadge source={data.volumes.source} />
            </div>
          )}

          {/* rawDescription — collapsible, arabic, для копипасты */}
          {data.rawDescription && <RawDescription text={data.rawDescription} />}
        </div>
      </section>

      {/* Тома / файлы */}
      <section className="overflow-hidden rounded-lg border border-border bg-elevated">
        <SectionHeader icon={Layers} title={t('admin.archiveorg.section_files')} />
        <div className="px-4 py-4">
          <VolumeList groups={data.files ?? []} />
          <p className="mt-3 text-xs text-ink-500">{t('admin.archiveorg.files_auto_note')}</p>
        </div>
      </section>

      {/* Выбор обложки */}
      <section className="overflow-hidden rounded-lg border border-border bg-elevated">
        <SectionHeader icon={ImageIcon} title={t('admin.archiveorg.section_cover')} />
        <div className="px-4 py-4">
          <CoverPicker
            options={data.coverOptions ?? []}
            selectedKind={coverKind}
            onChange={onCoverChange}
          />
        </div>
      </section>

      {/* Test-mode извлечение текста */}
      <section className="overflow-hidden rounded-lg border border-border bg-elevated">
        <SectionHeader icon={ScanLine} title={t('admin.archiveorg.section_extract')} />
        <div className="flex flex-col gap-3 px-4 py-4">
          <label className="flex items-start gap-2.5">
            <input
              type="checkbox"
              checked={extractText}
              onChange={(e) => onExtractTextChange(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 accent-accent-600"
            />
            <span className="text-sm">
              <span className="font-semibold text-ink-900">
                {t('admin.archiveorg.extract_toggle')}
              </span>
              <span className="block text-xs text-ink-500">
                {t('admin.archiveorg.extract_hint')}
              </span>
            </span>
          </label>
          {extractText && (
            <div className="ms-7 max-w-[280px]">
              <Field
                label={t('admin.archiveorg.test_pages_label')}
                hint={t('admin.archiveorg.test_pages_hint')}
              >
                <Field.Input
                  type="number"
                  min={1}
                  max={9999}
                  value={testModePages}
                  onChange={(e) => onTestModePagesChange(e.target.value)}
                  placeholder="5"
                  dir="ltr"
                />
              </Field>
            </div>
          )}
        </div>
      </section>

      {/* Import CTA + result */}
      <section className="rounded-lg border border-border bg-elevated px-4 py-4">
        {importResult ? (
          <ImportResultView result={importResult} />
        ) : (
          <Button icon={importing ? undefined : Download} full disabled={importing} onClick={onImport}>
            {importing && <Loader2 size={15} className="animate-spin" aria-hidden />}
            {importing ? t('admin.archiveorg.importing') : t('admin.archiveorg.import_cta')}
          </Button>
        )}
      </section>
    </div>
  );
}

// ====================================================================
//                          Sub-components
// ====================================================================

interface SectionHeaderProps {
  icon: typeof FileText;
  title: string;
}

function SectionHeader({ icon: Icon, title }: SectionHeaderProps) {
  return (
    <header className="flex items-center gap-2 border-b border-border bg-sunken px-4 py-2.5">
      <Icon size={14} aria-hidden className="text-ink-500" />
      <h2 className="text-sm font-semibold text-ink-900">{title}</h2>
    </header>
  );
}

interface ProvenanceInputProps {
  label: string;
  field: ProvenanceField | undefined;
  value: string;
  onChange: (v: string) => void;
  type?: 'text' | 'number';
}

/**
 * Поле метаданных с провенанс-бейджем: prefilled из preview, редактируемо.
 * Бейдж справа от label показывает откуда значение (archive.org / нет в источнике).
 */
function ProvenanceInput({ label, field, value, onChange, type = 'text' }: ProvenanceInputProps) {
  const arabic = type === 'text' && hasArabicScript(value);
  return (
    <Field label={label}>
      <div className="mb-1 -mt-0.5">
        <FieldProvenanceBadge source={field?.source} />
      </div>
      <Field.Input
        type={type === 'number' ? 'number' : undefined}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        dir={type === 'number' ? 'ltr' : 'auto'}
        className={arabic ? 'font-arabic' : ''}
      />
    </Field>
  );
}

interface FieldProvenanceBadgeProps {
  source: ProvenanceField['source'] | undefined;
}

/**
 * Gap-бейдж: зелёный «из archive.org» когда поле взято из источника,
 * жёлтый «нет в источнике — заполни» когда пусто (source==='missing').
 * Это ключевой visual сигнал gap-aware enrichment.
 */
function FieldProvenanceBadge({ source }: FieldProvenanceBadgeProps) {
  const t = useT();
  if (source === 'archive_org') {
    return (
      <span className="inline-flex items-center gap-1 rounded-sm bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700">
        <CheckCircle2 size={11} aria-hidden /> {t('admin.archiveorg.badge_from_source')}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-sm bg-amber-100 px-1.5 py-0.5 text-[11px] font-semibold text-amber-700">
      <AlertCircle size={11} aria-hidden /> {t('admin.archiveorg.badge_missing')}
    </span>
  );
}

interface RawDescriptionProps {
  text: string;
}

/** Сырое описание из archive.org (арабский HTML-текст) — collapsible для копипасты. */
function RawDescription({ text }: RawDescriptionProps) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const arabic = hasArabicScript(text);
  return (
    <div className="rounded-md border border-border bg-sunken">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-3 py-2 text-start text-sm font-semibold text-ink-700 transition-colors hover:text-ink-900"
      >
        <ChevronDown
          size={15}
          aria-hidden
          className={`transition-transform ${open ? 'rotate-180' : ''}`}
        />
        {t('admin.archiveorg.raw_description')}
      </button>
      {open && (
        <p
          dir={arabic ? 'rtl' : 'auto'}
          className={`max-h-72 overflow-y-auto whitespace-pre-wrap border-t border-border px-3 py-2.5 leading-relaxed text-ink-700 ${
            arabic ? 'font-arabic text-base' : 'text-sm'
          }`}
        >
          {text}
        </p>
      )}
    </div>
  );
}

interface VolumeListProps {
  groups: VolumeGroup[];
}

function VolumeList({ groups }: VolumeListProps) {
  const t = useT();
  if (groups.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border-strong bg-sunken p-6 text-center text-sm text-ink-500">
        {t('admin.archiveorg.files_empty')}
      </div>
    );
  }
  return (
    <ul className="space-y-2">
      {groups.map((g) => (
        <li key={`${g.role}-${g.volumeNo ?? 0}`}>
          <VolumeRow group={g} />
        </li>
      ))}
    </ul>
  );
}

interface VolumeRowProps {
  group: VolumeGroup;
}

function VolumeRow({ group }: VolumeRowProps) {
  const t = useT();
  const formatNumber = useNumberFormat();
  const isCover = group.role === 'cover';
  const label = isCover
    ? t('admin.archiveorg.role_cover')
    : t('admin.archiveorg.volume_label').replace('{n}', String(group.volumeNo ?? '?'));

  const hasOriginal = group.original != null;
  const hasOcr = group.ocr != null;

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-md border border-border bg-sunken px-3 py-2.5">
      <span className="flex items-center gap-1.5 text-sm font-semibold text-ink-900">
        {isCover ? <ImageIcon size={14} aria-hidden /> : <FileText size={14} aria-hidden />}
        {label}
      </span>
      <div className="flex flex-wrap items-center gap-2">
        {hasOriginal && (
          <FileChip
            label={t('admin.archiveorg.variant_original')}
            size={group.original?.size ?? undefined}
            formatNumber={formatNumber}
          />
        )}
        {hasOcr && (
          <FileChip
            label={t('admin.archiveorg.variant_ocr')}
            size={group.ocr?.size ?? undefined}
            formatNumber={formatNumber}
          />
        )}
        {!hasOcr && hasOriginal && (
          <span className="inline-flex items-center gap-1 rounded-sm bg-amber-100 px-1.5 py-0.5 text-[11px] font-semibold text-amber-700">
            <ScanLine size={11} aria-hidden /> {t('admin.archiveorg.scan_only')}
          </span>
        )}
      </div>
    </div>
  );
}

interface FileChipProps {
  label: string;
  size: number | undefined;
  formatNumber: (n: number | undefined | null) => string;
}

function FileChip({ label, size, formatNumber }: FileChipProps) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-sm border border-border bg-elevated px-2 py-0.5 text-xs text-ink-700">
      <span className="font-semibold">{label}</span>
      {size != null && (
        <span className="tabular-nums text-ink-500">{formatBytes(size, formatNumber)}</span>
      )}
    </span>
  );
}

interface CoverPickerProps {
  options: CoverOption[];
  selectedKind: string;
  onChange: (kind: string, url: string | undefined) => void;
}

function CoverPicker({ options, selectedKind, onChange }: CoverPickerProps) {
  const t = useT();
  if (options.length === 0) {
    return <p className="text-sm text-ink-500">{t('admin.archiveorg.cover_none')}</p>;
  }
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
      {options.map((opt) => (
        <CoverOptionView
          key={opt.kind}
          option={opt}
          selected={selectedKind === opt.kind}
          onSelect={() => onChange(opt.kind ?? 'thumbnail', opt.url ?? undefined)}
        />
      ))}
    </div>
  );
}

interface CoverOptionViewProps {
  option: CoverOption;
  selected: boolean;
  onSelect: () => void;
}

function CoverOptionView({ option, selected, onSelect }: CoverOptionViewProps) {
  const t = useT();
  // upload — для MVP заглушка (нет загрузчика файла), disabled с пометкой.
  const isUpload = option.kind === 'upload';

  const labelKey: DictKey =
    option.kind === 'thumbnail'
      ? 'admin.archiveorg.cover_thumbnail'
      : option.kind === 'cover_pdf_page'
        ? 'admin.archiveorg.cover_pdf_page'
        : 'admin.archiveorg.cover_upload';

  return (
    <button
      type="button"
      onClick={isUpload ? undefined : onSelect}
      disabled={isUpload}
      aria-pressed={selected}
      className={`flex flex-col items-center gap-2 rounded-md border p-3 text-center transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
        isUpload
          ? 'cursor-not-allowed border-dashed border-border-strong opacity-60'
          : selected
            ? 'border-accent-600 border-[1.5px] bg-accent-50'
            : 'border-border hover:border-border-strong'
      }`}
    >
      {option.kind === 'thumbnail' && option.url ? (
        <img
          src={option.url}
          alt={t('admin.archiveorg.cover_thumbnail')}
          className="h-28 w-auto rounded-sm border border-border object-cover"
          loading="lazy"
        />
      ) : (
        <span className="grid h-28 w-full place-items-center rounded-sm border border-dashed border-border-strong bg-sunken text-ink-400">
          {option.kind === 'cover_pdf_page' ? (
            <FileText size={24} aria-hidden />
          ) : isUpload ? (
            <Upload size={24} aria-hidden />
          ) : (
            <ImageIcon size={24} aria-hidden />
          )}
        </span>
      )}
      <span className="text-xs font-semibold text-ink-800">{t(labelKey)}</span>
      {isUpload && (
        <span className="text-[10px] text-ink-500">{t('admin.archiveorg.cover_upload_soon')}</span>
      )}
    </button>
  );
}

interface ImportResultViewProps {
  result: ImportResponse;
}

function ImportResultView({ result }: ImportResultViewProps) {
  const t = useT();
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-start gap-2.5 text-emerald-700">
        <CheckCircle2 size={20} className="mt-0.5 shrink-0" aria-hidden />
        <div className="text-sm">
          {result.alreadyExisted
            ? t('admin.archiveorg.result_already_existed')
            : t('admin.archiveorg.result_done')
                .replace('{volumes}', String(result.volumesRegistered ?? 0))
                .replace('{pages}', String(result.pagesExtracted ?? 0))}
        </div>
      </div>
      {result.bookId && (
        <Link
          to={`/books/${result.bookId}`}
          className="inline-flex items-center gap-1.5 self-start rounded-sm bg-accent-600 px-3 py-1.5 text-sm font-medium text-ink-0 transition-colors hover:bg-accent-cta-hover"
        >
          {t('admin.archiveorg.open_book')}
          <ArrowRight size={15} aria-hidden />
        </Link>
      )}
    </div>
  );
}

function NoPdfState() {
  const t = useT();
  return (
    <div className="flex flex-col items-center gap-4 rounded-lg border border-dashed border-border-strong bg-elevated px-6 py-12 text-center">
      <span className="grid h-14 w-14 place-items-center rounded-full bg-warn-100 text-warn-700">
        <CircleSlash size={26} aria-hidden />
      </span>
      <div>
        <h2 className="font-serif text-xl font-semibold text-ink-900">
          {t('admin.archiveorg.no_pdf_title')}
        </h2>
        <p className="mt-1.5 max-w-[480px] text-sm text-ink-500">
          {t('admin.archiveorg.no_pdf_body')}
        </p>
      </div>
    </div>
  );
}

// ====================================================================
//                          Helpers
// ====================================================================

function formFromPreview(data: Preview): FormState {
  return {
    title: data.title?.value ?? '',
    author: data.author?.value ?? '',
    language: data.language?.value ?? '',
    muhaqqiqName: data.muhaqqiq?.value ?? '',
    publisherName: data.publisher?.value ?? '',
    placeName: data.place?.value ?? '',
    editionNumber: data.edition?.value ?? '',
    yearHijri: data.yearHijri?.value ?? '',
    yearGregorian: data.yearGregorian?.value ?? '',
    description: data.rawDescription ?? '',
  };
}

function orUndefined(s: string): string | undefined {
  const trimmed = s.trim();
  return trimmed === '' ? undefined : trimmed;
}

function parseIntOrUndefined(s: string): number | undefined {
  const trimmed = s.trim();
  if (trimmed === '') return undefined;
  const n = Number.parseInt(trimmed, 10);
  return Number.isNaN(n) ? undefined : n;
}

/** Размер файла в KB/MB, локаль-aware числа (зеркалит FileUploadModal). */
function formatBytes(bytes: number, formatNumber: (n: number | undefined | null) => string): string {
  if (bytes < 1024) return `${formatNumber(bytes)} B`;
  if (bytes < 1024 * 1024) return `${formatNumber(Math.round(bytes / 1024))} KB`;
  const mb = Math.round((bytes / (1024 * 1024)) * 10) / 10;
  return `${formatNumber(mb)} MB`;
}

/**
 * Маппит ошибки preview/import в локализованные сообщения по статусу:
 *  400 невалидный URL · 404 item не найден · 502 archive.org недоступен.
 */
function formatArchiveError(e: unknown, t: (k: DictKey) => string): string {
  if (e instanceof ApiError) {
    if (e.status === 400) return e.problem.detail || t('admin.archiveorg.error_invalid_url');
    if (e.status === 404) return t('admin.archiveorg.error_not_found');
    if (e.status === 502) return t('admin.archiveorg.error_unavailable');
  }
  return formatApiError(e, t('admin.archiveorg.error_generic'));
}

export default AdminArchiveOrgPage;
