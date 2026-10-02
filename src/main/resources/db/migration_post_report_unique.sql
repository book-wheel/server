-- Run once with report writes paused, after backing up post_report.
-- Keep writes paused until the UNIQUE constraint has been added.
SELECT post_post_id, reporter_id, COUNT(*) AS report_count
FROM post_report
GROUP BY post_post_id, reporter_id
HAVING COUNT(*) > 1;

-- There is no creation timestamp; the smallest auto-generated report_id
-- identifies the oldest report. Preserve its original reason.
DELETE duplicate_report
FROM post_report AS duplicate_report
JOIN post_report AS older_report
    ON duplicate_report.post_post_id = older_report.post_post_id
    AND duplicate_report.reporter_id = older_report.reporter_id
    AND duplicate_report.report_id > older_report.report_id;

ALTER TABLE post_report
    ADD CONSTRAINT uk_post_report_reporter UNIQUE (post_post_id, reporter_id);
