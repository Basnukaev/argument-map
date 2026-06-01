package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Тело POST /nodes/{nodeId}/hadith-citations — какой хадис прикрепить. */
public record AttachHadithCitationRequest(
        @NotNull UUID hadithId
) {
}
