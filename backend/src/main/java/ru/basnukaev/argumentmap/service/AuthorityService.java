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
                metadataJson, Instant.now()
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

    @Transactional
    public void deleteAuthority(UUID id) {
        boolean removed = authorityRepository.deleteById(id);
        if (!removed) {
            throw new AuthorityNotFoundException(id);
        }
    }
}
