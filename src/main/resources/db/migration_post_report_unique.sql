SELECT post_post_id, reporter_id, COUNT(*) AS report_count
FROM post_report
GROUP BY post_post_id, reporter_id
HAVING COUNT(*) > 1;

ALTER TABLE post_report
    ADD CONSTRAINT uk_post_report_reporter UNIQUE (post_post_id, reporter_id);
