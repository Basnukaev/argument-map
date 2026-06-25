package ru.basnukaev.argumentmap.domain;

/**
 * Режим citation - какой positional pointer заполнен. Derived из значений
 * полей node_sources, не хранится отдельно (single source of truth - сами
 * positional поля).
 *
 * <p>Соответствие colonok node_sources:
 * <ul>
 *   <li>{@link #TEXT} - page_id + range_start + range_end</li>
 *   <li>{@link #PDF} - pdf_file_id + pdf_page_number + pdf_bbox (user-upload книги через library_files FK)</li>
 *   <li>{@link #PDF_LINK} - pdf_file_index + pdf_page_number + pdf_bbox
 *       (FILE_ONLY archive.org-сканы без library_files-строки, адресуются
 *       0-based ordinal'ом в pdf_links.files[] - см. ADR-067)</li>
 *   <li>{@link #REGION} - image_region_id (future, для image scans)</li>
 *   <li>{@link #LEGACY} - все positional NULL (freeform AddSourceModal flow)</li>
 * </ul>
 */
public enum CitationMode {
    TEXT,
    PDF,
    PDF_LINK,
    REGION,
    LEGACY;

    public static CitationMode derive(boolean hasPageId, boolean hasPdfFileId,
                                      boolean hasPdfFileIndex, boolean hasImageRegionId) {
        if (hasPageId) {
            return TEXT;
        }
        if (hasPdfFileId) {
            return PDF;
        }
        if (hasPdfFileIndex) {
            return PDF_LINK;
        }
        if (hasImageRegionId) {
            return REGION;
        }
        return LEGACY;
    }
}
