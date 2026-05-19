package ru.basnukaev.argumentmap.service;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.AuthorityType;
import ru.basnukaev.argumentmap.domain.HadithGrade;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;
import ru.basnukaev.argumentmap.domain.HadithGradeWithScholar;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.HadithGradeAccessDeniedException;
import ru.basnukaev.argumentmap.exception.HadithGradeDuplicateException;
import ru.basnukaev.argumentmap.exception.HadithGradeNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidHadithGradeException;
import ru.basnukaev.argumentmap.exception.InvalidScholarAuthorityException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithGradeServiceIT {

    @Autowired
    private HadithGradeService hadithGradeService;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID hadithSourceId;
    private UUID bookSourceId;
    private UUID scholarId;
    private UUID scholar2Id;
    private UUID publisherId;
    private UUID muhaqqiqId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );

        Authority bukhari = new Authority(UUID.randomUUID(),
                "Аль-Бухари", "Имам-мухаддис", "III век хиджры",
                null, null, Instant.now(), "Мухаммад ибн Исмаил аль-Бухари", 256,
                AuthorityType.SCHOLAR);
        authorityRepository.save(bukhari);
        scholarId = bukhari.id();

        Authority tirmizi = new Authority(UUID.randomUUID(),
                "Ат-Тирмизи", "Имам", "III век хиджры",
                null, null, Instant.now(), "Мухаммад ибн Иса ат-Тирмизи", 279,
                AuthorityType.SCHOLAR);
        authorityRepository.save(tirmizi);
        scholar2Id = tirmizi.id();

        // фикстуры для negative-кейсов «PUBLISHER/MUHAQQIQ как scholar»
        Authority darTayba = new Authority(UUID.randomUUID(),
                "Дар Тайба", "Издательство", null,
                null, null, Instant.now(), null, null,
                AuthorityType.PUBLISHER);
        authorityRepository.save(darTayba);
        publisherId = darTayba.id();

        Authority muhaqqiq = new Authority(UUID.randomUUID(),
                "Мухаммад Фуад Абдуль-Баки", "Тахкик", null,
                null, null, Instant.now(), null, null,
                AuthorityType.MUHAQQIQ);
        authorityRepository.save(muhaqqiq);
        muhaqqiqId = muhaqqiq.id();

        Source hadith = new Source(UUID.randomUUID(), SourceType.HADITH,
                "Хадис о намерениях", "Бухари 1", Reliability.SAHIH,
                null, null, null, Instant.now());
        sourceRepository.save(hadith);
        hadithSourceId = hadith.id();

        Source book = new Source(UUID.randomUUID(), SourceType.BOOK,
                "Книга про теологию", "Имам Газали", null,
                null, null, null, Instant.now());
        sourceRepository.save(book);
        bookSourceId = book.id();
    }

    @Test
    void addGrade_validInput_creates() {
        HadithGrade grade = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                "Сахих аль-Бухари 1/1", "Согласовано всеми",
                ownerId
        );

        assertThat(grade.id()).isNotNull();
        assertThat(grade.sourceId()).isEqualTo(hadithSourceId);
        assertThat(grade.scholarId()).isEqualTo(scholarId);
        assertThat(grade.grade()).isEqualTo(HadithGradeValue.SAHIH);
        assertThat(grade.gradeCitation()).isEqualTo("Сахих аль-Бухари 1/1");
        assertThat(grade.comment()).isEqualTo("Согласовано всеми");
        assertThat(grade.createdBy()).isEqualTo(ownerId);
        assertThat(grade.createdAt()).isNotNull();
    }

    @Test
    void addGrade_nonHadithSource_throws400() {
        assertThatThrownBy(() -> hadithGradeService.addGrade(
                bookSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        )).isInstanceOf(InvalidHadithGradeException.class)
                .hasMessageContaining("HADITH");
    }

    @Test
    void addGrade_sourceMissing_throws404() {
        assertThatThrownBy(() -> hadithGradeService.addGrade(
                UUID.randomUUID(), scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        )).isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void addGrade_scholarMissing_throws404() {
        assertThatThrownBy(() -> hadithGradeService.addGrade(
                hadithSourceId, UUID.randomUUID(), HadithGradeValue.SAHIH,
                null, null, ownerId
        )).isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void addGrade_duplicateScholarForSameSource_throws409() {
        hadithGradeService.addGrade(hadithSourceId, scholarId,
                HadithGradeValue.SAHIH, null, null, ownerId);

        assertThatThrownBy(() -> hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.HASAN,
                null, null, ownerId
        )).isInstanceOf(HadithGradeDuplicateException.class);
    }

    @Test
    void addGrade_differentScholarsSameSource_bothAllowed() {
        hadithGradeService.addGrade(hadithSourceId, scholarId,
                HadithGradeValue.SAHIH, null, null, ownerId);
        hadithGradeService.addGrade(hadithSourceId, scholar2Id,
                HadithGradeValue.HASAN, null, null, ownerId);

        List<HadithGradeWithScholar> list = hadithGradeService.listForSource(hadithSourceId);
        assertThat(list).hasSize(2);
    }

    @Test
    void listForSource_returnsScholarJoined() {
        hadithGradeService.addGrade(hadithSourceId, scholarId,
                HadithGradeValue.SAHIH, "Сахих 1/1", null, ownerId);

        List<HadithGradeWithScholar> list = hadithGradeService.listForSource(hadithSourceId);

        assertThat(list).hasSize(1);
        HadithGradeWithScholar g = list.get(0);
        assertThat(g.scholarName()).isEqualTo("Аль-Бухари");
        assertThat(g.scholarFullName()).isEqualTo("Мухаммад ибн Исмаил аль-Бухари");
        assertThat(g.scholarDeathYearHijri()).isEqualTo(256);
        assertThat(g.grade()).isEqualTo(HadithGradeValue.SAHIH);
        assertThat(g.gradeCitation()).isEqualTo("Сахих 1/1");
    }

    @Test
    void listForSource_sourceMissing_throws404() {
        assertThatThrownBy(() -> hadithGradeService.listForSource(UUID.randomUUID()))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void updateGrade_authoredByActor_updates() {
        HadithGrade created = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        );

        HadithGrade updated = hadithGradeService.updateGrade(
                created.id(), HadithGradeValue.HASAN, "Новая ссылка",
                "Спорный случай", ownerId, UserRole.USER
        );

        assertThat(updated.grade()).isEqualTo(HadithGradeValue.HASAN);
        assertThat(updated.gradeCitation()).isEqualTo("Новая ссылка");
        assertThat(updated.comment()).isEqualTo("Спорный случай");
    }

    @Test
    void updateGrade_nonAuthor_throws403() {
        HadithGrade created = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        );

        assertThatThrownBy(() -> hadithGradeService.updateGrade(
                created.id(), HadithGradeValue.HASAN, null, null,
                otherUserId, UserRole.USER
        )).isInstanceOf(HadithGradeAccessDeniedException.class);
    }

    @Test
    void updateGrade_admin_bypassesAuthorCheck() {
        HadithGrade created = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        );

        HadithGrade updated = hadithGradeService.updateGrade(
                created.id(), HadithGradeValue.DAIF, null, null,
                otherUserId, UserRole.ADMIN
        );

        assertThat(updated.grade()).isEqualTo(HadithGradeValue.DAIF);
    }

    @Test
    void removeGrade_existing_deletes() {
        HadithGrade created = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        );

        hadithGradeService.removeGrade(created.id(), ownerId, UserRole.USER);

        assertThat(hadithGradeService.listForSource(hadithSourceId)).isEmpty();
    }

    @Test
    void removeGrade_missingId_throws404() {
        assertThatThrownBy(() -> hadithGradeService.removeGrade(
                UUID.randomUUID(), ownerId, UserRole.USER
        )).isInstanceOf(HadithGradeNotFoundException.class);
    }

    @Test
    void removeGrade_nonAuthor_throws403() {
        HadithGrade created = hadithGradeService.addGrade(
                hadithSourceId, scholarId, HadithGradeValue.SAHIH,
                null, null, ownerId
        );

        assertThatThrownBy(() -> hadithGradeService.removeGrade(
                created.id(), otherUserId, UserRole.USER
        )).isInstanceOf(HadithGradeAccessDeniedException.class);
    }

    // ---- миграция 47: type-валидация scholar ----

    @Test
    void addGrade_publisherAsScholar_throws400() {
        // оценка хадиса от имени издательства - семантическая ошибка
        assertThatThrownBy(() -> hadithGradeService.addGrade(
                hadithSourceId, publisherId, HadithGradeValue.SAHIH,
                null, null, ownerId
        )).isInstanceOf(InvalidScholarAuthorityException.class)
                .hasMessageContaining("PUBLISHER");
    }

    @Test
    void addGrade_muhaqqiqAsScholar_throws400() {
        // тахкик не оценивает хадисы, только редактирует издания
        assertThatThrownBy(() -> hadithGradeService.addGrade(
                hadithSourceId, muhaqqiqId, HadithGradeValue.HASAN,
                null, null, ownerId
        )).isInstanceOf(InvalidScholarAuthorityException.class)
                .hasMessageContaining("MUHAQQIQ");
    }
}
