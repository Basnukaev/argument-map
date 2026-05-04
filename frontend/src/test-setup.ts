import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { server } from '@/test/server';

vi.stubEnv('VITE_API_URL', 'http://test.local');
vi.stubEnv('VITE_DEV_USER_ID', '00000000-0000-0000-0000-000000000001');

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
