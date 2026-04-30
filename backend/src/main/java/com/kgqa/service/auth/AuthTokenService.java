package com.kgqa.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kgqa.model.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(
            ObjectMapper objectMapper,
            @Value("${kgqa.auth.token-secret:kgqa-local-development-secret-change-me}") String tokenSecret,
            @Value("${kgqa.auth.token-ttl-seconds:86400}") long ttlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public TokenIssue createToken(AppUser user) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", user.getRole());
        payload.put("exp", expiresAt);

        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedPayload);
        return new TokenIssue(encodedPayload + "." + signature, expiresAt);
    }

    public TokenClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is empty");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
            throw new IllegalArgumentException("Token signature is invalid");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    URL_DECODER.decode(parts[0]),
                    new TypeReference<>() {
                    }
            );
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (expiresAt < Instant.now().toEpochMilli()) {
                throw new IllegalArgumentException("Token expired");
            }
            return new TokenClaims(
                    ((Number) payload.get("uid")).longValue(),
                    String.valueOf(payload.get("username")),
                    String.valueOf(payload.get("role")),
                    expiresAt
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token payload is invalid", e);
        }
    }

    private String encodeJson(Map<String, Object> payload) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create token", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }
        return result == 0;
    }

    public record TokenIssue(String token, Long expiresAt) {
    }

    public record TokenClaims(Long userId, String username, String role, Long expiresAt) {
    }
}
