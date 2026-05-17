package ru.basnukaev.argumentmap.web.dto;

/**
 * Простой helper для парсинга query-параметров {@code ?page=&size=}
 * на GET-list endpoints. Без Spring Data Pageable - на проекте JDBC,
 * не нужны лишние зависимости.
 *
 * <p>Default: page=0, size=20. Max size=100 (защита от {@code ?size=10000}
 * запросов). Отрицательные значения сбрасываются в default.
 */
public record PageRequest(int page, int size, int offset) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageRequest from(Integer page, Integer size) {
        int p = (page != null && page >= 0) ? page : DEFAULT_PAGE;
        int s;
        if (size == null || size <= 0) {
            s = DEFAULT_SIZE;
        } else if (size > MAX_SIZE) {
            s = MAX_SIZE;
        } else {
            s = size;
        }
        return new PageRequest(p, s, p * s);
    }
}
