package ru.basnukaev.argumentmap.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.service.UserService;

/**
 * IT для {@link UserRepository}. Изначально создан для покрытия bulk
 * {@code findByIds} (Code review round 3, #1) - устраняет N+1 lookup
 * username'ов в {@code AuditLogController}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryIT {

    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;

    @Test
    void findByIds_existingIds_returnsAllUsers() {
        User a = userService.register("a@example.com", "alpha", "password1");
        User b = userService.register("b@example.com", "bravo", "password1");
        User c = userService.register("c@example.com", "charlie", "password1");

        Map<UUID, User> result = userRepository.findByIds(
                List.of(a.id(), b.id(), c.id()));

        assertThat(result).hasSize(3);
        assertThat(result.get(a.id()).username()).isEqualTo("alpha");
        assertThat(result.get(b.id()).username()).isEqualTo("bravo");
        assertThat(result.get(c.id()).username()).isEqualTo("charlie");
    }

    @Test
    void findByIds_emptyInput_returnsEmptyMap() {
        Map<UUID, User> result = userRepository.findByIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIds_nullInput_returnsEmptyMap() {
        Map<UUID, User> result = userRepository.findByIds(null);

        assertThat(result).isEmpty();
    }

    @Test
    void findByIds_mixOfExistingAndMissing_returnsOnlyExisting() {
        User real = userService.register("real@example.com", "real-user", "password1");
        UUID phantom = UUID.randomUUID();

        Map<UUID, User> result = userRepository.findByIds(Set.of(real.id(), phantom));

        assertThat(result).hasSize(1);
        assertThat(result.get(real.id()).username()).isEqualTo("real-user");
        assertThat(result.get(phantom)).isNull();
    }

    @Test
    void findByIds_duplicateIdsInInput_returnsSingleEntry() {
        User u = userService.register("dup@example.com", "duplicate", "password1");

        // Collection с дубликатами - SQL IN (?, ?) с одинаковыми значениями
        // ок, в результате одна запись (Map не допускает duplicate keys)
        Map<UUID, User> result = userRepository.findByIds(List.of(u.id(), u.id()));

        assertThat(result).hasSize(1);
        assertThat(result.get(u.id()).username()).isEqualTo("duplicate");
    }
}
