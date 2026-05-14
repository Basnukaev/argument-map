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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceRepositoryIT {

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void save_insertsSource_withJsonbMetadata() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Source source = new Source(
                UUID.randomUUID(),
                SourceType.HADITH,
                "Сахих аль-Бухари",
                "том 1, хадис 4",
                Reliability.SAHIH,
                null,
                null,
                "{\"collection\":\"bukhari\",\"book\":1,\"hadith_number\":4}",
                now
        );

        sourceRepository.save(source);

        Optional<Source> found = sourceRepository.findById(source.id());
        assertThat(found).isPresent();
        Source reloaded = found.get();
        assertThat(reloaded.sourceType()).isEqualTo(SourceType.HADITH);
        assertThat(reloaded.reliability()).isEqualTo(Reliability.SAHIH);
        assertThat(reloaded.metadata()).contains("\"collection\"").contains("bukhari");
        assertThat(reloaded.createdAt()).isEqualTo(now);
    }

    @Test
    void save_withNullReliabilityAndMetadata_worksFine() {
        Source source = new Source(
                UUID.randomUUID(), SourceType.BOOK, "Муснад Ахмада",
                null, null, null, null, null, Instant.now()
        );

        sourceRepository.save(source);

        Source reloaded = sourceRepository.findById(source.id()).orElseThrow();
        assertThat(reloaded.reliability()).isNull();
        assertThat(reloaded.metadata()).isNull();
        assertThat(reloaded.citation()).isNull();
        assertThat(reloaded.authorityId()).isNull();
    }

    @Test
    void save_withAuthorityId_persistsLink() {
        Authority author = authorityRepository.save(new Authority(
                UUID.randomUUID(), "Ибн Таймийя",
                null, "VIII в.х.", "hanbali", null, Instant.now(),
                null, null
        ));
        Source source = new Source(
                UUID.randomUUID(), SourceType.BOOK, "Иктида ас-сырат аль-мустакым",
                null, null, author.id(), null, null, Instant.now()
        );

        sourceRepository.save(source);

        Source reloaded = sourceRepository.findById(source.id()).orElseThrow();
        assertThat(reloaded.authorityId()).isEqualTo(author.id());
    }

    @Test
    void deleteAuthority_setsSourceAuthorityIdToNull() {
        Authority author = authorityRepository.save(new Authority(
                UUID.randomUUID(), "Ас-Суюти", null, null, null, null, Instant.now(),
                null, null
        ));
        Source source = sourceRepository.save(new Source(
                UUID.randomUUID(), SourceType.BOOK, "Хусн аль-максыд",
                null, null, author.id(), null, null, Instant.now()
        ));

        authorityRepository.deleteById(author.id());

        Source reloaded = sourceRepository.findById(source.id()).orElseThrow();
        assertThat(reloaded.authorityId()).isNull();
    }

    @Test
    void searchByTitle_findsCaseInsensitive() {
        sourceRepository.save(new Source(
                UUID.randomUUID(), SourceType.BOOK, "Ихьйа улюм ад-дин",
                null, null, null, null, null, Instant.now()
        ));
        sourceRepository.save(new Source(
                UUID.randomUUID(), SourceType.BOOK, "Фатх аль-Бари",
                null, null, null, null, null, Instant.now()
        ));

        List<Source> found = sourceRepository.searchByTitle("ИХЬЙА");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).title()).isEqualTo("Ихьйа улюм ад-дин");
    }

    @Test
    void findAll_returnsInCreatedAtOrder() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Source older = new Source(UUID.randomUUID(), SourceType.URL, "older",
                null, null, null, null, null, base.minusSeconds(60));
        Source newer = new Source(UUID.randomUUID(), SourceType.URL, "newer",
                null, null, null, null, null, base);
        sourceRepository.save(newer);
        sourceRepository.save(older);

        List<Source> all = sourceRepository.findAll();
        assertThat(all).extracting(Source::id).containsExactly(older.id(), newer.id());
    }

    @Test
    void deleteById_removesSource() {
        Source source = new Source(UUID.randomUUID(), SourceType.ARTICLE, "t",
                null, null, null, null, null, Instant.now());
        sourceRepository.save(source);

        boolean deleted = sourceRepository.deleteById(source.id());

        assertThat(deleted).isTrue();
        assertThat(sourceRepository.findById(source.id())).isEmpty();
    }

    @Test
    void metadataJsonb_isQueryableWithJsonbOperators() {
        UUID id = UUID.randomUUID();
        sourceRepository.save(new Source(
                id, SourceType.HADITH, "s",
                null, Reliability.HASAN, null,
                null,
                "{\"book\":1,\"hadith_number\":42}",
                Instant.now()
        ));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sources WHERE metadata @> ?::jsonb",
                Integer.class,
                "{\"hadith_number\":42}"
        );
        assertThat(count).isOne();
    }

}
