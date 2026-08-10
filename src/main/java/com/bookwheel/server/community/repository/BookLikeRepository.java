package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookLike;
import com.bookwheel.server.community.dto.InterestBookResponseDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {

    Optional<BookLike> findByBookInfoAndUserPK(BookInfo bookInfo, String userPK);

    boolean existsByBookInfo_IsbnAndUserPK(String isbn, String userPK);

    long countByBookInfo_Isbn(String isbn);

    long countByUserPK(String userPK);

    @Query("""
            select bi.isbn
            from BookLike bl
            join bl.bookInfo bi
            where bl.userPK = :userPK
            and bi.isbn in :isbns
            """)
    List<String> findInterestedIsbns(
        @Param("userPK") String userPK,
        @Param("isbns") List<String> isbns
    );

    // 찜 시점에 저장해 둔 BookInfo 값을 우선 사용하고, 없으면 모임 도서로 등록된 book 행으로 대체한다.
    // 커서가 null 이면 첫 페이지, 있으면 커서 이후 페이지를 조회한다.
    @Query("""
            select new com.bookwheel.server.community.dto.InterestBookResponseDto(
                bi.bookInfoId,
                bi.isbn,
                coalesce(bi.title, b.title),
                coalesce(bi.author, b.author),
                coalesce(bi.coverImage, b.coverImage),
                bl.createdAt
            )
            from BookLike bl
            join bl.bookInfo bi
            left join Book b on b.isbn = bi.isbn
            where bl.userPK = :userPK
            and (
                :cursorInterestedAt is null
                or bl.createdAt < :cursorInterestedAt
                or (bl.createdAt = :cursorInterestedAt and bi.bookInfoId < :cursorBookId)
            )
            order by bl.createdAt desc, bi.bookInfoId desc
            """)
    List<InterestBookResponseDto> findInterestBooks(
        @Param("userPK") String userPK,
        @Param("cursorInterestedAt") LocalDateTime cursorInterestedAt,
        @Param("cursorBookId") Long cursorBookId,
        Pageable pageable
    );
}
