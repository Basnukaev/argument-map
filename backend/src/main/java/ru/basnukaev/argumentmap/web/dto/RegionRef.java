package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record RegionRef(
        UUID id,
        String printedPage,
        Integer pageNumber
) {
}
