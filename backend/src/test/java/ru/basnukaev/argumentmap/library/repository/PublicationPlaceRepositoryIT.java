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
import ru.basnukaev.argumentmap.library.domain.PublicationPlace;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicationPlaceRepositoryIT {

    @Autowired
    private PublicationPlaceRepository repository;

    @Test
    void save_insertsAndFindByIdReturnsIt() {
        PublicationPlace place = new PublicationPlace(UUID.randomUUID(), "Бейрут", Instant.now());

        repository.save(place);

        assertThat(repository.findById(place.id())).isPresent()
                .map(PublicationPlace::name).hasValue("Бейрут");
    }

    @Test
    void findByName_returnsRowWhenExists() {
        PublicationPlace place = repository.save(new PublicationPlace(
                UUID.randomUUID(), "Эр-Рияд", Instant.now()
        ));

        assertThat(repository.findByName("Эр-Рияд"))
                .isPresent()
                .map(PublicationPlace::id)
                .hasValue(place.id());
    }

    @Test
    void findByName_returnsEmptyWhenAbsent() {
        assertThat(repository.findByName("Несуществующий город")).isEmpty();
    }

    @Test
    void findOrCreate_returnsExistingIdWhenPresent() {
        PublicationPlace place = repository.save(new PublicationPlace(
                UUID.randomUUID(), "Каир", Instant.now()
        ));

        UUID id = repository.findOrCreate("Каир");

        assertThat(id).isEqualTo(place.id());
    }

    @Test
    void findOrCreate_createsNewRowWhenAbsent() {
        UUID id = repository.findOrCreate("Дамаск");

        assertThat(repository.findById(id)).isPresent()
                .map(PublicationPlace::name).hasValue("Дамаск");
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new PublicationPlace(UUID.randomUUID(), "Багдад", Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new PublicationPlace(UUID.randomUUID(), "Багдад", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
