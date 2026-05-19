import { useEffect, useRef, useState } from 'react';
import Field from '@/shared/components/ui/Field';
import { apiGetRaw } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';

type MuhaqqiqResponse = components['schemas']['MuhaqqiqResponse'];
type PublisherResponse = components['schemas']['PublisherResponse'];
type PublicationPlaceResponse = components['schemas']['PublicationPlaceResponse'];

export interface AcademicMetadataValues {
  muhaqqiq: string;
  publisher: string;
  place: string;
  edition: string;
  yearHijri: string;
  yearGregorian: string;
}

// EMPTY_ACADEMIC_METADATA - initial-value константа для форм; экспорт
// co-located с типом AcademicMetadataValues. HMR warning только dev,
// splitting не оправдан
// eslint-disable-next-line react-refresh/only-export-components
export const EMPTY_ACADEMIC_METADATA: AcademicMetadataValues = {
  muhaqqiq: '',
  publisher: '',
  place: '',
  edition: '',
  yearHijri: '',
  yearGregorian: '',
};

interface SuggestionItem {
  id: string;
  name: string;
  hint?: string;
}

interface Props {
  values: AcademicMetadataValues;
  onChange: (next: AcademicMetadataValues) => void;
  disabled?: boolean;
}

/**
 * Shared компонент для academic-метаданных книги: мухаккик / издатель /
 * место издания (через autocomplete по справочникам) + номер издания /
 * годы хиджра/григориан (числовые).
 *
 * Используется двумя caller'ами:
 * 1. Admin BookEditModal (Этап 20.d) - PATCH /api/v1/library/books/{id}
 * 2. AddSourceModal SourceCreateForm (Этап 20.e) - POST /api/v1/library/books
 *    для manual book entry с findOrCreate
 *
 * Compoonent - чисто controlled: значения как string, parent сам решает
 * как интерпретировать (PATCH "" = clear vs CREATE "" = no FK).
 * findOrCreate-семантика делается на бэке.
 *
 * Suggestion item id скрывается внутри компонента - наружу выдаём только
 * name string per поле. Backend сам разрешит findOrCreate.
 */
function AcademicMetadataFields({ values, onChange, disabled }: Props) {
  const t = useT();

  const update = (patch: Partial<AcademicMetadataValues>) => {
    onChange({ ...values, ...patch });
  };

  return (
    <fieldset disabled={disabled} className="flex flex-col gap-4">
      <AutocompleteRow
        label={t('admin.edit_book.muhaqqiq')}
        hint={t('admin.edit_book.muhaqqiq_hint')}
        value={values.muhaqqiq}
        onChange={(v) => update({ muhaqqiq: v })}
        fetchSuggestions={fetchMuhaqqiqSuggestions}
      />
      <AutocompleteRow
        label={t('admin.edit_book.publisher')}
        hint={t('admin.edit_book.publisher_hint')}
        value={values.publisher}
        onChange={(v) => update({ publisher: v })}
        fetchSuggestions={fetchPublisherSuggestions}
      />
      <AutocompleteRow
        label={t('admin.edit_book.publication_place')}
        hint={t('admin.edit_book.publication_place_hint')}
        value={values.place}
        onChange={(v) => update({ place: v })}
        fetchSuggestions={fetchPlaceSuggestions}
      />
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Field label={t('admin.edit_book.edition')}>
          <Field.Input
            type="number"
            min={1}
            max={99}
            value={values.edition}
            onChange={(e) => update({ edition: e.target.value })}
          />
        </Field>
        <Field label={t('admin.edit_book.year_hijri')}>
          <Field.Input
            type="number"
            min={1}
            max={9999}
            value={values.yearHijri}
            onChange={(e) => update({ yearHijri: e.target.value })}
          />
        </Field>
        <Field label={t('admin.edit_book.year_gregorian')}>
          <Field.Input
            type="number"
            min={1}
            max={9999}
            value={values.yearGregorian}
            onChange={(e) => update({ yearGregorian: e.target.value })}
          />
        </Field>
      </div>
    </fieldset>
  );
}

interface AutocompleteRowProps {
  label: string;
  hint?: string;
  value: string;
  onChange: (v: string) => void;
  fetchSuggestions: (query: string) => Promise<SuggestionItem[]>;
}

