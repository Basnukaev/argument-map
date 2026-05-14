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
import ru.basnukaev.argumentmap.library.domain.Publisher;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PublisherRepositoryIT {

    @Autowired
    private PublisherRepository repository;

    @Test
    void save_insertsAndFindByIdReturnsIt() {
        Publisher publisher = new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now());

        repository.save(publisher);

        Publisher reloaded = repository.findById(publisher.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Дар Тайба");
    }

    @Test
    void findByName_returnsRowWhenExists() {
        Publisher publisher = repository.save(new Publisher(
                UUID.randomUUID(), "Дар аль-Фикр", Instant.now()
        ));

        assertThat(repository.findByName("Дар аль-Фикр"))
                .isPresent()
                .map(Publisher::id)
                .hasValue(publisher.id());
    }

    @Test
    void findByName_returnsEmptyWhenAbsent() {
        assertThat(repository.findByName("Несуществующее издательство")).isEmpty();
    }

    @Test
    void findOrCreate_returnsExistingIdWhenPresent() {
        Publisher publisher = repository.save(new Publisher(
                UUID.randomUUID(), "Дар Ибн Хазм", Instant.now()
        ));

        UUID id = repository.findOrCreate("Дар Ибн Хазм");

        assertThat(id).isEqualTo(publisher.id());
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void findOrCreate_createsNewRowWhenAbsent() {
        UUID id = repository.findOrCreate("Дар аль-Кутуб аль-Ильмия");

        assertThat(repository.findById(id))
                .isPresent()
                .map(Publisher::name)
                .hasValue("Дар аль-Кутуб аль-Ильмия");
    }

    @Test
    void save_uniqueNameViolation_throws() {
        repository.save(new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now()));

        assertThatThrownBy(() ->
                repository.save(new Publisher(UUID.randomUUID(), "Дар Тайба", Instant.now()))
        ).isInstanceOf(DuplicateKeyException.class);
    }
}
