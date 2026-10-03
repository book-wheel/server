CREATE TABLE IF NOT EXISTS user_block (
    block_id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_pk VARCHAR(50) NOT NULL,
    blocked_user_pk VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (block_id),
    CONSTRAINT uk_user_block_pair UNIQUE (blocker_user_pk, blocked_user_pk),
    INDEX idx_user_block_list (blocker_user_pk, created_at, block_id),
    INDEX idx_user_block_target (blocked_user_pk),
    CONSTRAINT fk_user_block_blocker FOREIGN KEY (blocker_user_pk)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_block_blocked FOREIGN KEY (blocked_user_pk)
        REFERENCES users (id) ON DELETE CASCADE
);
