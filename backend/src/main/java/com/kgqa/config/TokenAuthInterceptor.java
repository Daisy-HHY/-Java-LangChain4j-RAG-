package com.kgqa.config;

import com.kgqa.model.entity.AppUser;
import com.kgqa.controller.AuthController;
import com.kgqa.repository.AppUserMapper;
import com.kgqa.service.auth.AuthTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenAuthInterceptor implements HandlerInterceptor {
    public static final String USER_ID_ATTRIBUTE = "authUserId";
    public static final String USERNAME_ATTRIBUTE = "authUsername";
    public static final String ROLE_ATTRIBUTE = "authRole";

    private final AuthTokenService authTokenService;
    private final AppUserMapper appUserMapper;

    public TokenAuthInterceptor(AuthTokenService authTokenService, AppUserMapper appUserMapper) {
        this.authTokenService = authTokenService;
        this.appUserMapper = appUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveBearerToken(request);
        try {
            AuthTokenService.TokenClaims claims = authTokenService.parseToken(token);
            AppUser user = appUserMapper.selectById(claims.userId());
            if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
                throw new IllegalArgumentException("User is disabled or not found");
            }
            request.setAttribute(USER_ID_ATTRIBUTE, claims.userId());
            request.setAttribute(USERNAME_ATTRIBUTE, claims.username());
            request.setAttribute(ROLE_ATTRIBUTE, claims.role());
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"未登录或登录已过期\"}");
            return false;
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AuthController.AUTH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
