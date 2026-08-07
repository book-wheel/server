package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    boolean existsByBookInfoAndReviewer_Id(BookInfo bookInfo, String userPK);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookReview r where r.reviewId = :reviewId")
    // 리뷰 삭제와 그 리뷰를 가리키는 비동기 알림 저장을 같은 리뷰 단위로 직렬화한다.
    Optional<BookReview> findByReviewIdForUpdate(@Param("reviewId") Long reviewId);

    // 작성자(reviewer)를 fetch join으로 함께 로딩해 목록 매핑 시 리뷰어 조회 N+1을 방지한다.
    @Query(value = "select r from BookReview r join fetch r.reviewer where r.bookInfo = :bookInfo",
        countQuery = "select count(r) from BookReview r where r.bookInfo = :bookInfo")
    Page<BookReview> findAllByBookInfo(@Param("bookInfo") BookInfo bookInfo, Pageable pageable);

    @Query("""
            select r
            from BookReview r
            join fetch r.reviewer
            where r.bookInfo.isbn = :isbn
            and r.isHidden = false
            order by r.likeCount desc, r.createdAt desc
            """)
    List<BookReview> findRepresentativePublicReviewByIsbn(
        @Param("isbn") String isbn,
        Pageable pageable
    );
}
