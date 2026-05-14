package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record MuhaqqiqRef(
        UUID id,
        String name,
        String fullName
) {
}
