package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorityRepositoryIT {

    @Autowired
    private AuthorityRepository authorityRepository;

    @Test
    void save_insertsAuthority_findByIdReturnsSame() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Authority authority = new Authority(
                UUID.randomUUID(),
                "Ибн Таймия",
                "Известный учёный и реформатор",
                "XIII-XIV век",
                "ханбалитский",
                "{\"birth_year\":1263}",
                now,
                null, null, null
        );

        authorityRepository.save(authority);

        Optional<Authority> found = authorityRepository.findById(authority.id());
        assertThat(found).isPresent();
        Authority reloaded = found.get();
        assertThat(reloaded.name()).isEqualTo("Ибн Таймия");
        assertThat(reloaded.era()).isEqualTo("XIII-XIV век");
        assertThat(reloaded.madhab()).isEqualTo("ханбалитский");
        assertThat(reloaded.metadata()).contains("1263");
        assertThat(reloaded.createdAt()).isEqualTo(now);
    }

    @Test
    void save_withNullableFields_worksFine() {
        Authority authority = new Authority(
                UUID.randomUUID(), "Неизвестный",
                null, null, null, null, Instant.now(),
                null, null, null
        );

        authorityRepository.save(authority);

        Authority reloaded = authorityRepository.findById(authority.id()).orElseThrow();
        assertThat(reloaded.bio()).isNull();
        assertThat(reloaded.era()).isNull();
        assertThat(reloaded.madhab()).isNull();
        assertThat(reloaded.metadata()).isNull();
        assertThat(reloaded.fullName()).isNull();
        assertThat(reloaded.deathYearHijri()).isNull();
    }

    @Test
    void searchByName_caseInsensitive_partialMatch() {
        authorityRepository.save(new Authority(UUID.randomUUID(), "Имам Малик", null, null, "маликитский", null, Instant.now(), null, null, null));
        authorityRepository.save(new Authority(UUID.randomUUID(), "Имам Шафии", null, null, "шафиитский", null, Instant.now(), null, null, null));
        authorityRepository.save(new Authority(UUID.randomUUID(), "Ибн Хазм", null, null, null, null, Instant.now(), null, null, null));

        List<Authority> found = authorityRepository.searchByName("имам");

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Authority::name).containsExactly("Имам Малик", "Имам Шафии");
    }

    @Test
    void findAll_returnsAllOrderedByName() {
        Authority a = new Authority(UUID.randomUUID(), "Zzz", null, null, null, null, Instant.now(), null, null, null);
        Authority b = new Authority(UUID.randomUUID(), "Aaa", null, null, null, null, Instant.now(), null, null, null);
        authorityRepository.save(a);
        authorityRepository.save(b);

        List<Authority> all = authorityRepository.findAll();
        assertThat(all).extracting(Authority::id).startsWith(b.id());
    }

    @Test
    void deleteById_removesAuthority() {
        Authority a = new Authority(UUID.randomUUID(), "x", null, null, null, null, Instant.now(), null, null, null);
        authorityRepository.save(a);

        boolean deleted = authorityRepository.deleteById(a.id());

        assertThat(deleted).isTrue();
        assertThat(authorityRepository.findById(a.id())).isEmpty();
    }

    // Bug-hunt Tier-3 #2: UNIQUE(name) + idempotent saveIgnoreConflict

    @Test
    void saveIgnoreConflict_secondInsertSameName_returnsCanonicalNoDuplicate() {
        // Эмуляция find-then-insert гонки: два «вызова» вставляют одно имя.
        // saveIgnoreConflict (ON CONFLICT name DO NOTHING + re-select) делает
        // второй no-op и возвращает уже существующую (каноническую) строку.
        String name = "Идемпотентный-" + UUID.randomUUID();
        Authority first = new Authority(UUID.randomUUID(), name, "bio-1",
                null, null, null, Instant.now(), null, null, null);
        Authority second = new Authority(UUID.randomUUID(), name, "bio-2",
                null, null, null, Instant.now(), null, null, null);

        Authority r1 = authorityRepository.saveIgnoreConflict(first);
        Authority r2 = authorityRepository.saveIgnoreConflict(second);

        // оба вернули одну и ту же (первую) строку - дубля нет
        assertThat(r1.id()).isEqualTo(first.id());
        assertThat(r2.id()).isEqualTo(first.id());
        assertThat(r2.bio()).isEqualTo("bio-1"); // вторая вставка - no-op
        // в БД ровно одна строка под этим именем
        assertThat(authorityRepository.findByName(name)).get()
                .extracting(Authority::id).isEqualTo(first.id());
    }

    @Test
    void save_duplicateName_violatesUniqueConstraint() {
        // прямой save (без ON CONFLICT) на дубль имени - падает на UNIQUE
        // index (миграция 66). Это страховка БД даже если код обойдёт resolver.
        String name = "Уникальный-" + UUID.randomUUID();
        authorityRepository.save(new Authority(UUID.randomUUID(), name, null,
                null, null, null, Instant.now(), null, null, null));

        Authority dup = new Authority(UUID.randomUUID(), name, null,
                null, null, null, Instant.now(), null, null, null);

        assertThatThrownBy(() -> authorityRepository.save(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveIgnoreConflict_newName_insertsRow() {
        String name = "Новый-" + UUID.randomUUID();
        Authority a = new Authority(UUID.randomUUID(), name, "новый",
                null, null, null, Instant.now(), null, null, null);

        Authority result = authorityRepository.saveIgnoreConflict(a);

        assertThat(result.id()).isEqualTo(a.id());
        assertThat(authorityRepository.findById(a.id())).get()
                .extracting(Authority::bio).isEqualTo("новый");
    }

    // ADR-028: academic citation fields

    @Test
    void save_withFullNameAndDeathYear_roundTrip() {
        Authority authority = new Authority(
                UUID.randomUUID(),
                "ابن كثير",
                null, "VIII в.х.", "shafii", null, Instant.now(),
                "إسماعيل بن عمر بن كثير الدمشقي",
                774, null
        );

        authorityRepository.save(authority);

        Authority reloaded = authorityRepository.findById(authority.id()).orElseThrow();
        assertThat(reloaded.fullName()).isEqualTo("إسماعيل بن عمر بن كثير الدمشقي");
        assertThat(reloaded.deathYearHijri()).isEqualTo(774);
    }

    @Test
    void save_deathYearZero_violatesCheck() {
        Authority bad = new Authority(
                UUID.randomUUID(), "Bad death year",
                null, null, null, null, Instant.now(),
                null, 0, null
        );

        assertThatThrownBy(() -> authorityRepository.save(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_deathYearTooLarge_violatesCheck() {
        Authority bad = new Authority(
                UUID.randomUUID(), "Future scholar",
                null, null, null, null, Instant.now(),
                null, 2500, null
        );

        assertThatThrownBy(() -> authorityRepository.save(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
