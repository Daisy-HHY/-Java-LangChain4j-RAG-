package com.kgqa.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kgqa.model.dto.LoginRequest;
import com.kgqa.model.dto.LoginResponse;
import com.kgqa.model.dto.RegisterRequest;
import com.kgqa.model.dto.UserProfile;
import com.kgqa.model.entity.AppUser;
import com.kgqa.repository.AppUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AppUserMapper appUserMapper;
    private final PasswordHasher passwordHasher;
    private final AuthTokenService authTokenService;

    public AuthService(AppUserMapper appUserMapper,
                       PasswordHasher passwordHasher,
                       AuthTokenService authTokenService) {
        this.appUserMapper = appUserMapper;
        this.passwordHasher = passwordHasher;
        this.authTokenService = authTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        AppUser user = findByUsername(request.getUsername().trim());
        if (user == null || Boolean.FALSE.equals(user.getEnabled())
                || !passwordHasher.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        AuthTokenService.TokenIssue tokenIssue = authTokenService.createToken(user);
        return new LoginResponse(tokenIssue.token(), tokenIssue.expiresAt(), UserProfile.from(user));
    }

    public LoginResponse register(RegisterRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        String username = request.getUsername().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线，长度为 3-32 位");
        }
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHasher.hash(request.getPassword()));
        user.setDisplayName(resolveDisplayName(request.getDisplayName(), username));
        user.setRole("USER");
        user.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserMapper.insert(user);

        AuthTokenService.TokenIssue tokenIssue = authTokenService.createToken(user);
        return new LoginResponse(tokenIssue.token(), tokenIssue.expiresAt(), UserProfile.from(user));
    }

    public UserProfile currentUser(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户不存在或已禁用");
        }
        return UserProfile.from(user);
    }

    public AppUser findByUsername(String username) {
        return appUserMapper.selectOne(new QueryWrapper<AppUser>()
                .eq("username", username)
                .last("LIMIT 1"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveDisplayName(String displayName, String username) {
        if (isBlank(displayName)) {
            return username;
        }
        String trimmed = displayName.trim();
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }
}
