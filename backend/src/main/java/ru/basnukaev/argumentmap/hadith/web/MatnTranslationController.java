package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.hadith.service.HadithTranslationService;
import ru.basnukaev.argumentmap.hadith.web.dto.MatnTranslateRequest;
import ru.basnukaev.argumentmap.hadith.web.dto.MatnTranslationEditRequest;
import ru.basnukaev.argumentmap.hadith.web.dto.MatnTranslationResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint AI-перевода матна на ru/en on-demand (План 7, ADR-058).
 * Отдельный контроллер под ресурс {@code /api/v1/hadith/matns} (матны —
 * самостоятельный ресурс hadith-домена, отличный от {@code /hadiths},
 * на который замаплен HadithController; Spring конкатенирует class+method
 * пути, поэтому абсолютный method-путь внутри HadithController невозможен).
 */
@RestController
@RequestMapping("/api/v1/hadith/matns")
public class MatnTranslationController {

    private final HadithTranslationService translationService;

    public MatnTranslationController(HadithTranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * Синхронный перевод (LLM 5-15с, фронт показывает лоадер). Идемпотентен:
     * перевод уже есть и не {@code force} → существующий ({@code cached=true})
     * без LLM-вызова; {@code force=true} → регенерация, ADMIN-only.
     *
     * <p>{@code @CurrentUser} обязателен — anonymous отсекается резолвером
     * (401 invalid-token). 404 matn-not-found / 422 invalid-matn-text /
     * 503 llm-not-configured / 403 forbidden-admin-only (force без ADMIN).
     */
    @PostMapping("/{matnId}/translate")
    public MatnTranslationResponse translate(
            @PathVariable UUID matnId,
            @RequestParam(defaultValue = "false") boolean force,
            @Valid @RequestBody MatnTranslateRequest request,
            @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return translationService.translate(
                matnId, request.lang(), force, currentUserId, role);
    }

    /**
     * Ручная правка сохранённого перевода матна — ADMIN перезаписывает
     * {@code text_ru}/{@code text_en} новым текстом БЕЗ вызова LLM (правка,
     * не генерация). Возвращает тот же {@link MatnTranslationResponse}, что
     * и translate, с {@code cached=true} (текст в ответе — сохранённое
     * значение, LLM не звался).
     *
     * <p>{@code @CurrentUser} обязателен — anonymous отсекается резолвером
     * (401 invalid-token). 403 forbidden-admin-only (не-ADMIN) /
     * 404 matn-not-found / 422 invalid-matn-text (blank после trim) /
     * 400 validation (невалидный lang или пустой text от @Valid).
     */
    @PatchMapping("/{matnId}/translation")
    public MatnTranslationResponse editTranslation(
            @PathVariable UUID matnId,
            @Valid @RequestBody MatnTranslationEditRequest request,
            @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return translationService.editTranslation(
                matnId, request.lang(), request.text(), currentUserId, role);
    }
}
