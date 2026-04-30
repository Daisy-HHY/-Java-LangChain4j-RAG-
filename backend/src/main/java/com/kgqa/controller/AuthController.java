package com.kgqa.controller;

import com.kgqa.config.TokenAuthInterceptor;
import com.kgqa.model.dto.LoginRequest;
import com.kgqa.model.dto.LoginResponse;
import com.kgqa.model.dto.RegisterRequest;
import com.kgqa.model.dto.UserProfile;
import com.kgqa.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String AUTH_COOKIE_NAME = "kgqa_auth_token";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(AuthService authService,
                          @Value("${kgqa.auth.cookie-secure:false}") boolean cookieSecure,
                          @Value("${kgqa.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            LoginResponse loginResponse = authService.login(request);
            issueAuthCookie(response, loginResponse);
            return withoutToken(loginResponse);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/register")
    public LoginResponse register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        try {
            LoginResponse registerResponse = authService.register(request);
            issueAuthCookie(response, registerResponse);
            return withoutToken(registerResponse);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/me")
    public UserProfile me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(TokenAuthInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return authService.currentUser(userId);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return Map.of("success", true);
    }

    private void issueAuthCookie(HttpServletResponse response, LoginResponse loginResponse) {
        long maxAgeSeconds = Math.max(0, (loginResponse.getExpiresAt() - System.currentTimeMillis()) / 1000);
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, loginResponse.getToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private LoginResponse withoutToken(LoginResponse response) {
        return new LoginResponse(null, response.getExpiresAt(), response.getUser());
    }
}
