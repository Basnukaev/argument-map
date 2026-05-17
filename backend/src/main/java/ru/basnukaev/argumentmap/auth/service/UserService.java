package ru.basnukaev.argumentmap.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.exception.EmailAlreadyTakenException;
import ru.basnukaev.argumentmap.exception.UserNotFoundException;
import ru.basnukaev.argumentmap.exception.UsernameAlreadyTakenException;

/**
 * Управление пользователями (ADR-040). Не отвечает за login flow -
 * это {@link AuthService}. UserService - registration / lookup /
 * enable-disable / password change. Все мутирующие методы транзакционны.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Регистрация нового пользователя. Email + username проверяются на
     * уникальность до записи. Password хешируется BCrypt. Role по
     * умолчанию USER.
     */
    @Transactional
    public User register(String email, String username, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyTakenException("Email уже зарегистрирован: " + email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyTakenException("Имя пользователя занято: " + username);
        }
        Instant now = Instant.now();
        User user = new User(
                UUID.randomUUID(),
                username,
                email,
                passwordEncoder.encode(rawPassword),
                UserRole.USER,
                true,
                now,
                now
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + id));
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException("Пользователь не найден: " + userId);
        }
        userRepository.setEnabled(userId, enabled);
    }
}
