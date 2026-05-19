package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.AuthorityType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidAuthorityTypeException;

/**
 * IT для {@link AuthorityService} - покрывает create/get/list/search/delete +
 * pagination с фильтрами. Эти пути не были покрыты до coverage audit
 * 2026-05-19 (service класс имел 0 прямых тестов, косвенное покрытие шло
 * через AuthorityControllerIT). Здесь - explicit service-level scenarios.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorityServiceIT {

    @Autowired
    private AuthorityService authorityService;

    @Test
    void createAuthority_persistsAllFieldsAndGeneratesId() {
        Authority saved = authorityService.createAuthority(
                "Ибн Таймия",
                "знаменитый учёный",
                "VIII век хиджры",
                "ханбалитский",
                "{\"source\":\"manual\"}"
        );

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("Ибн Таймия");
        assertThat(saved.bio()).isEqualTo("знаменитый учёный");
        assertThat(saved.era()).isEqualTo("VIII век хиджры");
        assertThat(saved.madhab()).isEqualTo("ханбалитский");
        assertThat(saved.createdAt()).isNotNull();
        // Round-trip - персистенция через get
        Authority loaded = authorityService.getAuthority(saved.id());
        assertThat(loaded.name()).isEqualTo("Ибн Таймия");
    }

    @Test
    void getAuthority_whenMissing_throwsAuthorityNotFoundException() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> authorityService.getAuthority(missing))
                .isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void deleteAuthority_removesAndSubsequentGetFails() {
        Authority a = authorityService.createAuthority("Имам Малик", null, null, "маликитский", null);
        authorityService.deleteAuthority(a.id());
        assertThatThrownBy(() -> authorityService.getAuthority(a.id()))
                .isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void deleteAuthority_whenMissing_throws() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> authorityService.deleteAuthority(missing))
                .isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void searchByName_returnsCaseInsensitiveMatches() {
        authorityService.createAuthority("Аль-Бухари", null, null, null, null);
        authorityService.createAuthority("Муслим ибн аль-Хаджадж", null, null, null, null);
        authorityService.createAuthority("Имам Ахмад", null, null, null, null);

        List<Authority> matches = authorityService.searchByName("бухари");
        assertThat(matches).extracting(Authority::name).contains("Аль-Бухари");
    }

    @Test
    void listPage_paginatesAndCountFiltered_areConsistent() {
        // 3 учёных одной эпохи + 2 другой → проверяем фильтр era и count
        authorityService.createAuthority("Учёный-A", null, "X-эпоха", null, null);
        authorityService.createAuthority("Учёный-B", null, "X-эпоха", null, null);
        authorityService.createAuthority("Учёный-C", null, "X-эпоха", null, null);
        authorityService.createAuthority("Учёный-D", null, "Y-эпоха", null, null);
        authorityService.createAuthority("Учёный-E", null, "Y-эпоха", null, null);

        List<Authority> page = authorityService.listPage(null, "X-эпоха", 10, 0);
        long total = authorityService.countFiltered(null, "X-эпоха");
        assertThat(page).hasSize(3);
        assertThat(total).isEqualTo(3L);
        assertThat(page).allSatisfy(a -> assertThat(a.era()).isEqualTo("X-эпоха"));
    }

    @Test
    void listPage_combinedFiltersQueryAndEra_intersectMatches() {
        authorityService.createAuthority("Алим-первый", null, "ранняя", null, null);
        authorityService.createAuthority("Алим-второй", null, "поздняя", null, null);
        authorityService.createAuthority("Другой", null, "ранняя", null, null);

        List<Authority> page = authorityService.listPage("Алим", "ранняя", 10, 0);
        long total = authorityService.countFiltered("Алим", "ранняя");
        // Из 3 ровно один матчит и query 'Алим', и era 'ранняя'
        assertThat(page).hasSize(1);
        assertThat(total).isEqualTo(1L);
        assertThat(page.get(0).name()).isEqualTo("Алим-первый");
    }

    // ─── updateAuthority ──────────────────────────────────────────────────────

    @Test
    void updateAuthority_allFields_updates() {
        Authority created = authorityService.createAuthority(
                "Имя-ДО", "Био-ДО", "Эпоха-ДО", "Мазхаб-ДО",
                "{\"k\":\"v\"}", AuthorityType.SCHOLAR
        );

        Authority updated = authorityService.updateAuthority(
                created.id(),
                "Имя-ПОСЛЕ", "Био-ПОСЛЕ", "Эпоха-ПОСЛЕ", "Мазхаб-ПОСЛЕ",
                AuthorityType.MUHAQQIQ, "{\"k\":\"new\"}"
        );

        assertThat(updated.name()).isEqualTo("Имя-ПОСЛЕ");
        assertThat(updated.bio()).isEqualTo("Био-ПОСЛЕ");
        assertThat(updated.era()).isEqualTo("Эпоха-ПОСЛЕ");
        assertThat(updated.madhab()).isEqualTo("Мазхаб-ПОСЛЕ");
        assertThat(updated.type()).isEqualTo(AuthorityType.MUHAQQIQ);
        // Простая проверка что metadata persisted (не строгий JSON parse)
        assertThat(updated.metadata()).contains("new");
    }

    @Test
    void updateAuthority_partialName_keepsOtherFields() {
        Authority created = authorityService.createAuthority(
                "Имя-ДО", "Bio-stable", "Эпоха-stable", "ханбалитский",
                null, AuthorityType.SCHOLAR
        );

        // Обновляем только name; все остальные поля null → COALESCE оставит
        Authority updated = authorityService.updateAuthority(
                created.id(), "Имя-НОВОЕ", null, null, null, null, null
        );

        assertThat(updated.name()).isEqualTo("Имя-НОВОЕ");
        assertThat(updated.bio()).isEqualTo("Bio-stable");
        assertThat(updated.era()).isEqualTo("Эпоха-stable");
        assertThat(updated.madhab()).isEqualTo("ханбалитский");
        assertThat(updated.type()).isEqualTo(AuthorityType.SCHOLAR);
    }

    @Test
    void updateAuthority_invalidType_throwsInvalidAuthorityTypeException() {
        Authority created = authorityService.createAuthority(
                "Имам Малик", null, null, null, null
        );

        assertThatThrownBy(() -> authorityService.updateAuthority(
                created.id(), null, null, null, null, "INVALID", null
        )).isInstanceOf(InvalidAuthorityTypeException.class);
    }

    @Test
    void updateAuthority_validTypeChange_persists() {
        Authority created = authorityService.createAuthority(
                "Ибн Кудама", null, null, null, null, AuthorityType.SCHOLAR
        );
        assertThat(created.type()).isEqualTo(AuthorityType.SCHOLAR);

        Authority updated = authorityService.updateAuthority(
                created.id(), null, null, null, null, AuthorityType.MUHAQQIQ, null
        );
        assertThat(updated.type()).isEqualTo(AuthorityType.MUHAQQIQ);
    }

    @Test
    void updateAuthority_notFound_throwsAuthorityNotFoundException() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> authorityService.updateAuthority(
                missing, "Имя", null, null, null, null, null
        )).isInstanceOf(AuthorityNotFoundException.class);
    }
}
