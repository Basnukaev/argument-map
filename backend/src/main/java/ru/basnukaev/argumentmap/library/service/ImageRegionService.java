package ru.basnukaev.argumentmap.library.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.repository.ImageRegionRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

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

    public ImageRegionService(ImageRegionRepository imageRegionRepository,
                               PageRepository pageRepository) {
        this.imageRegionRepository = imageRegionRepository;
        this.pageRepository = pageRepository;
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
    public List<ImageRegion> listByPage(UUID pageId) {
        if (pageRepository.findById(pageId).isEmpty()) {
            throw new PageNotFoundException(pageId);
        }
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
