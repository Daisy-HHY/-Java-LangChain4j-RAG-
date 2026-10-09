package com.kgqa.config;

import com.kgqa.model.entity.AppUser;
import com.kgqa.repository.AppUserMapper;
import com.kgqa.service.auth.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenAuthInterceptorTest {
    @Test
    void roleComesFromCurrentUserInsteadOfStaleToken() throws Exception {
        var tokens = mock(AuthTokenService.class);
        var users = mock(AppUserMapper.class);
        var user = new AppUser();
        user.setId(7L);
        user.setEnabled(true);
        user.setRole("USER");
        when(tokens.parseToken("token"))
                .thenReturn(new AuthTokenService.TokenClaims(7L, "name", "ADMIN", Long.MAX_VALUE));
        when(users.selectById(7L)).thenReturn(user);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        assertTrue(new TokenAuthInterceptor(tokens, users)
                .preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals("USER", request.getAttribute(TokenAuthInterceptor.ROLE_ATTRIBUTE));
    }
}
