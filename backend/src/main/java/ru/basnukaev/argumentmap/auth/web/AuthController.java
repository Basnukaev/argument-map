package ru.basnukaev.argumentmap.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.domain.AuthTokens;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.service.AuthService;
import ru.basnukaev.argumentmap.auth.service.JwtService;
import ru.basnukaev.argumentmap.auth.service.UserService;
import ru.basnukaev.argumentmap.auth.web.dto.AuthResponse;
import ru.basnukaev.argumentmap.auth.web.dto.LoginRequest;
import ru.basnukaev.argumentmap.auth.web.dto.MeResponse;
import ru.basnukaev.argumentmap.auth.web.dto.RegisterRequest;
import ru.basnukaev.argumentmap.exception.InvalidCredentialsException;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Auth endpoints (ADR-040). Контракт:
 * <ul>
 *   <li>POST /register - регистрация + сразу login (выдача токенов)
 *   <li>POST /login - выдача access (body) + refresh (httpOnly cookie)
 *   <li>POST /refresh - обмен refresh-cookie на новый access
 *   <li>POST /logout - очистка refresh cookie (access короткоживущий, blacklist не делаем - см. ADR-040)
 *   <li>GET /me - текущий пользователь (требует authenticated)
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * Имя cookie с refresh токеном. SameSite=Strict, HttpOnly, Secure
     * (для prod). Path / - доступен всем endpoint'ам, в т.ч. /refresh.
     */
    public static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(AuthService authService,
                          UserService userService,
                          JwtService jwtService) {
        this.authService = authService;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.email(), request.username(), request.password());
        AuthTokens tokens = authService.login(request.email(), request.password());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Set-Cookie", buildRefreshCookie(tokens.refreshToken()).toString())
                .body(toAuthResponse(tokens, user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokens tokens = authService.login(request.email(), request.password());
        AuthenticatedUser principal = jwtService.validateToken(tokens.accessToken());
        AuthResponse body = new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                new AuthResponse.UserInfo(principal.id(), principal.username(),
                        principal.email(), principal.role())
        );
        return ResponseEntity.ok()
                .header("Set-Cookie", buildRefreshCookie(tokens.refreshToken()).toString())
                .body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh-cookie отсутствует");
        }
        // ADR-047: single-use rotation - старый refresh revoked, выдаётся
        // новый. Set-Cookie с новым значением заменяет cookie в browser
        AuthTokens tokens = authService.refresh(refreshToken);
        AuthenticatedUser principal = jwtService.validateToken(tokens.accessToken());
        AuthResponse body = new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                new AuthResponse.UserInfo(principal.id(), principal.username(),
                        principal.email(), principal.role())
        );
        return ResponseEntity.ok()
                .header("Set-Cookie", buildRefreshCookie(tokens.refreshToken()).toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        // ADR-047: revoke refresh в БД при logout. Идемпотентно - даже если
        // токена нет или уже revoked
        authService.logout(refreshToken);
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header("Set-Cookie", cleared.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        if (authentication == null || principal == null) {
            throw new InvalidCredentialsException("Не аутентифицирован");
        }
        return ResponseEntity.ok(new MeResponse(
                principal.id(), principal.username(), principal.email(), principal.role()
        ));
    }

    private AuthResponse toAuthResponse(AuthTokens tokens, User user) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                new AuthResponse.UserInfo(user.id(), user.username(), user.email(), user.role())
        );
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                // В dev/local browser может слать без HTTPS -
                // Secure=true всё равно работает на localhost в современных
                // браузерах. В prod обязательно HTTPS.
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtService.refreshTokenTtlSeconds())
                .build();
    }
}
