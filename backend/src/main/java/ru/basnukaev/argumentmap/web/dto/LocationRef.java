package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record LocationRef(
        UUID pageId,
        String part,
        String printedPage,
        Integer pageNumber,
        Integer rangeStart,
        Integer rangeEnd
) {
}
