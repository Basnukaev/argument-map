package ru.basnukaev.argumentmap.library.shamela.etl;

/**
 * Утилита null-safe парсинга TEXT-значений из SQLite-таблиц shamela.
 *
 * <p>Контекст: shamela хранит большинство колонок (id-FK, type, year,
 * boolean-флаги) как TEXT, а не INTEGER. При чтении встречаются:
 * <ul>
 *   <li>пустая строка {@code ""} - "значение отсутствует"</li>
 *   <li>{@code "99999"} - магическое "год неизвестен" в полях {@code book.date}</li>
 *   <li>{@code "0"}/{@code "1"} - boolean-флаги ({@code is_deleted}, {@code printed})</li>
 *   <li>валидные числа в произвольном формате (с пробелами по краям)</li>
 * </ul>
 *
 * <p>Все методы возвращают {@code null} вместо бросания исключений на
 * невалидных значениях - философия ETL: данные транзитные, отсутствие
 * значения предпочтительнее краша (особенно при инкрементальных
 * обновлениях когда поле может быть устаревшим/частичным).
 */
public final class SqliteValueParser {

    private SqliteValueParser() {
    }

    /**
     * @return {@code Long} если строка - валидное число; {@code null} для
     *         {@code null}, пустой строки, нечисловых значений.
     */
    public static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseIntegerOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Год публикации с обработкой shamela-магии: {@code "99999"} означает
     * "неизвестен" и превращается в {@code null}, чтобы не загрязнять
     * {@code publication_year} sentinel-значениями.
     */
    public static Integer parseYearOrNull(String value) {
        Integer parsed = parseIntegerOrNull(value);
        if (parsed == null || parsed == 99999) {
            return null;
        }
        return parsed;
    }

    /**
     * @return {@code true} для {@code "1"}, {@code false} для {@code "0"},
     *         {@code null} для всего остального (включая пустые строки и
     *         любые "истиноподобные" строки типа {@code "true"} - shamela их
     *         не использует, так что строгий парсинг безопасен).
     */
    public static Boolean parseBoolOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return switch (trimmed) {
            case "1" -> Boolean.TRUE;
            case "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Проверка tombstone-флага {@code is_deleted}: только {@code "1"} - это
     * "удалено". Любое иное значение (включая {@code null}, пустую строку,
     * {@code "0"}) трактуется как "запись актуальна".
     */
    public static boolean isDeletedFlag(String value) {
        return value != null && "1".equals(value.trim());
    }
}
