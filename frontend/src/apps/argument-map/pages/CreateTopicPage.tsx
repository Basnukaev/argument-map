import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { Lightbulb, Sparkles } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import Header from '@/shared/components/layout/Header';
import { apiPost, ApiError } from '@/shared/api/client';

type ValidationError = { field: string; message: string };

function CreateTopicPage() {
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [rootQuestion, setRootQuestion] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ValidationError[]>([]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);
    setFieldErrors([]);

    try {
      const created = await apiPost('/api/v1/topics', {
        title: title.trim(),
        description: description.trim() || undefined,
        rootQuestion: rootQuestion.trim(),
      });
      const newId = created?.id;
      if (newId) {
        navigate(`/topics/${newId}`);
      } else {
        navigate('/topics');
      }
    } catch (e: unknown) {
      if (e instanceof ApiError) {
        if (e.problem.errors && e.problem.errors.length > 0) {
          setFieldErrors(e.problem.errors);
        } else {
          setFormError(e.problem.detail ?? e.problem.title);
        }
      } else if (e instanceof Error) {
        setFormError(e.message);
      } else {
        setFormError('Не удалось создать тему');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function fieldError(name: string): string | undefined {
    return fieldErrors.find((e) => e.field === name)?.message;
  }

  return (
    <main className="min-h-screen bg-bg">
      <Header />

      <div className="mx-auto max-w-[1100px] px-6 py-6">
        <div className="mb-6">
          <h1 className="text-xl font-bold tracking-tight text-ink-900">
            Создание темы
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            Корневой вопрос становится отправной точкой графа
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_300px]">
          <form
            onSubmit={handleSubmit}
            noValidate
            className="rounded-lg border border-border bg-elevated p-6 shadow-sh1"
          >
            <div className="flex flex-col gap-5">
              <Field
                label="Название"
                hint="Краткая формулировка темы дискуссии"
                required
                error={fieldError('title')}
              >
                <Field.Input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                  maxLength={500}
                  disabled={submitting}
                />
                <Field.Meta left={`${title.length} / 500`} />
              </Field>

              <Field
                label="Описание"
                hint="Необязательно. Поможет другим понять контекст"
                error={fieldError('description')}
              >
                <Field.Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  maxLength={2000}
                  disabled={submitting}
                />
                <Field.Meta left={`${description.length} / 2000`} />
              </Field>

              <Field
                label="Корневой вопрос"
                hint="Это станет корневым QUESTION-узлом графа"
                required
                error={fieldError('rootQuestion')}
              >
                <Field.Textarea
                  value={rootQuestion}
                  onChange={(e) => setRootQuestion(e.target.value)}
                  required
                  rows={2}
                  maxLength={1000}
                  disabled={submitting}
                />
                <Field.Meta left={`${rootQuestion.length} / 1000`} />
              </Field>

              {formError && (
                <div className="rounded-sm border border-err-500/40 bg-err-100 p-3 text-sm text-err-700">
                  {formError}
                </div>
              )}

              <div className="flex items-center gap-3 border-t border-border pt-4">
                <Button
                  type="submit"
                  icon={Sparkles}
                  disabled={submitting || !title.trim() || !rootQuestion.trim()}
                >
                  {submitting ? 'Создаём' : 'Создать'}
                </Button>
                <Link to="/topics">
                  <Button type="button" variant="ghost" disabled={submitting}>
                    Отмена
                  </Button>
                </Link>
              </div>
            </div>
          </form>

          <aside className="lg:sticky lg:top-6 lg:self-start">
            <div className="rounded-lg border border-border bg-paper p-5">
              <div className="mb-3 flex items-center gap-2 text-ink-700">
                <Lightbulb size={16} className="text-accent-600" aria-hidden />
                <span className="text-xs font-semibold uppercase tracking-wider">
                  Совет
                </span>
              </div>
              <p className="text-sm leading-relaxed text-ink-700">
                Хороший корневой вопрос - <strong>предметный</strong>,{' '}
                <strong>конкретный</strong> и оставляет место для разных
                ответов
              </p>
              <p className="mt-3 text-xs italic text-ink-500">
                Например: «Допустимо ли совершать аль-маулид?» Не «Что такое
                маулид?»
              </p>
            </div>
          </aside>
        </div>
      </div>
    </main>
  );
}

export default CreateTopicPage;
