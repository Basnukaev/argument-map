package ru.basnukaev.argumentmap.auth;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;

/**
 * Создаёт fixed dev user'а при старте в local/dev profile (ADR-040).
 * UUID детерминированный - тот же что во фронте mock'ом до Этапа 21.b.
 * Это позволяет existing dev workflow (curl с X-User-Id, ручные тесты)
 * продолжать работать без login UI.
 *
 * <p>Email: admin@argumentmap.local / Password: admin12345 - можно
 * залогиниться через POST /api/v1/auth/login и получить настоящий JWT
 * прямо сейчас (без frontend), для smoke-теста.
 */
@Component
@Profile({"local", "dev"})
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    public static final UUID DEV_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DEV_USERNAME = "admin";
    public static final String DEV_EMAIL = "admin@argumentmap.local";
    public static final String DEV_PASSWORD = "admin12345";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findById(DEV_USER_ID).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        User user = new User(
                DEV_USER_ID,
                DEV_USERNAME,
                DEV_EMAIL,
                passwordEncoder.encode(DEV_PASSWORD),
                UserRole.ADMIN,
                true,
                now,
                now
        );
        userRepository.save(user);
        log.info("Dev user seeded: {} (UUID {})", DEV_EMAIL, DEV_USER_ID);
    }
}
