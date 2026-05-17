import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router';
import { server } from '@/test/server';
import { useAuthStore } from '@/shared/stores/authStore';
import { waitForApi } from '@/test/asyncHelpers';
import RegisterPage from './RegisterPage';

const API = 'http://test.local';

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/topics" element={<div>topics page</div>} />
        <Route path="/login" element={<div>login page</div>} />
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

describe('RegisterPage', () => {
  beforeEach(() => {
    resetAuth();
  });

  it('client-side: password короче 8 - показывает ошибку', async () => {
    renderRegister();
    await userEvent.type(screen.getByLabelText(/^Email/), 'a@x.com');
    await userEvent.type(screen.getByLabelText(/^Имя пользователя/), 'newuser');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'short');
    await userEvent.type(screen.getByLabelText(/^Повторите пароль/), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }));

    expect(
      await screen.findByText('Пароль должен быть не короче 8 символов'),
    ).toBeInTheDocument();
  });

  it('client-side: passwords не совпадают - показывает ошибку', async () => {
    renderRegister();
    await userEvent.type(screen.getByLabelText(/^Email/), 'a@x.com');
    await userEvent.type(screen.getByLabelText(/^Имя пользователя/), 'newuser');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'password123');
    await userEvent.type(screen.getByLabelText(/^Повторите пароль/), 'different456');
    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }));

    expect(await screen.findByText('Пароли не совпадают')).toBeInTheDocument();
  });

  it('server-side: email уже занят - показывает локализованную ошибку', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/register`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/email-already-taken',
            title: 'Email taken',
            status: 409,
          },
          { status: 409 },
        ),
      ),
    );

    renderRegister();
    await userEvent.type(screen.getByLabelText(/^Email/), 'taken@x.com');
    await userEvent.type(screen.getByLabelText(/^Имя пользователя/), 'newuser');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'password123');
    await userEvent.type(screen.getByLabelText(/^Повторите пароль/), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }));

    await waitForApi(() => {
      expect(screen.getByText('Email уже используется')).toBeInTheDocument();
    });
  });

  it('успешная регистрация - редиректит на /topics', async () => {
    server.use(
      http.post(`${API}/api/v1/auth/register`, () =>
        HttpResponse.json(
          {
            accessToken: 'tok',
            accessTokenExpiresAt: '2030-01-01T00:00:00Z',
            user: { id: 'u', username: 'newuser', email: 'a@x.com', role: 'USER' },
          },
          { status: 201 },
        ),
      ),
    );

    renderRegister();
    await userEvent.type(screen.getByLabelText(/^Email/), 'a@x.com');
    await userEvent.type(screen.getByLabelText(/^Имя пользователя/), 'newuser');
    await userEvent.type(screen.getByLabelText(/^Пароль/), 'password123');
    await userEvent.type(screen.getByLabelText(/^Повторите пароль/), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }));

    await waitForApi(() => {
      expect(screen.getByText('topics page')).toBeInTheDocument();
    });
  });
});
