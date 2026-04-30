package com.kgqa.config;

import com.kgqa.service.auth.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final String defaultUsername;
    private final String defaultPassword;

    public AuthSchemaInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            @Value("${kgqa.auth.default-admin.username:admin}") String defaultUsername,
            @Value("${kgqa.auth.default-admin.password:admin123}") String defaultPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(64) UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    display_name VARCHAR(100),
                    role VARCHAR(30) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username)");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ?",
                Integer.class,
                defaultUsername
        );
        if (count != null && count == 0) {
            jdbcTemplate.update("""
                            INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                            VALUES (?, ?, ?, 'ADMIN', TRUE)
                            """,
                    defaultUsername,
                    passwordHasher.hash(defaultPassword),
                    "系统管理员"
            );
        }

        migrateOwnershipColumns();
    }

    private void migrateOwnershipColumns() {
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = ?",
                Long.class,
                defaultUsername
        );

        if (tableExists("chat_session")) {
            jdbcTemplate.execute("ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS user_id BIGINT");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_session_user_updated ON chat_session(user_id, updated_at DESC)");
            if (adminId != null) {
                jdbcTemplate.update("UPDATE chat_session SET user_id = ? WHERE user_id IS NULL", adminId);
            }
        }

        if (tableExists("knowledge_base")) {
            jdbcTemplate.execute("ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS user_id BIGINT");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_user_created ON knowledge_base(user_id, created_at DESC)");
            if (adminId != null) {
                jdbcTemplate.update("UPDATE knowledge_base SET user_id = ? WHERE user_id IS NULL", adminId);
            }
        }
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(exists);
    }
}
