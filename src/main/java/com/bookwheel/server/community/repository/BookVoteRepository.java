package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookVoteRepository extends JpaRepository<BookVote, Long> {
    Optional<BookVote> findByBookInfoAndUser_Id(BookInfo bookInfo, String userPK);

    long countByBookInfoAndIsRecommended(BookInfo bookInfo, boolean isRecommended);

    // 주어진 사용자 목록의 해당 도서 투표를 한 번에 조회한다. (리뷰 목록 매핑 시 투표별 조회 N+1 방지)
    @Query("select v from BookVote v where v.bookInfo = :bookInfo and v.user.id in :userIds")
    List<BookVote> findByBookInfoAndUserIds(@Param("bookInfo") BookInfo bookInfo, @Param("userIds") List<String> userIds);
}
