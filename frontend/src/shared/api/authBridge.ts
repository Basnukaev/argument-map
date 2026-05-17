import { _attachAuthAccessor } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';

/**
 * Связывает apiClient interceptor с authStore. Делаем через отдельный
 * модуль чтобы избежать circular dependency:
 *
 *   authStore.ts → import { API_BASE_URL, ApiError } from client.ts
 *   client.ts → НЕ импортирует authStore напрямую (только через accessor)
 *   authBridge.ts → подключает оба и инициализируется в main.tsx
 *
 * Вызывается один раз на старте приложения (main.tsx). В тестах bridge
 * не подключается - apiClient работает в legacy mode (без Bearer headers,
 * без 401 retry) что подходит для unit-тестов где auth не в фокусе.
 * Тесты authStore + apiClient interceptor сами вызывают _attachAuthAccessor
 * с mock'ом
 */
export function installAuthBridge(): void {
  _attachAuthAccessor({
    getAccessToken: () => useAuthStore.getState().accessToken,
    refresh: () => useAuthStore.getState().refreshAccessToken(),
    clearSession: () => {
      useAuthStore.getState()._setSession(null, null);
    },
  });
}
