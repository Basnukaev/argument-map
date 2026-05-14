import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from '@/App';
import { LocaleEffect } from '@/shared/i18n';
import '@/index.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Не найден корневой элемент #root в index.html');
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <LocaleEffect />
      <App />
    </BrowserRouter>
  </StrictMode>,
);
