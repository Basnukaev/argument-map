import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { useAuthStore } from '@/shared/stores/authStore';
import ProtectedRoute from './ProtectedRoute';

function resetAuth(state: Partial<ReturnType<typeof useAuthStore.getState>> = {}) {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  useAuthStore.setState({
    user: null,
    accessToken: null,
    isLoading: false,
    initialized: true,
    ...state,
  });
}

function renderProtected(initialPath = '/topics', requireRole?: 'USER' | 'ADMIN') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path="/topics"
          element={
            <ProtectedRoute {...(requireRole ? { requireRole } : {})}>
              <div>topics page</div>
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute requireRole="ADMIN">
              <div>admin page</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const USER = {
  id: 'u1',
  username: 'user',
  email: 'u@x.com',
  role: 'USER' as const,
};
const ADMIN = {
  id: 'a1',
  username: 'admin',
  email: 'a@x.com',
  role: 'ADMIN' as const,
};

describe('ProtectedRoute', () => {
  beforeEach(() => {
    resetAuth();
  });

  it('показывает loading splash пока initialized=false', () => {
    resetAuth({ initialized: false, isLoading: true });
    renderProtected();
    expect(screen.getByText('Загрузка')).toBeInTheDocument();
    expect(screen.queryByText('topics page')).not.toBeInTheDocument();
  });

  it('редиректит на /login если нет user', () => {
    renderProtected();
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('рендерит children если user есть', () => {
    resetAuth({ user: USER });
    renderProtected();
    expect(screen.getByText('topics page')).toBeInTheDocument();
  });

  it('requireRole=ADMIN: редиректит USER на /topics', () => {
    resetAuth({ user: USER });
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={
              <ProtectedRoute requireRole="ADMIN">
                <div>admin page</div>
              </ProtectedRoute>
            }
          />
          <Route path="/topics" element={<div>topics page</div>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('topics page')).toBeInTheDocument();
    expect(screen.queryByText('admin page')).not.toBeInTheDocument();
  });

  it('requireRole=ADMIN: пропускает ADMIN', () => {
    resetAuth({ user: ADMIN });
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={
              <ProtectedRoute requireRole="ADMIN">
                <div>admin page</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });
});
