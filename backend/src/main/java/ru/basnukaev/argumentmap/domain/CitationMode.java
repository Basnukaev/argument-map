package ru.basnukaev.argumentmap.domain;

/**
 * Режим citation - какой positional pointer заполнен. Derived из значений
 * полей node_sources, не хранится отдельно (single source of truth - сами
 * positional поля).
 *
 * <p>Соответствие colonok node_sources:
 * <ul>
 *   <li>{@link #TEXT} - page_id + range_start + range_end</li>
 *   <li>{@link #PDF} - pdf_file_id + pdf_page_number + pdf_bbox</li>
 *   <li>{@link #REGION} - image_region_id (future, для image scans)</li>
 *   <li>{@link #LEGACY} - все positional NULL (freeform AddSourceModal flow)</li>
 * </ul>
 */
public enum CitationMode {
    TEXT,
    PDF,
    REGION,
    LEGACY;

    public static CitationMode derive(boolean hasPageId, boolean hasPdfFileId, boolean hasImageRegionId) {
        if (hasPageId) {
            return TEXT;
        }
        if (hasPdfFileId) {
            return PDF;
        }
        if (hasImageRegionId) {
            return REGION;
        }
        return LEGACY;
    }
}
