import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft, Lightbulb } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import Header from '@/shared/components/layout/Header';
import { apiPostRaw, ApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { sanitizePageHtml } from '@/shared/components/reader/utils';
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
      <div className="mx-auto max-w-[1100px] px-3 py-6 sm:px-6 sm:py-8">
        <header className="mb-6">
          <Link
            to="/qa"
            className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-700"
          >
            <ArrowLeft size={14} aria-hidden />
            {t('qa.create.back')}
          </Link>
          <div className="mb-1 mt-3 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
            {t('qa.create.eyebrow')}
          </div>
          <h1 className="font-serif text-[28px] font-semibold leading-tight tracking-tight text-ink-900">
            {t('qa.create.title')}
          </h1>
          <p className="mt-1.5 max-w-[680px] text-sm text-ink-500">
            {t('qa.create.subtitle')}
          </p>
        </header>

        {/* 2-column layout с hint panel справа - design-system pattern,
            parity с CreateTopicPage. На <lg экранах collapse в стопку */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_300px]">
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

          <aside className="lg:sticky lg:top-6 lg:self-start">
            <div className="rounded-lg border border-border bg-paper p-5">
              <div className="mb-3 flex items-center gap-2 text-ink-700">
                <Lightbulb size={16} className="text-accent-600" aria-hidden />
                <span className="text-xs font-semibold uppercase tracking-wider">
                  {t('qa.create.hint_eyebrow')}
                </span>
              </div>
              <p
                className="text-sm leading-relaxed text-ink-700"
                dangerouslySetInnerHTML={{ __html: sanitizePageHtml(t('qa.create.hint_body')) }}
              />
              <p className="mt-3 text-xs italic text-ink-500">
                {t('qa.create.hint_example')}
              </p>
            </div>
          </aside>
        </div>
      </div>
    </main>
  );
}

export default CreateQuestionPage;
