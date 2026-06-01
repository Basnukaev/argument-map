package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.hadith.service.HadithCitationService;
import ru.basnukaev.argumentmap.hadith.web.dto.AttachHadithCitationRequest;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithCitationResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Прикрепление хадиса из {@code hd_*} к узлу графа как опоры (под-проект #2).
 * Хадис-опора живёт в общем {@code node_sources} (sourceType=HADITH), поэтому
 * list/detach — через существующий {@code /nodes/{id}/sources}. Здесь только
 * attach (с ensure-source логикой моста).
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/hadith-citations")
public class HadithCitationController {

    private final HadithCitationService hadithCitationService;

    public HadithCitationController(HadithCitationService hadithCitationService) {
        this.hadithCitationService = hadithCitationService;
    }

    @PostMapping
    public ResponseEntity<HadithCitationResponse> attach(
            @PathVariable UUID nodeId,
            @Valid @RequestBody AttachHadithCitationRequest request,
            @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        NodeSource link = hadithCitationService.attachHadithToNode(
                nodeId, request.hadithId(), currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(HadithCitationResponse.of(link, request.hadithId()));
    }
}
