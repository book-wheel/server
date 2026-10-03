-- Run once with report writes paused, after backing up post_report.
-- Keep writes paused until the UNIQUE constraint has been added.
-- Apply before starting the application that maps reporter_user_pk.
-- MySQL 8.0 updates existing indexes and foreign keys during the rename.
ALTER TABLE post_report
    RENAME COLUMN reporter_id TO reporter_user_pk;

SELECT post_post_id, reporter_user_pk, COUNT(*) AS report_count
FROM post_report
GROUP BY post_post_id, reporter_user_pk
HAVING COUNT(*) > 1;

-- There is no creation timestamp; the smallest auto-generated report_id
-- identifies the oldest report. Preserve its original reason.
DELETE duplicate_report
FROM post_report AS duplicate_report
JOIN post_report AS older_report
    ON duplicate_report.post_post_id = older_report.post_post_id
    AND duplicate_report.reporter_user_pk = older_report.reporter_user_pk
    AND duplicate_report.report_id > older_report.report_id;

ALTER TABLE post_report
    ADD CONSTRAINT uk_post_report_reporter UNIQUE (post_post_id, reporter_user_pk);
