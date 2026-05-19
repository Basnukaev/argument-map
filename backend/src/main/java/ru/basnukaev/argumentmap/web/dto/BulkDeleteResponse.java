package ru.basnukaev.argumentmap.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ответ группового удаления (DELETE /api/v1/nodes/bulk).
 *
 * <p>{@code deletedIds} - сколько и какие узлы успешно удалены.
 * {@code skippedRootIds} - узлы которые были в запросе но являются
 * корневыми (защита {@link ru.basnukaev.argumentmap.exception.NodeIsRootException}
 * на single-delete) - skip'нуты, не fail'или весь запрос. В UI можно
 * показать «удалил 47 из 50, 3 корневых пропущено».
 */
public record BulkDeleteResponse(
        List<UUID> deletedIds,
        List<UUID> skippedRootIds
) {
    public int deletedCount() {
        return deletedIds.size();
    }

    public int skippedCount() {
        return skippedRootIds.size();
    }
}
