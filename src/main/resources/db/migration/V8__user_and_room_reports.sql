ALTER TABLE report
    MODIFY COLUMN reason VARCHAR(40) NOT NULL,
    ADD COLUMN description VARCHAR(2000),
    ADD COLUMN target_type ENUM('USER', 'ROOM') NOT NULL DEFAULT 'USER',
    ADD COLUMN target_id BIGINT,
    ADD COLUMN target_snapshot TEXT,
    ADD COLUMN status ENUM('PENDING', 'REVIEWING', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN client_report_id VARCHAR(36);

-- Existing reports are user reports. Preserve their reason, identity and timestamps.
UPDATE report r
LEFT JOIN profile p ON p.user_id = r.reported_id
SET r.target_id = r.reported_id,
    r.target_snapshot = JSON_OBJECT(
        'userId', r.reported_id,
        'nickname', p.nickname,
        'avatarKey', p.avatar_key,
        'bio', p.bio
    );

ALTER TABLE report
    MODIFY COLUMN target_id BIGINT NOT NULL,
    MODIFY COLUMN target_snapshot TEXT NOT NULL,
    DROP FOREIGN KEY fk_report_reported,
    DROP COLUMN reported_id,
    ADD CONSTRAINT uq_report_client UNIQUE (reporter_id, client_report_id),
    ADD INDEX idx_report_review (status, created_at, id),
    ADD INDEX idx_report_target (target_type, target_id, created_at);
