import { useState } from 'react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import AcademicMetadataFields, {
  parseIntOrNull,
  type AcademicMetadataValues,
} from '@/shared/components/citation/AcademicMetadataFields';
import { apiPatchRaw } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';
import { toast } from '@/shared/stores/toastStore';

type BookDetailResponse = components['schemas']['BookDetailResponse'];
type UpdateBookRequest = components['schemas']['UpdateBookRequest'];

interface Props {
  book: BookDetailResponse;
  onClose: () => void;
  onSaved: (updated: BookDetailResponse) => void;
}

/**
 * Admin модалка для ручной правки academic metadata книги (Этап 20.d).
 * 6 полей через shared <AcademicMetadataFields/>. Backend через PATCH
 * /api/v1/library/books/{id} - findOrCreate по имени делает в сервисе,
 * фронт посылает name string (autocomplete только как UX подсказка для
 * избежания typo-дублей).
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

  const initialValues: AcademicMetadataValues = {
    muhaqqiq: book.muhaqqiq?.name ?? '',
    publisher: book.publisher?.name ?? '',
    place: book.publicationPlace?.name ?? '',
    edition: book.editionNumber != null ? String(book.editionNumber) : '',
    yearHijri: book.publishedYearHijri != null ? String(book.publishedYearHijri) : '',
    yearGregorian:
      book.publishedYearGregorian != null ? String(book.publishedYearGregorian) : '',
  };

  const [values, setValues] = useState<AcademicMetadataValues>(initialValues);
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      // PATCH semantics: null = no change. Передаём в request только
      // изменённые fields. Backend интерпретирует "" как clear, non-empty
      // как replace.
      const body: UpdateBookRequest = {};
      if (values.muhaqqiq !== initialValues.muhaqqiq) body.muhaqqiqName = values.muhaqqiq;
      if (values.publisher !== initialValues.publisher) body.publisherName = values.publisher;
      if (values.place !== initialValues.place) body.publicationPlaceName = values.place;
      if (parseIntOrNull(values.edition) !== (book.editionNumber ?? null)) {
        body.editionNumber = parseIntOrNull(values.edition) ?? undefined;
      }
      if (parseIntOrNull(values.yearHijri) !== (book.publishedYearHijri ?? null)) {
        body.publishedYearHijri = parseIntOrNull(values.yearHijri) ?? undefined;
      }
      if (parseIntOrNull(values.yearGregorian) !== (book.publishedYearGregorian ?? null)) {
        body.publishedYearGregorian = parseIntOrNull(values.yearGregorian) ?? undefined;
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
        <AcademicMetadataFields
          values={values}
          onChange={setValues}
          disabled={saving}
        />
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

export default BookEditModal;
