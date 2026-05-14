package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MuhaqqiqRepositoryIT {

    @Autowired
    private MuhaqqiqRepository repository;

    @Test
    void save_withFullName_roundTrip() {
        Muhaqqiq muhaqqiq = new Muhaqqiq(
                UUID.randomUUID(),
                "السلامة",
                "سامي بن محمد السلامة",
                Instant.now()
        );

        repository.save(muhaqqiq);

        Muhaqqiq reloaded = repository.findById(muhaqqiq.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("السلامة");
        assertThat(reloaded.fullName()).isEqualTo("سامي بن محمد السلامة");
    }

    @Test
    void save_withNullFullName_persistsNull() {
        Muhaqqiq muhaqqiq = new Muhaqqiq(UUID.randomUUID(), "Аль-Албани", null, Instant.now());

        repository.save(muhaqqiq);

        assertThat(repository.findById(muhaqqiq.id()).orElseThrow().fullName()).isNull();
    }

    @Test
    void findByName_returnsRow() {
        Muhaqqiq muhaqqiq = repository.save(new Muhaqqiq(
                UUID.randomUUID(), "Шуайб аль-Арна'ут", "أبو أسامة شعيب الأرناؤوط", Instant.now()
        ));

        assertThat(repository.findByName("Шуайб аль-Арна'ут"))
                .isPresent()
                .map(Muhaqqiq::id)
                .hasValue(muhaqqiq.id());
    }

    @Test
    void findOrCreate_createsWithNullFullName() {
        UUID id = repository.findOrCreate("Новый редактор");

        Muhaqqiq created = repository.findById(id).orElseThrow();
        assertThat(created.name()).isEqualTo("Новый редактор");
        assertThat(created.fullName()).isNull();
    }

    @Test
    void findOrCreate_returnsExistingId() {
        Muhaqqiq existing = repository.save(new Muhaqqiq(
                UUID.randomUUID(), "Ас-Сахалити", "محمد ناصر الدين السحاليتي", Instant.now()
        ));

        assertThat(repository.findOrCreate("Ас-Сахалити")).isEqualTo(existing.id());
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new Muhaqqiq(UUID.randomUUID(), "Ат-Турки", null, Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new Muhaqqiq(UUID.randomUUID(), "Ат-Турки", "разный fullName", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
