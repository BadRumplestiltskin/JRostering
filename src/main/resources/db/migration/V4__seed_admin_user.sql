-- =============================================================================
-- V4__seed_admin_user.sql
-- Seeds a default admin user so the application is usable on first run.
--
-- Default credentials:  username = admin   password = admin
--
-- IMPORTANT: Change the password immediately after first login.
-- The hash below is BCrypt (10 rounds) of the string "admin".
-- =============================================================================
INSERT INTO app_user (username, password_hash, active, created_at, updated_at)
VALUES ('admin',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        true,
        NOW(),
        NOW());
