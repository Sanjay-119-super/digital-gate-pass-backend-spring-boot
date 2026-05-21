-- ============================================================
-- V2__add_student_fields.sql
-- Adds department, course, semester, student_mobile, parent_mobile
-- to both the `users` table (student profile) and the
-- `gate_passes` table (snapshot at pass-creation time).
-- ============================================================

-- ── users table ──────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN department    VARCHAR(100) NULL AFTER room_no,
    ADD COLUMN course        VARCHAR(100) NULL AFTER department,
    ADD COLUMN semester      INT          NULL AFTER course,
    ADD COLUMN student_mobile VARCHAR(15) NULL AFTER semester,
    ADD COLUMN parent_mobile  VARCHAR(15) NULL AFTER student_mobile;

-- ── gate_passes table ─────────────────────────────────────────
ALTER TABLE gate_passes
    ADD COLUMN department    VARCHAR(100) NULL AFTER decision_note,
    ADD COLUMN course        VARCHAR(100) NULL AFTER department,
    ADD COLUMN semester      INT          NULL AFTER course,
    ADD COLUMN student_mobile VARCHAR(15) NULL AFTER semester,
    ADD COLUMN parent_mobile  VARCHAR(15) NULL AFTER student_mobile;

-- ============================================================
-- V2__fix_actor_id_nullable.sql
-- Make approval_logs.actor_id nullable so the expiry scheduler
-- can write audit log rows without a fake user ID.
-- actor_id = NULL means the action was system-generated.
-- ============================================================

-- Step 1: Drop the existing NOT NULL + FK constraint
ALTER TABLE approval_logs
DROP FOREIGN KEY fk_al_actor;

-- Step 2: Allow NULL (system actions have no human actor)
ALTER TABLE approval_logs
    MODIFY COLUMN actor_id BIGINT NULL;

-- Step 3: Re-add the FK — ON DELETE SET NULL so if a user is
--         ever removed the log rows are preserved (audit history)
ALTER TABLE approval_logs
    ADD CONSTRAINT fk_al_actor
        FOREIGN KEY (actor_id) REFERENCES users (id)
            ON DELETE SET NULL;