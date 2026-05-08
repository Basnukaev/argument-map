package ru.basnukaev.argumentmap.library.shamela.etl.dto;

/**
 * Заголовок главы из {@code {bookId}.sqlite}. {@code parentId == 0}
 * означает корневой заголовок (shamela-соглашение, в нашей схеме
 * хранится как-есть и интерпретируется при mapping в lib_chapters
 * на этапе 15.5).
 *
 * <p>{@code pageRef} - ссылка на страницу. Может быть {@code id} из
 * {@code page} или совпадать с {@code page.page} (printed_page) -
 * проверяется эмпирически на реальных данных в этапе 15.4/15.5.
 */
public record ShamelaTitleRow(
        long bookId,
        int id,
        String content,
        String pageRef,
        Integer parentId
) {
}
