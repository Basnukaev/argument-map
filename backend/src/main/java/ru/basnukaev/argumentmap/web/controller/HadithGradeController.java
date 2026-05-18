package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.HadithGrade;
import ru.basnukaev.argumentmap.domain.HadithGradeWithScholar;
import ru.basnukaev.argumentmap.service.HadithGradeService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateHadithGradeRequest;
import ru.basnukaev.argumentmap.web.dto.HadithGradeResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateHadithGradeRequest;

/**
 * REST-эндпоинты multi-grading хадисов.
 *
 * <ul>
 *   <li>POST   /api/v1/sources/{sourceId}/grades   - добавить оценку</li>
 *   <li>GET    /api/v1/sources/{sourceId}/grades   - список оценок</li>
 *   <li>PATCH  /api/v1/sources/grades/{gradeId}    - обновить</li>
 *   <li>DELETE /api/v1/sources/grades/{gradeId}    - удалить</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sources")
public class HadithGradeController {

    private final HadithGradeService hadithGradeService;

    public HadithGradeController(HadithGradeService hadithGradeService) {
        this.hadithGradeService = hadithGradeService;
    }

    @PostMapping("/{sourceId}/grades")
    public ResponseEntity<HadithGradeResponse> addGrade(@PathVariable UUID sourceId,
                                                        @Valid @RequestBody CreateHadithGradeRequest request,
                                                        @CurrentUser UUID userId) {
        HadithGrade created = hadithGradeService.addGrade(
                sourceId, request.scholarId(), request.grade(),
                request.gradeCitation(), request.comment(), userId
        );
        // для consistent response - возвращаем denormalized view из listForSource;
        // simpler: вернуть thin HadithGradeResponse без scholar info (фронт может
        // refetch list). Делаем thin - 1 SQL вместо JOIN.
        HadithGradeResponse body = new HadithGradeResponse(
                created.id(), created.sourceId(), created.scholarId(),
                null, null, null,
                created.grade(), created.gradeCitation(), created.comment(),
                created.createdAt(), created.createdBy()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/sources/grades/" + created.id()))
                .body(body);
    }

    @GetMapping("/{sourceId}/grades")
    public List<HadithGradeResponse> listGrades(@PathVariable UUID sourceId) {
        List<HadithGradeWithScholar> grades = hadithGradeService.listForSource(sourceId);
        return grades.stream().map(HadithGradeController::toResponse).toList();
    }

    @PatchMapping("/grades/{gradeId}")
    public HadithGradeResponse updateGrade(@PathVariable UUID gradeId,
                                           @Valid @RequestBody UpdateHadithGradeRequest request,
                                           @CurrentUser UUID userId) {
        HadithGrade updated = hadithGradeService.updateGrade(
                gradeId, request.grade(), request.gradeCitation(), request.comment(),
                userId, SecurityContextUtils.currentRoleOrAnonymous()
        );
        return new HadithGradeResponse(
                updated.id(), updated.sourceId(), updated.scholarId(),
                null, null, null,
                updated.grade(), updated.gradeCitation(), updated.comment(),
                updated.createdAt(), updated.createdBy()
        );
    }

    @DeleteMapping("/grades/{gradeId}")
    public ResponseEntity<Void> deleteGrade(@PathVariable UUID gradeId,
                                            @CurrentUser UUID userId) {
        hadithGradeService.removeGrade(gradeId, userId, SecurityContextUtils.currentRoleOrAnonymous());
        return ResponseEntity.noContent().build();
    }

    private static HadithGradeResponse toResponse(HadithGradeWithScholar g) {
        return new HadithGradeResponse(
                g.id(), g.sourceId(), g.scholarId(),
                g.scholarName(), g.scholarFullName(), g.scholarDeathYearHijri(),
                g.grade(), g.gradeCitation(), g.comment(),
                g.createdAt(), g.createdBy()
        );
    }
}
