package ru.basnukaev.argumentmap.web.dto;

/**
 * Ответ POST /api/v1/topics/{id}/renormalize-zindex.
 *
 * <p>{@code nodesRenormalized} — количество узлов чьи z_index были
 * перезаписаны в компактную последовательность 0..N.
 * {@code edgesRenormalized} — аналогично для рёбер. Нули означают
 * что в теме не было узлов/рёбер — не ошибка.
 */
public record ZIndexRenormalizeResponse(
        int nodesRenormalized,
        int edgesRenormalized
) {
}
