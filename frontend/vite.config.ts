/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    // WSL2 + проект на /mnt/c/* не получает file-system events с DrvFs.
    // Принудительный polling - HMR начинает срабатывать на каждое сохранение
    watch: {
      usePolling: true,
      interval: 300,
    },
    // Проксируем /api и /actuator на backend - same-origin для cookies.
    // Auth-flow Этапа 21.b использует httpOnly refresh cookie с
    // SameSite=Strict, который браузер не пошлёт через cross-origin
    // даже с credentials: 'include'. Backend CORS allowCredentials=false
    // тоже блокировал бы. Через proxy фронт и API живут на одном
    // origin (5173) - cookies работают без специальной конфигурации
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: true,
    // E2E tests (Playwright) живут в /e2e и используют расширение
    // *.spec.ts. Не пускаем vitest туда - там другой runtime (browser),
    // другой API (@playwright/test), tsc/jsdom не справятся
    exclude: ['node_modules', 'dist', 'e2e/**'],
  },
});
