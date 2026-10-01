CREATE TABLE IF NOT EXISTS moderation_report (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20) NOT NULL,
    source_report_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    author_user_pk VARCHAR(50),
    author_nickname VARCHAR(50),
    reporter_user_pk VARCHAR(50),
    reporter_nickname VARCHAR(50),
    content_snapshot TEXT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    created_at DATETIME(6),
    status VARCHAR(20) NOT NULL,
    action VARCHAR(30),
    processed_by_admin_pk VARCHAR(50),
    processed_at DATETIME(6),
    processing_reason VARCHAR(255),
    ban_type VARCHAR(20),
    PRIMARY KEY (report_id),
    CONSTRAINT uk_moderation_source UNIQUE (target_type, source_report_id),
    INDEX idx_moderation_queue (status, target_type, created_at, report_id),
    INDEX idx_moderation_author (author_user_pk),
    INDEX idx_moderation_reporter (reporter_user_pk)
);

INSERT INTO moderation_report (target_type, source_report_id, target_id, post_id,
    author_user_pk, author_nickname, reporter_user_pk, reporter_nickname,
    content_snapshot, reason, created_at, status)
SELECT 'POST', r.report_id, p.post_id, p.post_id, a.id,
    CASE WHEN a.is_active = 1 THEN a.nickname ELSE '탈퇴한 사용자' END,
    u.id, CASE WHEN u.is_active = 1 THEN u.nickname ELSE '탈퇴한 사용자' END,
    COALESCE(p.content, ''), r.reason, NULL, 'PENDING'
FROM post_report r JOIN post p ON p.post_id = r.post_post_id
LEFT JOIN users a ON a.id = p.user_id
JOIN users u ON u.id = r.reporter_id
WHERE NOT EXISTS (SELECT 1 FROM moderation_report m
    WHERE m.target_type = 'POST' AND m.source_report_id = r.report_id);

INSERT INTO moderation_report (target_type, source_report_id, target_id, post_id,
    author_user_pk, author_nickname, reporter_user_pk, reporter_nickname,
    content_snapshot, reason, created_at, status)
SELECT 'COMMENT', r.report_id, c.post_comment_id, c.post_id, a.id,
    CASE WHEN a.is_active = 1 THEN a.nickname ELSE '탈퇴한 사용자' END,
    u.id, CASE WHEN u.is_active = 1 THEN u.nickname ELSE '탈퇴한 사용자' END,
    COALESCE(c.content, ''), r.reason, r.created_at, 'PENDING'
FROM post_comment_report r JOIN post_comment c ON c.post_comment_id = r.comment_id
LEFT JOIN users a ON a.id = c.user_id
JOIN users u ON u.id = r.reporter_user_pk
WHERE NOT EXISTS (SELECT 1 FROM moderation_report m
    WHERE m.target_type = 'COMMENT' AND m.source_report_id = r.report_id);
