package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@Service
public class AuthorityService {

    private final AuthorityRepository authorityRepository;

    public AuthorityService(AuthorityRepository authorityRepository) {
        this.authorityRepository = authorityRepository;
    }

    @Transactional
    public Authority createAuthority(String name, String bio, String era,
                                     String madhab, String metadataJson) {
        Authority authority = new Authority(
                UUID.randomUUID(), name, bio, era, madhab,
                metadataJson, Instant.now(),
                null, null
        );
        authorityRepository.save(authority);
        return authority;
    }

    @Transactional(readOnly = true)
    public Authority getAuthority(UUID id) {
        return authorityRepository.findById(id)
                .orElseThrow(() -> new AuthorityNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Authority> listAuthorities() {
        return authorityRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Authority> searchByName(String query) {
        return authorityRepository.searchByName(query);
    }

    @Transactional(readOnly = true)
    public List<Authority> listPage(String query, String era, int limit, int offset) {
        return authorityRepository.findPage(query, era, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countFiltered(String query, String era) {
        return authorityRepository.countFiltered(query, era);
    }

    @Transactional
    public void deleteAuthority(UUID id) {
        boolean removed = authorityRepository.deleteById(id);
        if (!removed) {
            throw new AuthorityNotFoundException(id);
        }
    }
}
