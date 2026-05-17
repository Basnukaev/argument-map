package ru.basnukaev.argumentmap.web.dto;

import java.util.List;

/**
 * Универсальная обёртка для пагинированных GET-list ответов.
 * Содержит элементы текущей страницы + метаданные навигации.
 *
 * <p>Используется всеми list endpoints с pagination ({@code /sources},
 * {@code /authorities}, {@code /topics}, {@code /library/books},
 * {@code /questions}). Поля page/size echo'ятся из запроса (после
 * clamp/default-нормализации); totalElements/totalPages/hasNext/hasPrev
 * считаются по результату.
 *
 * <p>page - 0-based; size - количество элементов на страницу
 * (default 20, max 100 - см. {@code PageRequest}).
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrev
) {

    /**
     * Конструктор с автоматическим расчётом totalPages/hasNext/hasPrev.
     * totalPages = ceil(total/size), но минимум 1 (пустая первая страница).
     */
    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        if (totalPages == 0) {
            totalPages = 1;
        }
        return new PagedResponse<>(
                items,
                page,
                size,
                total,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }
}
