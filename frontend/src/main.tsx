import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from '@/App';
import { LocaleEffect } from '@/shared/i18n';
import { ThemeEffect } from '@/shared/components/ThemeEffect';
import { FontPairEffect } from '@/shared/components/FontPairEffect';
import { PreferencesEffect } from '@/shared/components/PreferencesEffect';
import { installAuthBridge } from '@/shared/api/authBridge';
import '@/index.css';

// Подключаем authStore к apiClient interceptor (Bearer + refresh-on-401).
// Делаем ДО рендера приложения чтобы первые API-запросы уже знали про
// access token. Не подключается в тестах - там apiClient legacy mode
installAuthBridge();

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Не найден корневой элемент #root в index.html');
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <LocaleEffect />
      <ThemeEffect />
      <FontPairEffect />
      <PreferencesEffect />
      <App />
    </BrowserRouter>
  </StrictMode>,
);
