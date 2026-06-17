package ru.basnukaev.argumentmap.web.controller;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.service.TopicExportService;
import ru.basnukaev.argumentmap.service.TopicImportService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto;
import ru.basnukaev.argumentmap.web.dto.TopicImportResponse;

/**
 * REST endpoints для JSON export/import темы (Этап 6, ADR-037).
 *
 * <p>Два пути импорта:
 * <ul>
 *   <li>{@code POST /api/v1/topics/import} с {@code Content-Type:
 *       application/json} - programmatic flow (curl, скрипты, agent-to-agent)</li>
 *   <li>{@code POST /api/v1/topics/import} с {@code Content-Type:
 *       multipart/form-data} - upload файла через {@code <input type="file">}
 *       во frontend UI</li>
 * </ul>
 * Один endpoint, content-type negotiation через два @PostMapping (Spring
 * routes по {@code consumes})
 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicExportImportController {

    private static final Logger log = LoggerFactory.getLogger(TopicExportImportController.class);

    private final TopicExportService exportService;
    private final TopicImportService importService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public TopicExportImportController(TopicExportService exportService,
                                       TopicImportService importService,
                                       PermissionService permissionService,
                                       ObjectMapper objectMapper) {
        this.exportService = exportService;
        this.importService = importService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Экспорт темы в JSON. Возвращает {@link TopicExportDto} как
     * {@code application/json} с {@code Content-Disposition: attachment} -
     * браузер сразу скачивает как файл.
     *
     * <p>Read-guard (ADR-043): export отдаёт полный граф темы (nodes +
     * edges + sources + authorities), поэтому требует тех же read-прав
     * что GET /topics/{id} и /graph. Без проверки любой мог бы скачать
     * приватную тему по её URL. assertCanRead для PRIVATE чужой темы →
     * 403 (404-like: не leak'аем существование).
     */
    @GetMapping("/{topicId}/export")
    public ResponseEntity<TopicExportDto> export(@PathVariable UUID topicId) {
        // Guest view (roadmap 49.G): GET под permitAll. userId из
        // SecurityContext (null если аноним), не @CurrentUser. assertCanRead
        // отдаёт PRIVATE/SHARED чужой темы как 403 - аноним экспортирует
        // только PUBLIC.
        UUID userId = SecurityContextUtils.currentUserIdOrNull();
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        permissionService.assertCanRead(topicId, userId, role);
        TopicExportDto dto = exportService.exportTopic(topicId);

        // короткий ID (первые 8 символов UUID) для имени файла - читаемо
        // и достаточно уникально, чтобы не путать файлы экспорта на диске
        String shortId = topicId.toString().substring(0, 8);
        String filename = "topic-" + shortId + ".json";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(dto);
    }

    /**
     * Импорт темы через JSON body. {@code @CurrentUser} обеспечивает что
     * новая тема будет принадлежать импортирующему пользователю, а не
     * createdBy из payload (security).
     */
    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TopicImportResponse> importJson(@Valid @RequestBody TopicExportDto dto,
                                                          @CurrentUser UUID currentUserId) {
        log.info("Topic import via JSON: formatVersion={} title='{}' user={}",
                dto.formatVersion(),
                dto.topic() != null ? dto.topic().title() : "(null)",
                currentUserId);
        TopicImportResponse response = importService.importTopic(dto, currentUserId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Импорт темы через multipart upload файла (для UI {@code <input
     * type="file">}). Парсит binary JSON и делегирует в тот же
     * {@code importService}.
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TopicImportResponse> importMultipart(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UUID currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("файл импорта пустой");
        }
        TopicExportDto dto;
        try {
            dto = objectMapper.readValue(file.getBytes(), TopicExportDto.class);
        } catch (IOException ex) {
            // невалидный JSON / структура - 400 через GlobalExceptionHandler
            // (IllegalArgumentException). Detail сообщает о проблеме парсинга
            throw new IllegalArgumentException(
                    "не удалось распарсить JSON импорта: " + ex.getMessage(), ex);
        }
        log.info("Topic import via multipart: filename={} size={}B formatVersion={} user={}",
                file.getOriginalFilename(), file.getSize(), dto.formatVersion(), currentUserId);
        TopicImportResponse response = importService.importTopic(dto, currentUserId);
        return ResponseEntity.status(201).body(response);
    }
}
