package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookVoteRepository extends JpaRepository<BookVote, Long> {
    Optional<BookVote> findByBookInfoAndUser_Id(BookInfo bookInfo, String userPK);

    long countByBookInfoAndIsRecommended(BookInfo bookInfo, boolean isRecommended);

    // 주어진 사용자 목록의 해당 도서 투표를 한 번에 조회한다. (리뷰 목록 매핑 시 투표별 조회 N+1 방지)
    @Query("select v from BookVote v where v.bookInfo = :bookInfo and v.user.id in :userPKs")
    List<BookVote> findByBookInfoAndUserPKs(@Param("bookInfo") BookInfo bookInfo, @Param("userPKs") List<String> userPKs);

    // 추천/비추천 등록·변경을 단일 원자적 쿼리로 처리한다.
    // 동시 요청으로 (book_info_id, user_id) 유니크 제약이 충돌해도 트랜잭션이 rollback-only가 되지 않도록
    // 애플리케이션 레벨 재조회 대신 MySQL upsert(ON DUPLICATE KEY UPDATE)로 경합을 DB에서 해소한다.
    @Modifying
    @Query(value = "insert into book_vote (book_info_id, user_id, is_recommended, created_at) "
        + "values (:bookInfoId, :userPK, :isRecommended, now()) "
        + "on duplicate key update is_recommended = values(is_recommended)", nativeQuery = true)
    void upsertVote(@Param("bookInfoId") Long bookInfoId,
                    @Param("userPK") String userPK,
                    @Param("isRecommended") boolean isRecommended);
}
