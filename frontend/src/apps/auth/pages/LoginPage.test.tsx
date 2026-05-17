import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router';
import { server } from '@/test/server';
import { useAuthStore } from '@/shared/stores/authStore';
import { waitForApi } from '@/test/asyncHelpers';
import LoginPage from './LoginPage';

const API = 'http://test.local';

function renderLoginAt(initialPath = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/topics" element={<div>topics page</div>} />
        <Route path="/secret" element={<div>secret page</div>} />
        <Route path="/register" element={<div>register page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function resetAuth() {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  useAuthStore.setState({
    user: null,
    accessToken: null,
    isLoading: false,
    initialized: false,
  });
}

describe('LoginPage', () => {
  beforeEach(() => {
    resetAuth();
  });

  it('успешный submit редиректит на /topics', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/login`, async () =>
        HttpResponse.json({
          accessToken: 'tok',
          accessTokenExpiresAt: '2030-01-01T00:00:00Z',
          user: {
            id: 'u1',
            username: 'admin',
            email: 'a@x.com',
            role: 'ADMIN',
          },
        }),
      ),
    );

    renderLoginAt('/login');

    await userEvent.type(screen.getByLabelText(/^Email/), 'a@x.com');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'password1');
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }));

    await waitForApi(() => {
      expect(screen.getByText('topics page')).toBeInTheDocument();
    });
  });

  it('редиректит на ?redirect=URL после login', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/login`, async () =>
        HttpResponse.json({
          accessToken: 'tok',
          accessTokenExpiresAt: '2030-01-01T00:00:00Z',
          user: { id: 'u', username: 'u', email: 'u@x.com', role: 'USER' },
        }),
      ),
    );

    renderLoginAt('/login?redirect=%2Fsecret');

    await userEvent.type(screen.getByLabelText(/^Email/), 'u@x.com');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'password1');
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }));

    await waitForApi(() => {
      expect(screen.getByText('secret page')).toBeInTheDocument();
    });
  });

  it('показывает локализованную 401 ошибку «Неверный email или пароль»', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/login`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/invalid-credentials',
            title: 'Invalid credentials',
            status: 401,
          },
          { status: 401 },
        ),
      ),
    );

    renderLoginAt();

    await userEvent.type(screen.getByLabelText(/^Email/), 'wrong@x.com');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'wrongpass');
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }));

    await waitForApi(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Неверный email или пароль');
    });
  });

  it('кнопка disabled при пустых полях', () => {
    renderLoginAt();
    expect(screen.getByRole('button', { name: 'Войти' })).toBeDisabled();
  });

  it('линк на /register присутствует', () => {
    renderLoginAt();
    expect(screen.getByRole('link', { name: 'Регистрация' })).toHaveAttribute(
      'href',
      '/register',
    );
  });
});
