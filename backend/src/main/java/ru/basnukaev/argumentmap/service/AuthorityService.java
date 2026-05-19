package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.AuthorityType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidAuthorityTypeException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@Service
public class AuthorityService {

    private final AuthorityRepository authorityRepository;

    public AuthorityService(AuthorityRepository authorityRepository) {
        this.authorityRepository = authorityRepository;
    }

    /**
     * Legacy-перегрузка без type - применяется default SCHOLAR.
     * Сохранена для existing callers (тесты, ETL без указания роли).
     */
    @Transactional
    public Authority createAuthority(String name, String bio, String era,
                                     String madhab, String metadataJson) {
        return createAuthority(name, bio, era, madhab, metadataJson, AuthorityType.SCHOLAR);
    }

    /**
     * Создание authority с явным типом. {@code type} проверяется по
     * whitelist {@link AuthorityType#isValid}: невалидный →
     * {@link InvalidAuthorityTypeException} → 400. null → default SCHOLAR
     * (тот же effect что у legacy-перегрузки).
     */
    @Transactional
    public Authority createAuthority(String name, String bio, String era,
                                     String madhab, String metadataJson,
                                     String type) {
        String resolvedType = type == null ? AuthorityType.SCHOLAR : type;
        validateType(resolvedType);
        Authority authority = new Authority(
                UUID.randomUUID(), name, bio, era, madhab,
                metadataJson, Instant.now(),
                null, null, resolvedType
        );
        return authorityRepository.save(authority);
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

    /**
     * Partial update authority. Поля с null остаются без изменений
     * (COALESCE в SQL). {@code type} не-null проверяется по whitelist;
     * невалидный → {@link InvalidAuthorityTypeException} → 400.
     * Не найден по id → {@link AuthorityNotFoundException} → 404.
     *
     * @return обновлённый Authority (перечитанный из БД)
     */
    @Transactional
    public Authority updateAuthority(UUID id, String name, String bio, String era,
                                     String madhab, String type, String metadataJson) {
        if (type != null) {
            validateType(type);
        }
        int affected = authorityRepository.update(id, name, bio, era, madhab, type, metadataJson);
        if (affected == 0) {
            throw new AuthorityNotFoundException(id);
        }
        return authorityRepository.findById(id)
                .orElseThrow(() -> new AuthorityNotFoundException(id));
    }

    @Transactional
    public void deleteAuthority(UUID id) {
        boolean removed = authorityRepository.deleteById(id);
        if (!removed) {
            throw new AuthorityNotFoundException(id);
        }
    }

    /**
     * Валидация type по whitelist {@link AuthorityType}. Вынесена, чтобы
     * createAuthority и updateAuthority не дублировали логику.
     */
    private void validateType(String type) {
        if (!AuthorityType.isValid(type)) {
            throw new InvalidAuthorityTypeException(type);
        }
    }
}
