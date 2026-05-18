package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidSourceException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;

/**
 * IT для {@link SourceService} - покрывает create с validation, get/delete +
 * pagination с фильтрами включая combination type≠HADITH+reliability.
 * До audit не было прямого теста сервиса, покрытие шло косвенно через
 * SourceControllerIT. Здесь явная проверка business rules service-слоя.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceServiceIT {

    @Autowired
    private SourceService sourceService;

    @Autowired
    private AuthorityService authorityService;

    @Test
    void createSource_whenReliabilityWithoutHadithType_throwsInvalidSourceException() {
        // Семантика: reliability имеет смысл только для HADITH. Если клиент
        // прислал её с BOOK/QURAN/etc - 422 через InvalidSourceException.
        assertThatThrownBy(() -> sourceService.createSource(
                SourceType.BOOK, "Сахих Бухари", null,
                Reliability.SAHIH, null, null, null
        )).isInstanceOf(InvalidSourceException.class);
    }

    @Test
    void createSource_HADITH_withReliability_persists() {
        Source s = sourceService.createSource(
                SourceType.HADITH,
                "Хадис от Аль-Бухари",
                "Сахих Бухари 1:1",
                Reliability.SAHIH,
                null, null, null
        );
        assertThat(s.id()).isNotNull();
        assertThat(s.reliability()).isEqualTo(Reliability.SAHIH);
        assertThat(s.isHadith()).isTrue();
    }

    @Test
    void createSource_whenAuthorityIdMissing_throwsAuthorityNotFoundException() {
        UUID missingAuthority = UUID.randomUUID();
        assertThatThrownBy(() -> sourceService.createSource(
                SourceType.BOOK, "Книга", null,
                null, missingAuthority, null, null
        )).isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void createSource_whenBookIdMissing_throwsBookNotFoundException() {
        UUID missingBook = UUID.randomUUID();
        // Гарантированно отсутствующая книга - 404, не 500 FK violation
        assertThatThrownBy(() -> sourceService.createSource(
                SourceType.BOOK, "Книга", null,
                null, null, missingBook, null
        )).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void createSource_withValidAuthorityId_persistsLink() {
        Authority a = authorityService.createAuthority("Имам Шафии", null, null, "шафиитский", null);
        Source s = sourceService.createSource(
                SourceType.ARTICLE, "Статья шейха", "URL", null, a.id(), null, null
        );
        assertThat(s.authorityId()).isEqualTo(a.id());
    }

    @Test
    void getSource_whenMissing_throws() {
        assertThatThrownBy(() -> sourceService.getSource(UUID.randomUUID()))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void deleteSource_whenMissing_throws() {
        assertThatThrownBy(() -> sourceService.deleteSource(UUID.randomUUID()))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void listPage_filterByReliability_requiresMatchingType() {
        sourceService.createSource(SourceType.HADITH, "Хадис-1", null, Reliability.SAHIH, null, null, null);
        sourceService.createSource(SourceType.HADITH, "Хадис-2", null, Reliability.HASAN, null, null, null);
        sourceService.createSource(SourceType.BOOK, "Книга", null, null, null, null, null);

        List<Source> sahih = sourceService.listPage(SourceType.HADITH, Reliability.SAHIH, null, 100, 0);
        long sahihCount = sourceService.countFiltered(SourceType.HADITH, Reliability.SAHIH, null);
        assertThat(sahih).hasSize(1);
        assertThat(sahihCount).isEqualTo(1L);
        assertThat(sahih.get(0).reliability()).isEqualTo(Reliability.SAHIH);
    }

    @Test
    void listPage_reliabilityWithoutHadithType_throwsIllegalArgument() {
        // service-слой validate combo до SQL - IllegalArgumentException
        // (handler даёт 400) а не InvalidSourceException (тот для payload-level).
        assertThatThrownBy(() -> sourceService.listPage(
                SourceType.BOOK, Reliability.SAHIH, null, 10, 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countFiltered_reliabilityWithoutHadithType_throwsIllegalArgument() {
        // count тоже валидирует - чтобы count и listPage всегда согласованы
        assertThatThrownBy(() -> sourceService.countFiltered(
                SourceType.BOOK, Reliability.SAHIH, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchByTitle_returnsCaseInsensitiveMatches() {
        sourceService.createSource(SourceType.BOOK, "Аль-Муватта", null, null, null, null, null);
        sourceService.createSource(SourceType.BOOK, "Сахих Муслим", null, null, null, null, null);

        List<Source> matches = sourceService.searchByTitle("муватта");
        assertThat(matches).extracting(Source::title).contains("Аль-Муватта");
    }
}
