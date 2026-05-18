ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_otp VARCHAR(10) NULL;
ALTER TABLE users ADD COLUMN otp_expiry TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN reset_token VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN reset_token_expiry TIMESTAMP NULL;
-- agar password reset bhi add kiya hai to upar wali do lines bhi daal