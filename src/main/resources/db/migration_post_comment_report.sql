CREATE TABLE IF NOT EXISTS post_comment_report (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    reporter_user_pk VARCHAR(50) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (report_id),
    CONSTRAINT uk_comment_report_reporter UNIQUE (comment_id, reporter_user_pk),
    CONSTRAINT fk_comment_report_comment FOREIGN KEY (comment_id)
        REFERENCES post_comment (post_comment_id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_report_reporter FOREIGN KEY (reporter_user_pk)
        REFERENCES users (id) ON DELETE CASCADE
);
