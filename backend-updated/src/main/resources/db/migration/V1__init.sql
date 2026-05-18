-- ============================================================
-- V1__init.sql
-- Digital Gate Pass System — baseline schema
-- Engine: MySQL 8 / InnoDB
-- All timestamps stored in UTC.
-- ============================================================

-- ── Users ────────────────────────────────────────────────────
CREATE TABLE users (
                       id            BIGINT         NOT NULL AUTO_INCREMENT,
                       email         VARCHAR(190)   NOT NULL,
                       password_hash VARCHAR(255)   NOT NULL,
                       full_name     VARCHAR(150)   NOT NULL,
                       phone         VARCHAR(30)    NULL,
                       enrollment_no VARCHAR(50)    NULL,
                       hostel        VARCHAR(50)    NULL,
                       room_no       VARCHAR(20)    NULL,
                       active        BOOLEAN        NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       PRIMARY KEY (id),
                       UNIQUE KEY uq_users_email         (email),
                       UNIQUE KEY uq_users_enrollment_no (enrollment_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Stores each user's roles as separate rows (STUDENT, WARDEN, SECURITY, ADMIN)
CREATE TABLE user_roles (
                            user_id BIGINT      NOT NULL,
                            role    VARCHAR(20) NOT NULL,
                            PRIMARY KEY (user_id, role),
                            CONSTRAINT fk_ur_user FOREIGN KEY (user_id)
                                REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ── Gate Pass Requests ────────────────────────────────────────
CREATE TABLE gate_passes (
                             id            BIGINT        NOT NULL AUTO_INCREMENT,
                             student_id    BIGINT        NOT NULL,
                             reason        VARCHAR(500)  NOT NULL,
                             destination   VARCHAR(255)  NOT NULL,
                             pass_type     VARCHAR(20)   NOT NULL  COMMENT 'DAY | NIGHT | EMERGENCY | MEDICAL | HOME',
                             leave_at      TIMESTAMP     NOT NULL,
                             return_by     TIMESTAMP     NOT NULL,
                             status        VARCHAR(20)   NOT NULL  DEFAULT 'PENDING'
                                 COMMENT 'PENDING|APPROVED|REJECTED|USED|RETURNED|EXPIRED|CANCELLED',
                             warden_id     BIGINT        NULL,
                             decision_note VARCHAR(500)  NULL,
    -- qr_token is a UUID generated at approval time; used as the lookup key for scanning
                             qr_token      VARCHAR(255)  NULL,
                             used_at       TIMESTAMP     NULL,
                             returned_at   TIMESTAMP     NULL,
    -- version supports JPA optimistic locking (@Version); prevents two wardens
    -- approving/rejecting the same pass simultaneously
                             version       INT           NOT NULL  DEFAULT 0,
                             created_at    TIMESTAMP     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                             updated_at    TIMESTAMP     NOT NULL  DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY (id),
                             UNIQUE KEY uq_gp_qr_token (qr_token),
                             KEY idx_gp_status    (status),
                             KEY idx_gp_student   (student_id),
                             KEY idx_gp_created   (created_at),
                             CONSTRAINT fk_gp_student FOREIGN KEY (student_id) REFERENCES users (id),
                             CONSTRAINT fk_gp_warden  FOREIGN KEY (warden_id)  REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ── Approval / Audit Logs ──────────────────────────────────────
-- Every state change on a gate pass is recorded here for full auditability.
CREATE TABLE approval_logs (
                               id          BIGINT        NOT NULL AUTO_INCREMENT,
                               pass_id     BIGINT        NOT NULL,
                               actor_id    BIGINT        NOT NULL,
                               action      VARCHAR(30)   NOT NULL  COMMENT 'CREATE|APPROVE|REJECT|CANCEL|CHECK_OUT|CHECK_IN|EXPIRE',
                               from_status VARCHAR(20)   NULL,
                               to_status   VARCHAR(20)   NULL,
                               note        VARCHAR(500)  NULL,
                               ip_address  VARCHAR(64)   NULL,
                               created_at  TIMESTAMP     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (id),
                               KEY idx_al_pass (pass_id),
                               CONSTRAINT fk_al_pass  FOREIGN KEY (pass_id)  REFERENCES gate_passes (id) ON DELETE CASCADE,
                               CONSTRAINT fk_al_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ── Refresh Tokens ────────────────────────────────────────────
-- We store only the SHA-256 hash, not the raw token.
-- If the token is stolen and used, revoked=TRUE stops reuse.
CREATE TABLE refresh_tokens (
                                id         BIGINT        NOT NULL AUTO_INCREMENT,
                                user_id    BIGINT        NOT NULL,
                                token_hash VARCHAR(128)  NOT NULL,
                                expires_at TIMESTAMP     NOT NULL,
                                revoked    BOOLEAN       NOT NULL  DEFAULT FALSE,
                                created_at TIMESTAMP     NOT NULL  DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (id),
                                UNIQUE KEY uq_rt_token_hash (token_hash),
                                KEY idx_rt_user (user_id),
                                CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- Seed data: default ADMIN user (password = Admin@1234)
-- BCrypt hash generated with strength 10.
-- CHANGE THE PASSWORD IMMEDIATELY after first login.
-- ============================================================
INSERT INTO users (email, password_hash, full_name, active)
VALUES ('admin@gatepass.edu',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lHuO',
        'System Administrator',
        TRUE);

INSERT INTO user_roles (user_id, role)
VALUES (LAST_INSERT_ID(), 'ADMIN');