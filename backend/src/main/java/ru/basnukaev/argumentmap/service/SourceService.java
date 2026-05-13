package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidSourceException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final AuthorityRepository authorityRepository;

    public SourceService(SourceRepository sourceRepository,
                         AuthorityRepository authorityRepository) {
        this.sourceRepository = sourceRepository;
        this.authorityRepository = authorityRepository;
    }

    @Transactional
    public Source createSource(SourceType sourceType, String title, String citation,
                               Reliability reliability, UUID authorityId, String metadataJson) {
        if (reliability != null && sourceType != SourceType.HADITH) {
            throw new InvalidSourceException(
                    "поле reliability допустимо только для типа HADITH"
            );
        }
        if (authorityId != null && authorityRepository.findById(authorityId).isEmpty()) {
            throw new AuthorityNotFoundException(authorityId);
        }
        Source source = new Source(
                UUID.randomUUID(), sourceType, title, citation,
                reliability, authorityId, null, metadataJson, Instant.now()
        );
        sourceRepository.save(source);
        return source;
    }

    @Transactional(readOnly = true)
    public Source getSource(UUID id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new SourceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Source> listSources() {
        return sourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Source> searchByTitle(String query) {
        return sourceRepository.searchByTitle(query);
    }

    @Transactional
    public void deleteSource(UUID id) {
        boolean removed = sourceRepository.deleteById(id);
        if (!removed) {
            throw new SourceNotFoundException(id);
        }
    }
}
