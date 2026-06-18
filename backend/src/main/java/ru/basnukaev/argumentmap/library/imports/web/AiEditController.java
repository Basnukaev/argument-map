package ru.basnukaev.argumentmap.library.imports.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.AiEditStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.imports.AiEditNotConfiguredException;
import ru.basnukaev.argumentmap.library.imports.AiEditService;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.service.BookService;
import ru.basnukaev.argumentmap.library.web.dto.AiEditJobResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoints для AI editing pass (Этап 17.e, ADR-042).
 *
 * <ul>
 *   <li>{@code POST /api/v1/library/pages/{pageId}/ai-edit} - триггер
 *       async AI edit через {@link AiEditService#enhanceAsync}. Returns
 *       202 Accepted + текущий status. 503 если LlmClient disabled
 *       (нет API key)</li>
 *   <li>{@code GET /api/v1/library/pages/{pageId}/ai-edit} - polling
 *       endpoint для статуса. Фронт опрашивает каждые 2-3 сек пока
 *       {@code status=PROCESSING}, переключается на DONE/FAILED -
 *       стопает polling</li>
 * </ul>
 *
 * <p>Re-trigger допустим (после DONE если результат не понравился, либо
 * после FAILED для retry). {@link AiEditService#enhance} idempotent на
 * уровне state machine - PENDING/FAILED/DONE → PROCESSING принимается
 * из любого предыдущего state.
 *
 * <p>403 не возвращается на MVP - любой authenticated user может
 * триггерить AI edit. Visibility (этап 22) подключится в будущем.
 */
@RestController
@RequestMapping("/api/v1/library/pages")
public class AiEditController {

    private static final Logger log = LoggerFactory.getLogger(AiEditController.class);

    private final AiEditService aiEditService;
    private final LlmClient llmClient;
    private final PageRepository pageRepository;
    private final BookService bookService;
    private final PermissionService permissionService;

    public AiEditController(AiEditService aiEditService,
                             LlmClient llmClient,
                             PageRepository pageRepository,
                             BookService bookService,
                             PermissionService permissionService) {
        this.aiEditService = aiEditService;
        this.llmClient = llmClient;
        this.pageRepository = pageRepository;
        this.bookService = bookService;
        this.permissionService = permissionService;
    }

    @PostMapping("/{pageId}/ai-edit")
    public ResponseEntity<AiEditJobResponse> triggerAiEdit(
            @PathVariable UUID pageId,
            @CurrentUser UUID currentUserId) {

        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));

        // ADR-043 Amendment: write-guard - AI edit тратит платный LLM
        // API budget + переписывает formatted_content страницы, поэтому
        // требует write-доступ к книге. Раньше шло без проверки (любой
        // мог триггерить AI edit на чужой книге и жечь бюджет).
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        bookService.assertCanWriteBookForPage(pageId, currentUserId, role);

        // Pre-flight check: если ключа нет, синхронный 503 вместо
        // background FAILED. Лучше UX - пользователь сразу видит причину.
        if (!llmClient.isEnabled()) {
            throw new AiEditNotConfiguredException();
        }

        log.info("AI edit trigger: page={} by user={} currentStatus={}",
                pageId, currentUserId, page.aiEditStatus());

        aiEditService.enhanceAsync(pageId);

        String status = page.aiEditStatus() != null
                ? page.aiEditStatus() : AiEditStatus.PENDING;
        AiEditJobResponse body = new AiEditJobResponse(
                pageId, status,
                page.aiEditStartedAt(), page.aiEditCompletedAt(),
                page.textContent() != null && !page.textContent().isBlank()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{pageId}/ai-edit")
    public AiEditJobResponse getAiEditStatus(@PathVariable UUID pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));
        // Read-guard: статус AI-обработки страницы приватной книги не должен
        // утекать анониму/чужому. permitAll на GET /library/pages/** сделал
        // эндпоинт достижимым без auth — guard обязателен (C-1, ревью С62).
        permissionService.assertCanReadBook(
                page.bookId(),
                SecurityContextUtils.currentUserIdOrNull(),
                SecurityContextUtils.currentRoleOrAnonymous());
        return new AiEditJobResponse(
                pageId,
                page.aiEditStatus(),
                page.aiEditStartedAt(),
                page.aiEditCompletedAt(),
                page.textContent() != null && !page.textContent().isBlank()
        );
    }
}
