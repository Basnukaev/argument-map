package ru.basnukaev.argumentmap.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.AuthTokens;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.exception.InvalidCredentialsException;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Login / refresh flow (ADR-040). Выдаёт пары access+refresh.
 *
 * <p>login проверяет пароль и enabled, выдаёт оба токена.
 * refresh принимает refresh-токен, проверяет тип, возвращает новую
 * пару (новый access + reuse того же refresh). Rotation refresh
 * отложена - см. ADR-040 «Открытые вопросы».
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // Сравним hash с любой константой чтобы не дать timing-leak
                    // "email не зарегистрирован" vs "пароль неверный"
                    passwordEncoder.matches(rawPassword, "$2a$10$DummyHashForTimingProtectionOnly");
                    return new InvalidCredentialsException("Неверный email или пароль");
                });

        if (user.passwordHash() == null) {
            // legacy X-User-Id user без password (см. ADR-040 transitional)
            throw new InvalidCredentialsException("Пароль не установлен для пользователя");
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException("Неверный email или пароль");
        }
        if (!user.enabled()) {
            throw new InvalidCredentialsException("Аккаунт деактивирован");
        }

        return issueTokenPair(user);
    }

    @Transactional(readOnly = true)
    public AuthTokens refresh(String refreshToken) {
        String tokenType = jwtService.extractTokenType(refreshToken);
        if (!JwtService.TYPE_REFRESH.equals(tokenType)) {
            throw new InvalidTokenException("Ожидается refresh-токен, получен: " + tokenType);
        }
        AuthenticatedUser principal = jwtService.validateToken(refreshToken);
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new InvalidTokenException("Пользователь из refresh-токена не существует"));
        if (!user.enabled()) {
            throw new InvalidTokenException("Аккаунт деактивирован");
        }
        return issueTokenPair(user);
    }

    private AuthTokens issueTokenPair(User user) {
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        return new AuthTokens(
                access,
                jwtService.accessTokenExpiry(),
                refresh,
                jwtService.refreshTokenExpiry()
        );
    }
}
