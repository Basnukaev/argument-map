import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuthStore, hasRoleAtLeast } from '@/shared/stores/authStore';
import type { AuthRole } from '@/shared/stores/authStore';
import { useT } from '@/shared/i18n';

interface Props {
  children: ReactNode;
  /**
   * Если задано - дополнительная проверка роли через hierarchy.
   * `requireRole='SCHOLAR'` пускает SCHOLAR и ADMIN. `requireRole='ADMIN'` -
   * только ADMIN. Vision 49d Phase A.6 expanded к 4 значениям
   * (USER < STUDENT < SCHOLAR < ADMIN, монотонная иерархия).
   */
  requireRole?: AuthRole;
}

/**
 * Wrapper защищающий route - если auth bootstrap ещё не завершён,
 * показывает loading. Если user отсутствует - редирект на /login
 * с ?redirect=current-path. Если требуется роль и user не подходит
 * по иерархии - редирект на /topics (главная страница) и flash
 * сообщения нет.
 *
 * Используется как обёртка:
 * <ProtectedRoute><MyPage /></ProtectedRoute>
 * <ProtectedRoute requireRole="ADMIN"><AdminPage /></ProtectedRoute>
 * <ProtectedRoute requireRole="SCHOLAR"><HadithGradeEditor /></ProtectedRoute>
 */
function ProtectedRoute({ children, requireRole }: Props) {
  const t = useT();
  const user = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);
  const initialized = useAuthStore((s) => s.initialized);
  const location = useLocation();

  // Bootstrap не закончен - показываем splash чтобы protected route не
  // флешил на /login до того как мы узнаем что user authenticated
  if (isLoading || !initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg">
        <div className="text-sm text-ink-500">{t('auth.bootstrap_loading')}</div>
      </div>
    );
  }

  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  // Hierarchical role guard: requireRole='SCHOLAR' пускает SCHOLAR+ADMIN,
  // requireRole='ADMIN' — только ADMIN. Если actual ниже required в
  // иерархии — silent redirect на /topics (на Этапе 22 был запланирован
  // 403 page, defer)
  if (requireRole && !hasRoleAtLeast(user.role, requireRole)) {
    return <Navigate to="/topics" replace />;
  }

  return <>{children}</>;
}

export default ProtectedRoute;
