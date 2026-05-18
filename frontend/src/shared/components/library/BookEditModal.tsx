import { useState } from 'react';
import { Users } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import VisibilityRadioGroup, {
  type Visibility,
} from '@/shared/components/visibility/VisibilityRadioGroup';
import BookMembersModal from '@/shared/components/library/BookMembersModal';
import AcademicMetadataFields, {
  parseIntOrNull,
  type AcademicMetadataValues,
} from '@/shared/components/citation/AcademicMetadataFields';
import { apiPatchRaw, ApiError, formatApiError } from '@/shared/api/client';
import { formatPermissionError } from '@/shared/api/permissionErrors';
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
 * 22.c.f: добавлена visibility секция (radio group) + кнопка управления
 * участниками при SHARED. Smen visibility - отдельный PATCH endpoint
 * `/visibility`, в отличие от academic metadata. Сохраняем оба за один
 * Save - independent calls (visibility отправляется только если changed)
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

  const initialVisibility: Visibility = (book.visibility ?? 'PRIVATE') as Visibility;

  const [values, setValues] = useState<AcademicMetadataValues>(initialValues);
  const [visibility, setVisibility] = useState<Visibility>(initialVisibility);
  const [saving, setSaving] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      // 1) Academic metadata PATCH - только если что-то изменено
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

      let updated: BookDetailResponse = book;
      const hasMetadataChange = Object.keys(body).length > 0;
      if (hasMetadataChange) {
        updated = await apiPatchRaw<BookDetailResponse>(
          `/api/v1/library/books/${book.id}`,
          body,
        );
      }

      // 2) Visibility PATCH - только если изменена. Отдельный endpoint
      // потому что меняется через assertIsBookOwner (а не assertCanWriteBook
      // как для metadata) - семантически другая операция
      if (visibility !== initialVisibility) {
        updated = await apiPatchRaw<BookDetailResponse>(
          `/api/v1/library/books/${book.id}/visibility`,
          { visibility },
        );
      }

      if (!hasMetadataChange && visibility === initialVisibility) {
        // nothing changed - close silently
        onClose();
        return;
      }

      toast.success(t('admin.edit_book.saved'));
      onSaved(updated);
      onClose();
    } catch (err) {
      const permMsg =
        err instanceof ApiError ? formatPermissionError(err, t) : null;
      toast.error(
        `${t('admin.edit_book.save_failed')}: ${permMsg ?? formatApiError(err, t('common.error'))}`,
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
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

          <div className="border-t border-ink-100 pt-4">
            <Field
              label={t('book.visibility.field_label')}
              hint={t('book.visibility.field_hint')}
            >
              <VisibilityRadioGroup
                value={visibility}
                onChange={setVisibility}
                disabled={saving}
                labelPrefix="book.visibility"
              />
            </Field>
            {visibility === 'SHARED' && book.id && (
              <div className="mt-3">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  icon={Users}
                  onClick={() => setMembersOpen(true)}
                  disabled={saving}
                >
                  {t('book.members.manage_button')}
                </Button>
              </div>
            )}
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
      {membersOpen && book.id && (
        <BookMembersModal
          open={membersOpen}
          bookId={book.id}
          ownerUserId={book.createdBy}
          onClose={() => setMembersOpen(false)}
        />
      )}
    </>
  );
}

export default BookEditModal;
