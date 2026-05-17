import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import AuthShell from '@/apps/auth/components/AuthShell';
import { useAuthStore } from '@/shared/stores/authStore';
import { ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';

/**
 * Login страница. После успеха - redirect либо на ?redirect=URL
 * (если ProtectedRoute перенаправил сюда), либо на /topics (default
 * landing).
 *
 * Локализация ошибок: 401 → «Неверный email или пароль»,
 * остальные → fallback generic. Server-side ошибки приходят в формате
 * Problem Details, type-коды:
 *   - /errors/invalid-credentials → 401
 *   - /errors/user-disabled → 403
 */
function LoginPage() {
  const t = useT();
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const redirectTo = new URLSearchParams(location.search).get('redirect') ?? '/topics';

  const canSubmit = email.trim().length > 0 && password.length > 0 && !submitting;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setError(t('login.error_invalid'));
        } else if (err.status === 403) {
          setError(t('login.error_disabled'));
        } else {
          setError(err.problem.detail ?? err.problem.title ?? t('login.error_generic'));
        }
      } else {
        setError(t('login.error_generic'));
      }
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title={t('login.title')}
      subtitle={t('login.subtitle')}
      footer={
        <span>
          {t('login.register_prompt')}{' '}
          <Link
            to="/register"
            className="font-medium text-accent-600 hover:text-accent-700 hover:underline"
          >
            {t('login.register_link')}
          </Link>
        </span>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Field label={t('login.email')} required>
          <Field.Input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={t('login.email_placeholder')}
            autoFocus
          />
        </Field>
        <Field label={t('login.password')} required>
          <Field.Input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={t('login.password_placeholder')}
          />
        </Field>

        {error && (
          <div
            role="alert"
            className="rounded-sm border border-err-500 bg-err-50 px-3 py-2 text-sm text-err-700"
          >
            {error}
          </div>
        )}

        <Button type="submit" disabled={!canSubmit} full size="lg">
          {submitting ? t('login.submitting') : t('login.submit')}
        </Button>
      </form>
    </AuthShell>
  );
}

export default LoginPage;
