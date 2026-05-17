/**
 * stripTashkeel - утилита удаления арабских диакритических знаков
 * (огласовок / harakat) из текста и ProseMirror JSON-документа.
 *
 * Закрывает gotcha из Этапа 17.0.c «Tashkeel full removal требует
 * runtime text manipulation» - теперь удаление работает реально, не
 * через CSS-placeholder. Используется в {@link RichTextRenderer} при
 * `hideTashkeel=true` (read-only reader). В admin editor НЕ применяется -
 * автор должен видеть оригинал с огласовками.
 *
 * **Unicode range:** `U+064B`-`U+065F` (15 знаков fatha/kasra/damma/
 * sukun/shadda/tanwin/etc) + `U+0670` (superscript alef "khanjariyya").
 * `U+0640` (tatweel) намеренно НЕ удаляется - это не диакритик, а
 * горизонтальное растяжение буквы для каллиграфии. Удаление tatweel -
 * отдельный feature в backlog
 *
 * **Подход (ADR-039 amendment):** трансформация ProseMirror JSON
 * **перед** render через `generateHTML` / Tiptap useEditor. Чистый
 * functional подход - без DOM-walk, без MutationObserver, React-friendly.
 * Reverse - просто пере-render с оригинальным content (toggle обратно)
 */

/**
 * Regex для tashkeel-знаков. Range `ً-ٟ` покрывает все
 * стандартные harakat, `ٰ` - superscript alef (часто встречается
 * в Коране). Tatweel `ـ` намеренно НЕ включён - см. JSDoc выше
 */
const TASHKEEL_PATTERN = /[ً-ٰٟ]/g;

/**
 * Удаляет все tashkeel-знаки из строки. Идемпотентна: повторный вызов
 * на уже-stripped тексте возвращает то же значение
 */
export function stripTashkeelText(text: string): string {
  return text.replace(TASHKEEL_PATTERN, '');
}

/**
 * Минимальный shape ProseMirror text-node для transform. Полная
 * структура - в `@tiptap/core` Node, но для рекурсии нужны только
 * `type` / `text` / `content` поля
 */
interface ProseMirrorNode {
  type?: string;
  text?: string;
  content?: ProseMirrorNode[];
  [key: string]: unknown;
}

/**
 * Рекурсивно проходит ProseMirror JSON-tree и удаляет tashkeel-знаки
 * из каждого text-node. Не мутирует input - возвращает новый объект
 * (структурный клон через map). Marks и attrs сохраняются как есть -
 * tashkeel-mark на нодах остаётся, просто текст становится без огласовок
 *
 * @param doc ProseMirror JSON (любой node: doc / paragraph / text / ...)
 * @param strip если false - возвращает input as-is без модификаций
 *              (для toggle обратно из reader UI)
 */
export function stripTashkeelFromDoc<T>(doc: T, strip: boolean): T {
  if (!strip) return doc;
  return transformNode(doc as unknown) as T;
}

function transformNode(node: unknown): unknown {
  if (Array.isArray(node)) {
    return node.map(transformNode);
  }
  if (node === null || typeof node !== 'object') {
    return node;
  }
  const obj = node as ProseMirrorNode;
  const result: ProseMirrorNode = { ...obj };
  if (obj.type === 'text' && typeof obj.text === 'string') {
    result.text = stripTashkeelText(obj.text);
  }
  if (Array.isArray(obj.content)) {
    result.content = obj.content.map((child) => transformNode(child) as ProseMirrorNode);
  }
  return result;
}
