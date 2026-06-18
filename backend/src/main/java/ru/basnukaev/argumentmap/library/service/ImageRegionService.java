package ru.basnukaev.argumentmap.library.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.ImageRegionRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.service.PermissionService;

/**
 * CRUD сервис над {@link ImageRegion} - выделенные прямоугольники на
 * страницах-сканах (Этап 17.c, ADR-041). Регион - связь между
 * physical area на скане и semantic content (например хадис-бокс,
 * marginalia, footnote). Используется ImagePageRenderer (18.e) для
 * overlay визуализации.
 *
 * <p>Координаты нормализованные (0..1). DB CHECK constraint
 * {@code lib_image_regions_bounds} гарантирует что регион внутри
 * страницы; здесь только validate page existence и delegate в
 * repository.
 */
@Service
public class ImageRegionService {

    private final ImageRegionRepository imageRegionRepository;
    private final PageRepository pageRepository;
    private final PermissionService permissionService;

    public ImageRegionService(ImageRegionRepository imageRegionRepository,
                               PageRepository pageRepository,
                               PermissionService permissionService) {
        this.imageRegionRepository = imageRegionRepository;
        this.pageRepository = pageRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public ImageRegion create(UUID pageId, double x, double y,
                               double width, double height,
                               String extractedText) {
        if (pageRepository.findById(pageId).isEmpty()) {
            throw new PageNotFoundException(pageId);
        }
        ImageRegion region = new ImageRegion(
                UUID.randomUUID(),
                pageId,
                x, y, width, height,
                extractedText,
                Instant.now()
        );
        return imageRegionRepository.save(region);
    }

    @Transactional(readOnly = true)
    public ImageRegion getOne(UUID regionId) {
        return imageRegionRepository.findById(regionId)
                .orElseThrow(() -> new ImageRegionNotFoundException(regionId));
    }

    @Transactional(readOnly = true)
    public List<ImageRegion> listByPage(UUID pageId, UUID userId, String role) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));
        // Read-guard: метадата регионов (bbox + extractedText) страницы
        // приватной книги не должна утекать анониму/чужому. permitAll на
        // GET /library/pages/** сделал эндпоинт достижимым без auth — guard
        // обязателен (C-1, независимое ревью С62).
        permissionService.assertCanReadBook(page.bookId(), userId, role);
        return imageRegionRepository.findByPageId(pageId);
    }

    @Transactional
    public void delete(UUID regionId) {
        boolean removed = imageRegionRepository.deleteById(regionId);
        if (!removed) {
            throw new ImageRegionNotFoundException(regionId);
        }
    }
}
