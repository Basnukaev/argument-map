package ru.basnukaev.argumentmap.library.pdf.domain;

import java.util.List;

/**
 * Полные PDF-метаданные книги: где брать root, список файлов,
 * есть ли cover. {@code root} - префикс к {@link PdfFileInfo#filename}
 * для построения download URL (например
 * {@code "https://archive.org/download/ibnkatheer_jawzee/"}).
 *
 * <p>{@code totalSizeBytes} - сумма размеров всех файлов если
 * известна, иначе null.
 */
public record PdfMetadata(
        String root,
        boolean hasCover,
        Long totalSizeBytes,
        List<PdfFileInfo> files
) {
}
