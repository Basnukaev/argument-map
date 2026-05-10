package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Page;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ImageRegionRepositoryIT {

    @Autowired
    private ImageRegionRepository imageRegionRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private Page page;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        Instant now = Instant.now();
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "T", null, "ar",
                null, null, userId, now, now
        ));
        page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                null, "https://example.com/scan.jpg", now, now
        ));
    }

    @Test
    void save_validRegion_persistsCorrectly() {
        ImageRegion region = new ImageRegion(
                UUID.randomUUID(), page.id(),
                0.1, 0.2, 0.3, 0.4,
                "بسم الله", Instant.now()
        );

        imageRegionRepository.save(region);

        ImageRegion reloaded = imageRegionRepository.findById(region.id()).orElseThrow();
        assertThat(reloaded.x()).isEqualTo(0.1);
        assertThat(reloaded.width()).isEqualTo(0.3);
        assertThat(reloaded.extractedText()).isEqualTo("بسم الله");
    }

    @Test
    void save_regionExceedingPageBounds_violatesCheck() {
        ImageRegion oversized = new ImageRegion(
                UUID.randomUUID(), page.id(),
                0.5, 0.5, 0.6, 0.6,
                null, Instant.now()
        );

        assertThatThrownBy(() -> imageRegionRepository.save(oversized))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lib_image_regions_bounds");
    }

    @Test
    void save_zeroWidthRegion_violatesCheck() {
        ImageRegion zero = new ImageRegion(
                UUID.randomUUID(), page.id(),
                0.1, 0.1, 0.0, 0.5,
                null, Instant.now()
        );

        assertThatThrownBy(() -> imageRegionRepository.save(zero))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_negativeCoordinates_violatesCheck() {
        ImageRegion negative = new ImageRegion(
                UUID.randomUUID(), page.id(),
                -0.1, 0.1, 0.5, 0.5,
                null, Instant.now()
        );

        assertThatThrownBy(() -> imageRegionRepository.save(negative))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByPageId_returnsAllRegionsForPage() {
        imageRegionRepository.save(new ImageRegion(
                UUID.randomUUID(), page.id(), 0.1, 0.1, 0.2, 0.2, null, Instant.now()
        ));
        imageRegionRepository.save(new ImageRegion(
                UUID.randomUUID(), page.id(), 0.5, 0.5, 0.2, 0.2, null, Instant.now()
        ));

        List<ImageRegion> regions = imageRegionRepository.findByPageId(page.id());

        assertThat(regions).hasSize(2);
    }

    @Test
    void deletePage_cascadesRegions() {
        ImageRegion region = imageRegionRepository.save(new ImageRegion(
                UUID.randomUUID(), page.id(), 0.1, 0.1, 0.2, 0.2, null, Instant.now()
        ));

        pageRepository.deleteById(page.id());

        assertThat(imageRegionRepository.findById(region.id())).isEmpty();
    }
}
