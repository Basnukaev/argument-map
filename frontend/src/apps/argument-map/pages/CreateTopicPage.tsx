import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import Button from '@/shared/components/ui/Button';
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

  const inputClass =
    'block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200';

  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-6 text-3xl font-bold text-gray-900">Создание темы</h1>

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="title" className="mb-1 block text-sm font-medium text-gray-700">
              Название
            </label>
            <input
              id="title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              maxLength={500}
              className={inputClass}
              disabled={submitting}
            />
            {fieldError('title') && (
              <p className="mt-1 text-sm text-red-600">{fieldError('title')}</p>
            )}
          </div>

          <div>
            <label htmlFor="description" className="mb-1 block text-sm font-medium text-gray-700">
              Описание (необязательно)
            </label>
            <textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              maxLength={2000}
              className={inputClass}
              disabled={submitting}
            />
            {fieldError('description') && (
              <p className="mt-1 text-sm text-red-600">{fieldError('description')}</p>
            )}
          </div>

          <div>
            <label htmlFor="rootQuestion" className="mb-1 block text-sm font-medium text-gray-700">
              Корневой вопрос
            </label>
            <textarea
              id="rootQuestion"
              value={rootQuestion}
              onChange={(e) => setRootQuestion(e.target.value)}
              required
              rows={2}
              maxLength={1000}
              className={inputClass}
              disabled={submitting}
            />
            <p className="mt-1 text-xs text-gray-500">Это станет корневым QUESTION-узлом графа</p>
            {fieldError('rootQuestion') && (
              <p className="mt-1 text-sm text-red-600">{fieldError('rootQuestion')}</p>
            )}
          </div>

          {formError && (
            <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800">
              {formError}
            </div>
          )}

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit" disabled={submitting || !title.trim() || !rootQuestion.trim()}>
              {submitting ? 'Создаём' : 'Создать'}
            </Button>
            <Link to="/topics">
              <Button type="button" variant="secondary" disabled={submitting}>
                Отмена
              </Button>
            </Link>
          </div>
        </form>
      </div>
    </main>
  );
}

export default CreateTopicPage;
