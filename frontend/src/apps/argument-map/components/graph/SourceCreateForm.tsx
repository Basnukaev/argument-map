import { ArrowLeft } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import {
  SOURCE_TYPE_LABEL,
  SOURCE_TYPE_ICON,
  SOURCE_TYPE_HINT,
  SOURCE_TYPE_ORDER,
  type SourceType,
} from '@/apps/argument-map/utils/attachmentTokens';
import AttachFields from '@/apps/argument-map/components/graph/AttachFields';
import { useT } from '@/shared/i18n';

export type Reliability = 'SAHIH' | 'HASAN' | 'DAIF' | '';

export interface CreateForm {
  sourceType: SourceType;
  title: string;
  citation: string;
  reliability: Reliability;
}

export const INITIAL_CREATE_FORM: CreateForm = {
  sourceType: 'BOOK',
  title: '',
  citation: '',
  reliability: '',
};

interface Props {
  form: CreateForm;
  onFormChange: (f: CreateForm | ((prev: CreateForm) => CreateForm)) => void;
  onBack: () => void;
  quote: string;
  context: string;
  location: string;
  onQuoteChange: (v: string) => void;
  onContextChange: (v: string) => void;
  onLocationChange: (v: string) => void;
  submitting: boolean;
}

/**
 * Create-mode AddSourceModal: форма создания нового источника с выбором
 * типа, обязательным title, опциональным citation, reliability для
 * HADITH, плюс AttachFields для метаданных привязки.
 */
function SourceCreateForm({
  form,
  onFormChange,
  onBack,
  quote,
  context,
  location,
  onQuoteChange,
  onContextChange,
  onLocationChange,
  submitting,
}: Props) {
  const t = useT();
  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        icon={ArrowLeft}
        onClick={onBack}
        disabled={submitting}
      >
        {t('source_form.back_to_search')}
      </Button>

      <fieldset disabled={submitting} className="space-y-3">
        <legend className="mb-1 text-xs font-medium text-ink-700">
          {t('source_form.type_legend')}
        </legend>
        <div className="grid grid-cols-5 gap-2">
          {SOURCE_TYPE_ORDER.map((type) => {
            const Icon = SOURCE_TYPE_ICON[type];
            const isSelected = form.sourceType === type;
            return (
              <label
                key={type}
                title={SOURCE_TYPE_HINT[type]}
                className={`flex cursor-pointer flex-col items-center gap-1 rounded-md border p-2 text-center transition-colors ${
                  isSelected
                    ? 'border-accent-500 bg-accent-50/60 ring-1 ring-accent-500/30'
                    : 'border-border-strong hover:bg-ink-50'
                }`}
              >
                <span className="grid h-6 w-6 place-items-center rounded bg-ink-100 text-ink-600">
                  <Icon size={13} aria-hidden="true" />
                </span>
                <input
                  type="radio"
                  name="source-type"
                  value={type}
                  checked={isSelected}
                  onChange={() =>
                    onFormChange((f) => ({
                      ...f,
                      sourceType: type,
                      reliability: type === 'HADITH' ? f.reliability : '',
                    }))
                  }
                  className="sr-only"
                />
                <span className="text-xs font-semibold text-ink-700">
                  {SOURCE_TYPE_LABEL[type]}
                </span>
              </label>
            );
          })}
        </div>

        <div>
          <label
            htmlFor="create-title"
            className="mb-1 block text-xs font-medium text-ink-700"
          >
            {t('source_form.title_label')}
          </label>
          <input
            id="create-title"
            type="text"
            value={form.title}
            onChange={(e) => onFormChange((f) => ({ ...f, title: e.target.value }))}
            required
            maxLength={500}
            placeholder={t('source_form.title_placeholder')}
            className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
          />
        </div>

        <div>
          <label
            htmlFor="create-citation"
            className="mb-1 block text-xs font-medium text-ink-700"
          >
            {t('source_form.citation_label')}
          </label>
          <input
            id="create-citation"
            type="text"
            value={form.citation}
            onChange={(e) => onFormChange((f) => ({ ...f, citation: e.target.value }))}
            maxLength={500}
            placeholder={t('source_form.citation_placeholder')}
            className="block w-full rounded-md border border-border-strong bg-elevated px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
          />
        </div>

        {form.sourceType === 'HADITH' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-ink-700">
              {t('source_form.reliability_label')}
            </label>
            <div className="grid grid-cols-3 gap-2">
              {(['SAHIH', 'HASAN', 'DAIF'] as const).map((rel) => {
                const isSelected = form.reliability === rel;
                return (
                  <label
                    key={rel}
                    className={`flex cursor-pointer items-center justify-center rounded-md border px-2 py-1.5 font-mono text-xs uppercase transition-colors ${
                      isSelected
                        ? 'border-accent-500 bg-accent-50/60 text-accent-700'
                        : 'border-border-strong text-ink-700 hover:bg-ink-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name="reliability"
                      value={rel}
                      checked={isSelected}
                      onChange={() => onFormChange((f) => ({ ...f, reliability: rel }))}
                      className="sr-only"
                    />
                    {rel}
                  </label>
                );
              })}
            </div>
            <p className="mt-1 text-xs text-ink-500">
              {t('source_form.reliability_hint')}
            </p>
          </div>
        )}
      </fieldset>

      <AttachFields
        quote={quote}
        context={context}
        location={location}
        onQuoteChange={onQuoteChange}
        onContextChange={onContextChange}
        onLocationChange={onLocationChange}
        disabled={submitting}
      />
    </>
  );
}

export default SourceCreateForm;
