package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.AuthTokens;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.exception.EmailAlreadyTakenException;
import ru.basnukaev.argumentmap.exception.InvalidCredentialsException;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;
import ru.basnukaev.argumentmap.exception.UsernameAlreadyTakenException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthServiceIT {

    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    @Test
    void register_validInput_createsUserWithBcryptHash() {
        User u = userService.register("alice@example.com", "alice1", "password1");
        assertThat(u.id()).isNotNull();
        assertThat(u.email()).isEqualTo("alice@example.com");
        assertThat(u.username()).isEqualTo("alice1");
        assertThat(u.passwordHash()).startsWith("$2a$").as("BCrypt hash format");
        assertThat(u.role()).isEqualTo("USER");
        assertThat(u.enabled()).isTrue();
    }

    @Test
    void register_duplicateEmail_throws() {
        userService.register("bob@example.com", "bob1", "password1");
        assertThatThrownBy(() -> userService.register(
                "BOB@example.com", "bob2", "password2"
        )).isInstanceOf(EmailAlreadyTakenException.class);
    }

    @Test
    void register_duplicateUsername_throws() {
        userService.register("first@example.com", "carol", "password1");
        assertThatThrownBy(() -> userService.register(
                "second@example.com", "carol", "password2"
        )).isInstanceOf(UsernameAlreadyTakenException.class);
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        userService.register("dave@example.com", "dave1", "password1");
        AuthTokens tokens = authService.login("dave@example.com", "password1");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        // access короткоживущий, refresh длинноживущий
        assertThat(tokens.accessTokenExpiresAt()).isBefore(tokens.refreshTokenExpiresAt());
        // принципал в access токене должен соответствовать
        var principal = jwtService.validateToken(tokens.accessToken());
        assertThat(principal.email()).isEqualTo("dave@example.com");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        userService.register("eve@example.com", "eve1", "password1");
        assertThatThrownBy(() -> authService.login("eve@example.com", "wrongpass"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        // даже без существующего user'а - тайминг через dummy hash должен сработать
        assertThatThrownBy(() -> authService.login("unknown@example.com", "password1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_disabledUser_throwsInvalidCredentials() {
        User user = userService.register("frank@example.com", "frank1", "password1");
        userRepository.setEnabled(user.id(), false);
        assertThatThrownBy(() -> authService.login("frank@example.com", "password1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refresh_validRefreshToken_returnsNewPair() {
        userService.register("grace@example.com", "grace1", "password1");
        AuthTokens original = authService.login("grace@example.com", "password1");

        AuthTokens refreshed = authService.refresh(original.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        var principal = jwtService.validateToken(refreshed.accessToken());
        assertThat(principal.email()).isEqualTo("grace@example.com");
    }

    @Test
    void refresh_accessTokenInsteadOfRefresh_throws() {
        userService.register("henry@example.com", "henry1", "password1");
        AuthTokens t = authService.login("henry@example.com", "password1");
        assertThatThrownBy(() -> authService.refresh(t.accessToken()))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("refresh-токен");
    }

    @Test
    void refresh_garbageToken_throws() {
        assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
