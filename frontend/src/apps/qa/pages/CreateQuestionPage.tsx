import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import Header from '@/shared/components/layout/Header';
import { apiPostRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type Question = components['schemas']['QuestionResponse'];

const TITLE_MAX = 500;
const BODY_MAX = 10000;

function CreateQuestionPage() {
  const t = useT();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const titleTrimmed = title.trim();
  const canSubmit = titleTrimmed.length > 0 && titleTrimmed.length <= TITLE_MAX && !submitting;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      const created = await apiPostRaw<Question>(
        '/api/v1/questions',
        { title: titleTrimmed, body: body.trim() || null },
      );
      toast.success(t('qa.create.success'));
      navigate(`/qa/${created.id}`);
    } catch (e) {
      const message =
        e instanceof ApiError
          ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
          : e instanceof Error
            ? e.message
            : 'failed';
      toast.error(message);
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      <div className="mx-auto max-w-2xl px-6 py-6">
        <div className="mb-6">
          <Link
            to="/qa"
            className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-700"
          >
            <ArrowLeft size={14} aria-hidden />
            {t('qa.create.back')}
          </Link>
          <h1 className="mt-2 text-xl font-bold tracking-tight text-ink-900">
            {t('qa.create.title')}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            {t('qa.create.subtitle')}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field
            label={t('qa.create.field_title')}
            hint={t('qa.create.field_title_hint')}
            required
          >
            <Field.Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={TITLE_MAX}
              dir="auto"
              autoFocus
            />
            <Field.Meta
              left={`${titleTrimmed.length} / ${TITLE_MAX}`}
            />
          </Field>

          <Field
            label={t('qa.create.field_body')}
            hint={t('qa.create.field_body_hint')}
          >
            <Field.Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              maxLength={BODY_MAX}
              rows={6}
              dir="auto"
            />
            <Field.Meta
              left={`${body.length} / ${BODY_MAX}`}
            />
          </Field>

          <div className="flex justify-end gap-2 pt-2">
            <Link to="/qa">
              <Button type="button" variant="ghost" disabled={submitting}>
                {t('common.cancel')}
              </Button>
            </Link>
            <Button type="submit" variant="primary" disabled={!canSubmit}>
              {submitting ? t('common.saving') : t('qa.create.submit')}
            </Button>
          </div>
        </form>
      </div>
    </main>
  );
}

export default CreateQuestionPage;
