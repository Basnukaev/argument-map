import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { useAuthStore } from '@/shared/stores/authStore';
import { usePaletteStore } from '@/shared/stores/paletteStore';

// Мокаем тяжёлые lazy pages - в этих тестах содержимое страниц не важно
vi.mock('@/apps/argument-map/pages/TopicGraphPage', () => ({
  default: () => <div>TopicGraph</div>,
}));
vi.mock('@/apps/argument-map/pages/TopicListPage', () => ({
  default: () => <div>TopicList</div>,
}));
vi.mock('@/apps/argument-map/pages/CreateTopicPage', () => ({
  default: () => <div>CreateTopic</div>,
}));
vi.mock('@/apps/auth/pages/LoginPage', () => ({
  default: () => <div>Login</div>,
}));
vi.mock('@/apps/auth/pages/RegisterPage', () => ({
  default: () => <div>Register</div>,
}));
vi.mock('@/apps/library/pages/BookListPage', () => ({
  default: () => <div>Books</div>,
}));
vi.mock('@/apps/library/pages/BookReaderPage', () => ({
  default: () => <div>BookReader</div>,
}));
vi.mock('@/apps/admin/pages/AdminShamelaPage', () => ({
  default: () => <div>AdminShamela</div>,
}));
vi.mock('@/apps/admin/pages/AdminPageEditorPage', () => ({
  default: () => <div>AdminPageEditor</div>,
}));
vi.mock('@/apps/admin/pages/AdminAuditPage', () => ({
  default: () => <div>AdminAudit</div>,
}));
vi.mock('@/apps/qa/pages/QuestionListPage', () => ({
  default: () => <div>QuestionList</div>,
}));
vi.mock('@/apps/qa/pages/CreateQuestionPage', () => ({
  default: () => <div>CreateQuestion</div>,
}));
vi.mock('@/apps/qa/pages/QuestionDetailPage', () => ({
  default: () => <div>QuestionDetail</div>,
}));
vi.mock('@/apps/settings/pages/SettingsPage', () => ({
  default: () => <div>Settings</div>,
}));
vi.mock('@/shared/components/citation/SourceDetailPanel', () => ({
  default: () => null,
}));
vi.mock('@/shared/components/onboarding/OnboardingChecklist', () => ({
  default: () => null,
}));

// Импортируем App после моков
import App from './App';

// scrollIntoView не реализован в jsdom — CommandPalette вызывает его
// при открытии. Мокаем на прототипе чтобы избежать TypeError в stderr.
HTMLElement.prototype.scrollIntoView = vi.fn();

/** Хелпер: рендерит App с MemoryRouter на заданный route */
function renderApp(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App - Alt+K Command Palette guard', () => {
  beforeEach(() => {
    // MSW: заглушить /auth/me чтобы loadCurrentUser не падал
    server.use(
      http.get('http://test.local/api/v1/auth/me', () =>
        HttpResponse.json(null, { status: 401 }),
      ),
      http.post('http://test.local/api/v1/auth/refresh', () =>
        HttpResponse.json(null, { status: 401 }),
      ),
    );
    // Сбросить stores
    useAuthStore.setState({ initialized: true, user: null, accessToken: null, isLoading: false });
    usePaletteStore.setState({ open: false });
  });

  it('Alt+K не открывает palette на /login', async () => {
    const user = userEvent.setup();
    renderApp('/login');

    expect(screen.queryByRole('dialog', { name: /palette|команды/i })).not.toBeInTheDocument();

    await user.keyboard('{Alt>}k{/Alt}');

    // Palette должна остаться закрытой
    expect(usePaletteStore.getState().open).toBe(false);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('Alt+K не открывает palette на /register', async () => {
    const user = userEvent.setup();
    renderApp('/register');

    await user.keyboard('{Alt>}k{/Alt}');

    expect(usePaletteStore.getState().open).toBe(false);
  });

  it('Alt+K открывает palette на /topics (не auth страница)', async () => {
    // Для /topics нужен авторизованный пользователь (ProtectedRoute)
    useAuthStore.setState({
      initialized: true,
      user: { id: '1', username: 'test', email: 'test@test.com', role: 'USER' },
      accessToken: 'token',
      isLoading: false,
    });

    const user = userEvent.setup();
    renderApp('/topics');

    await user.keyboard('{Alt>}k{/Alt}');

    expect(usePaletteStore.getState().open).toBe(true);
  });
});
