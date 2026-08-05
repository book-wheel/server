-- Add Data4Library bibliographic metadata used to merge popular books into search results.
-- Hibernate ddl-auto=update can create these columns locally, but apply this explicitly
-- when managing schema changes outside Hibernate.

ALTER TABLE popular_loan_book
    ADD COLUMN title VARCHAR(500) NULL,
    ADD COLUMN author VARCHAR(500) NULL,
    ADD COLUMN publisher VARCHAR(200) NULL,
    ADD COLUMN published_date VARCHAR(20) NULL,
    ADD COLUMN thumbnail VARCHAR(1000) NULL,
    ADD INDEX idx_popular_loan_book_title (title);
