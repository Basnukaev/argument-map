/**
 * Backward-compat re-export. Компонент переехал в shared (22.c.f) чтобы
 * переиспользовался library books + topics. Сам компонент - в
 * `@/shared/components/visibility/VisibilityRadioGroup`
 */
export { default } from '@/shared/components/visibility/VisibilityRadioGroup';
export type {
  Visibility,
  TopicVisibility,
} from '@/shared/components/visibility/VisibilityRadioGroup';
