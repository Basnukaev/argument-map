import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import Button from '@/shared/components/ui/Button';
import Field from '@/shared/components/ui/Field';
import AuthShell from '@/apps/auth/components/AuthShell';
import { useAuthStore } from '@/shared/stores/authStore';
import { ApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';

const MIN_PASSWORD = 8;
const MIN_USERNAME = 3;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface FormErrors {
  email?: string;
  username?: string;
  password?: string;
  passwordConfirm?: string;
  generic?: string;
}

/**
 * Registration страница. Client-side валидация перед submit:
 *   - email - regex
 *   - username - не короче 3 символов
 *   - password - не короче 8 (бэк тоже валидирует - double check)
 *   - passwordConfirm - совпадает с password
 *
 * Server-side ошибки маппятся через type-коды Problem Details:
 *   - /errors/email-already-taken
 *   - /errors/username-already-taken
 */
function RegisterPage() {
  const t = useT();
  const navigate = useNavigate();
  const register = useAuthStore((s) => s.register);

  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});

  const validate = (): FormErrors => {
    const next: FormErrors = {};
    if (!EMAIL_RE.test(email.trim())) {
      next.email = t('register.error_email_invalid');
    }
    if (username.trim().length < MIN_USERNAME) {
      next.username = t('register.error_username_short');
    }
    if (password.length < MIN_PASSWORD) {
      next.password = t('register.error_password_short');
    }
    if (password !== passwordConfirm) {
      next.passwordConfirm = t('register.error_passwords_dont_match');
    }
    return next;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const validation = validate();
    if (Object.keys(validation).length > 0) {
      setErrors(validation);
      return;
    }
    setSubmitting(true);
    setErrors({});
    try {
      await register(email.trim(), username.trim(), password);
      navigate('/topics', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.is('email-already-taken')) {
          setErrors({ email: t('register.error_email_taken') });
        } else if (err.is('username-already-taken')) {
          setErrors({ username: t('register.error_username_taken') });
        } else {
          setErrors({
            generic:
              err.problem.detail ?? err.problem.title ?? t('register.error_generic'),
          });
        }
      } else {
        setErrors({ generic: t('register.error_generic') });
      }
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title={t('register.title')}
      subtitle={t('register.subtitle')}
      footer={
        <span>
          {t('register.login_prompt')}{' '}
          <Link
            to="/login"
            className="font-medium text-accent-600 hover:text-accent-700 hover:underline"
          >
            {t('register.login_link')}
          </Link>
        </span>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <Field label={t('register.email')} required error={errors.email}>
          <Field.Input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={t('register.email_placeholder')}
            autoFocus
          />
        </Field>
        <Field label={t('register.username')} required error={errors.username}>
          <Field.Input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder={t('register.username_placeholder')}
          />
        </Field>
        <Field
          label={t('register.password')}
          hint={t('register.password_hint')}
          required
          error={errors.password}
        >
          <Field.Input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <Field
          label={t('register.password_confirm')}
          required
          error={errors.passwordConfirm}
        >
          <Field.Input
            type="password"
            autoComplete="new-password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
          />
        </Field>

        {errors.generic && (
          <div
            role="alert"
            className="rounded-sm border border-err-500 bg-err-50 px-3 py-2 text-sm text-err-700"
          >
            {errors.generic}
          </div>
        )}

        <Button type="submit" disabled={submitting} full size="lg">
          {submitting ? t('register.submitting') : t('register.submit')}
        </Button>
      </form>
    </AuthShell>
  );
}

export default RegisterPage;
