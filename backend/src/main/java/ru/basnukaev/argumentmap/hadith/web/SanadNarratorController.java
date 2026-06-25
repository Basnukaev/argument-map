package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.hadith.service.SanadTransmissionPhraseService;
import ru.basnukaev.argumentmap.hadith.web.dto.TransmissionPhraseEditRequest;
import ru.basnukaev.argumentmap.hadith.web.dto.TransmissionPhraseResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint правки формул передачи звеньев иснада (курация Фаза 5.b,
 * ADR-065 amendment). Отдельный контроллер под ресурс
 * {@code /api/v1/hadith/sanad-narrators} (звенья — самостоятельный ресурс,
 * отличный от {@code /hadiths}); адресуется по СТАБИЛЬНОМУ синтетическому ключу
 * {@code (hadithId, position)}, а не по нестабильному {@code sanad_id}.
 */
@RestController
@RequestMapping("/api/v1/hadith/sanad-narrators")
public class SanadNarratorController {

    private final SanadTransmissionPhraseService transmissionPhraseService;

    public SanadNarratorController(SanadTransmissionPhraseService transmissionPhraseService) {
        this.transmissionPhraseService = transmissionPhraseService;
    }

    /**
     * Ручная правка формулы передачи (риваят-глагол حدثنا/عن) звена иснада —
     * ADMIN перезаписывает курируемое значение поверх импортного по стабильному
     * ключу {@code (hadithId, position)}. Правка живёт в overlay, переживает
     * реимпорт.
     *
     * <p>{@code @CurrentUser} обязателен — anonymous отсекается резолвером
     * (401 invalid-token). 403 forbidden-admin-only (не-ADMIN) /
     * 404 curation-entity-not-found (нет звена (hadithId, position)) /
     * 400 validation (пустой phrase или null поля от @Valid).
     */
    @PatchMapping("/transmission-phrase")
    public TransmissionPhraseResponse editTransmissionPhrase(
            @Valid @RequestBody TransmissionPhraseEditRequest request,
            @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return transmissionPhraseService.editTransmissionPhrase(
                request.hadithId(), request.position(), request.phrase(), currentUserId, role);
    }
}
