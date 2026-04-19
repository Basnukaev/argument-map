package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
                now
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
                UUID.randomUUID(), "Неизвестный", null, null, null, null, Instant.now()
        );

        authorityRepository.save(authority);

        Authority reloaded = authorityRepository.findById(authority.id()).orElseThrow();
        assertThat(reloaded.bio()).isNull();
        assertThat(reloaded.era()).isNull();
        assertThat(reloaded.madhab()).isNull();
        assertThat(reloaded.metadata()).isNull();
    }

    @Test
    void searchByName_caseInsensitive_partialMatch() {
        authorityRepository.save(new Authority(UUID.randomUUID(), "Имам Малик", null, null, "маликитский", null, Instant.now()));
        authorityRepository.save(new Authority(UUID.randomUUID(), "Имам Шафии", null, null, "шафиитский", null, Instant.now()));
        authorityRepository.save(new Authority(UUID.randomUUID(), "Ибн Хазм", null, null, null, null, Instant.now()));

        List<Authority> found = authorityRepository.searchByName("имам");

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Authority::name).containsExactly("Имам Малик", "Имам Шафии");
    }

    @Test
    void findAll_returnsAllOrderedByName() {
        Authority a = new Authority(UUID.randomUUID(), "Zzz", null, null, null, null, Instant.now());
        Authority b = new Authority(UUID.randomUUID(), "Aaa", null, null, null, null, Instant.now());
        authorityRepository.save(a);
        authorityRepository.save(b);

        List<Authority> all = authorityRepository.findAll();
        assertThat(all).extracting(Authority::id).startsWith(b.id());
    }

    @Test
    void deleteById_removesAuthority() {
        Authority a = new Authority(UUID.randomUUID(), "x", null, null, null, null, Instant.now());
        authorityRepository.save(a);

        boolean deleted = authorityRepository.deleteById(a.id());

        assertThat(deleted).isTrue();
        assertThat(authorityRepository.findById(a.id())).isEmpty();
    }
}
