package com.bookwheel.server.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "popular_loan_book",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_popular_loan_book_snapshot",
        columnNames = {"isbn", "source", "start_date", "end_date"}
    ),
    indexes = {
        @Index(name = "idx_popular_loan_book_isbn", columnList = "isbn"),
        @Index(name = "idx_popular_loan_book_snapshot", columnList = "source, start_date, end_date"),
        @Index(name = "idx_popular_loan_book_title", columnList = "title")
    }
)
public class PopularLoanBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popular_loan_book_id")
    private Long popularLoanBookId;

    @Column(name = "isbn", nullable = false, length = 20)
    private String isbn;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "author", length = 500)
    private String author;

    @Column(name = "publisher", length = 200)
    private String publisher;

    @Column(name = "published_date", length = 20)
    private String publishedDate;

    @Column(name = "thumbnail", length = 1000)
    private String thumbnail;

    @Column(name = "ranking", nullable = false)
    private Integer rank;

    @Column(name = "loan_count", nullable = false)
    private Integer loanCount;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private PopularLoanBookSource source;
}
