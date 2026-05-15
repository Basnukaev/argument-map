import { useEffect, useRef, useState } from 'react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import { apiGetRaw, apiPatchRaw } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';
import { toast } from '@/shared/stores/toastStore';

type BookDetailResponse = components['schemas']['BookDetailResponse'];
type UpdateBookRequest = components['schemas']['UpdateBookRequest'];
type MuhaqqiqResponse = components['schemas']['MuhaqqiqResponse'];
type PublisherResponse = components['schemas']['PublisherResponse'];
type PublicationPlaceResponse = components['schemas']['PublicationPlaceResponse'];

interface Props {
  book: BookDetailResponse;
  onClose: () => void;
  onSaved: (updated: BookDetailResponse) => void;
}

interface SuggestionItem {
  id: string;
  name: string;
  hint?: string;
}

/**
 * Admin модалка для ручной правки academic metadata книги (Этап 20.d).
 * 6 полей: мухаккик / издатель / место издания (с autocomplete по
 * соответствующему справочнику) + номер издания / год хиджры / год
 * григориан (числовые).
 *
 * Backend через PATCH /api/v1/library/books/{id} - findOrCreate по
 * имени делает в сервисе, фронт посылает name string (autocomplete
 * только как UX подсказка для избежания typo-дублей).
 *
 * Семантика clear: пользователь стирает поле до пустой строки →
 * backend получает "" → FK clear to null. Цифровые поля не clear'абельны
 * (acceptable edge case по плану 20.d).
 *
 * Render-pattern: контейнер/body split (см. memory feedback_react_key_remount).
 */
function BookEditModal({ book, onClose, onSaved }: Props) {
  return (
    <BookEditModalBody book={book} onClose={onClose} onSaved={onSaved} />
  );
}

function BookEditModalBody({ book, onClose, onSaved }: Props) {
  const t = useT();

  const [muhaqqiq, setMuhaqqiq] = useState(book.muhaqqiq?.name ?? '');
  const [publisher, setPublisher] = useState(book.publisher?.name ?? '');
  const [place, setPlace] = useState(book.publicationPlace?.name ?? '');
  const [edition, setEdition] = useState(
    book.editionNumber != null ? String(book.editionNumber) : '',
  );
  const [yearHijri, setYearHijri] = useState(
    book.publishedYearHijri != null ? String(book.publishedYearHijri) : '',
  );
  const [yearGregorian, setYearGregorian] = useState(
    book.publishedYearGregorian != null ? String(book.publishedYearGregorian) : '',
  );
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      const initialMuhaqqiq = book.muhaqqiq?.name ?? '';
      const initialPublisher = book.publisher?.name ?? '';
      const initialPlace = book.publicationPlace?.name ?? '';
      // PATCH semantics: null = no change. Передаём в request только
      // изменённые fields. Backend интерпретирует "" как clear, non-empty
      // как replace.
      const body: UpdateBookRequest = {};
      if (muhaqqiq !== initialMuhaqqiq) body.muhaqqiqName = muhaqqiq;
      if (publisher !== initialPublisher) body.publisherName = publisher;
      if (place !== initialPlace) body.publicationPlaceName = place;
      if (parseIntOrNull(edition) !== (book.editionNumber ?? null)) {
        body.editionNumber = parseIntOrNull(edition) ?? undefined;
      }
      if (parseIntOrNull(yearHijri) !== (book.publishedYearHijri ?? null)) {
        body.publishedYearHijri = parseIntOrNull(yearHijri) ?? undefined;
      }
      if (parseIntOrNull(yearGregorian) !== (book.publishedYearGregorian ?? null)) {
        body.publishedYearGregorian = parseIntOrNull(yearGregorian) ?? undefined;
      }

      const updated = await apiPatchRaw<BookDetailResponse>(
        `/api/v1/library/books/${book.id}`,
        body,
      );
      toast.success(t('admin.edit_book.saved'));
      onSaved(updated);
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      toast.error(`${t('admin.edit_book.save_failed')}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={true}
      onClose={onClose}
      title={t('admin.edit_book.title')}
      maxWidth="max-w-2xl"
    >
      <div className="flex flex-col gap-4">
        <AutocompleteRow
          label={t('admin.edit_book.muhaqqiq')}
          hint={t('admin.edit_book.muhaqqiq_hint')}
          value={muhaqqiq}
          onChange={setMuhaqqiq}
          fetchSuggestions={fetchMuhaqqiqSuggestions}
        />
        <AutocompleteRow
          label={t('admin.edit_book.publisher')}
          hint={t('admin.edit_book.publisher_hint')}
          value={publisher}
          onChange={setPublisher}
          fetchSuggestions={fetchPublisherSuggestions}
        />
        <AutocompleteRow
          label={t('admin.edit_book.publication_place')}
          hint={t('admin.edit_book.publication_place_hint')}
          value={place}
          onChange={setPlace}
          fetchSuggestions={fetchPlaceSuggestions}
        />
        <div className="grid grid-cols-3 gap-3">
          <Field label={t('admin.edit_book.edition')}>
            <Field.Input
              type="number"
              min={1}
              max={99}
              value={edition}
              onChange={(e) => setEdition(e.target.value)}
            />
          </Field>
          <Field label={t('admin.edit_book.year_hijri')}>
            <Field.Input
              type="number"
              min={1}
              max={9999}
              value={yearHijri}
              onChange={(e) => setYearHijri(e.target.value)}
            />
          </Field>
          <Field label={t('admin.edit_book.year_gregorian')}>
            <Field.Input
              type="number"
              min={1}
              max={9999}
              value={yearGregorian}
              onChange={(e) => setYearGregorian(e.target.value)}
            />
          </Field>
        </div>
        <div className="flex justify-end gap-2 pt-2 border-t border-ink-100">
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            {t('common.cancel')}
          </Button>
          <Button variant="primary" onClick={handleSave} disabled={saving}>
            {saving ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </div>
    </Modal>
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

function parseIntOrNull(s: string): number | null {
  if (s.trim() === '') return null;
  const n = Number.parseInt(s.trim(), 10);
  return Number.isNaN(n) ? null : n;
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

export default BookEditModal;
