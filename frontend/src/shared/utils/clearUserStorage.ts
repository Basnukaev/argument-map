/**
 * Cleanup user-specific localStorage entries при logout / session-expired.
 *
 * Rationale: на shared машине user A может dismiss onboarding, потом
 * logout. Без cleanup'а следующий user B не получит свой onboarding
 * widget. Keys которые user-scoped по семантике но stored device-wide
 * по реализации - чистятся здесь
 *
 * Не чистим device-level preferences (theme - выбранная пользователем
 * под экран; шрифты / layoutPreset / showEdgeLabels - пер-устройство):
 * - `theme` - наследуется как ambient device setting между users
 * - `app.fontPair` / `app.titleWeight` / `app.density` и пр. -
 *   device-level настройки оформления (FontSettings)
 * - `argmap.layoutPreset` - device pref для productivity (форма графа)
 * - `argmap.showEdgeLabels` - graph viewport pref
 *
 * `auth.user` чистится через persistUser(null) в самом authStore -
 * не дублируем здесь
 */
export function clearUserStorage(): void {
  if (typeof window === 'undefined') return;
  // onboarding_dismissed - per-device cache но семантически per-user
  // (новый user должен получить onboarding flow если не закрыл его сам)
  window.localStorage.removeItem('onboarding_dismissed');
}