/**
 * Inline autocomplete - input + dropdown suggestions. Debounce 250ms через
 * setTimeout. Click на suggestion заполняет input. Без selected id - на
 * save backend через findOrCreate(name) сам разрешит.
 */
function AutocompleteRow({
  label,
  hint,
  value,
  onChange,
  fetchSuggestions,
}: AutocompleteRowProps) {
  const [suggestions, setSuggestions] = useState<SuggestionItem[]>([]);
  const [open, setOpen] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const blurTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (debounceRef.current != null) {
      window.clearTimeout(debounceRef.current);
    }
    if (!value || value.length < 2) {
      // Stale suggestions из предыдущего длинного query - не очищаем здесь
      // (sync setState в effect триггерит eslint), а скрываем computed через
      // `showDropdown` ниже. Это правильное место для UI-логики show/hide.
      return undefined;
    }
    const controller = new AbortController();
    debounceRef.current = window.setTimeout(async () => {
      try {
        const items = await fetchSuggestions(value);
        if (!controller.signal.aborted) setSuggestions(items);
      } catch {
        if (!controller.signal.aborted) setSuggestions([]);
      }
    }, 250);
    return () => {
      controller.abort();
      if (debounceRef.current != null) {
        window.clearTimeout(debounceRef.current);
      }
    };
  }, [value, fetchSuggestions]);

  const handleSelect = (item: SuggestionItem) => {
    onChange(item.name);
    setOpen(false);
  };

  // showDropdown учитывает minimal length value - так stale suggestions
  // не показываются когда пользователь стёр input до 1 символа
  const showDropdown = open && value.length >= 2 && suggestions.length > 0;

  return (
    <Field label={label} hint={hint}>
      <div className="relative">
        <Field.Input
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            // отложенный close, чтобы успел сработать onClick на dropdown
            blurTimerRef.current = window.setTimeout(() => setOpen(false), 150);
          }}
          dir="auto"
        />
        {showDropdown && (
          <ul
            role="listbox"
            className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-sm border border-ink-200 bg-elevated shadow-sh3"
            onMouseDown={(e) => {
              // предотвращаем blur input'а раньше click'а
              e.preventDefault();
              if (blurTimerRef.current != null) {
                window.clearTimeout(blurTimerRef.current);
              }
            }}
          >
            {suggestions.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="block w-full px-3 py-1.5 text-start text-sm text-ink-800 hover:bg-accent-50 hover:text-accent-700"
                  onClick={() => handleSelect(item)}
                  dir="auto"
                >
                  <span>{item.name}</span>
                  {item.hint && (
                    <span className="ms-2 text-xs text-ink-500">{item.hint}</span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Field>
  );
}

async function fetchMuhaqqiqSuggestions(query: string): Promise<SuggestionItem[]> {
  const rows = await apiGetRaw<MuhaqqiqResponse[]>(
    `/api/v1/library/muhaqqiqs?q=${encodeURIComponent(query)}&limit=10`,
  );
  return rows.map((m) => ({
    id: m.id ?? '',
    name: m.name ?? '',
    hint: m.fullName ?? undefined,
  }));
}

async function fetchPublisherSuggestions(query: string): Promise<SuggestionItem[]> {
  const rows = await apiGetRaw<PublisherResponse[]>(
    `/api/v1/library/publishers?q=${encodeURIComponent(query)}&limit=10`,
  );
  return rows.map((p) => ({ id: p.id ?? '', name: p.name ?? '' }));
}

async function fetchPlaceSuggestions(query: string): Promise<SuggestionItem[]> {
  const rows = await apiGetRaw<PublicationPlaceResponse[]>(
    `/api/v1/library/publication-places?q=${encodeURIComponent(query)}&limit=10`,
  );
  return rows.map((p) => ({ id: p.id ?? '', name: p.name ?? '' }));
}

// parseIntOrNull - pure utility, co-located т.к. используется только формами
// в этом модуле. HMR warning только dev, splitting не оправдан
// eslint-disable-next-line react-refresh/only-export-components
export function parseIntOrNull(s: string): number | null {
  if (s.trim() === '') return null;
  const n = Number.parseInt(s.trim(), 10);
  return Number.isNaN(n) ? null : n;
}

export default AcademicMetadataFields;
