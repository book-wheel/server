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

    long countByUserPK(String userPK);

    @Query("""
            select new com.bookwheel.server.community.dto.InterestBookResponseDto(
                bi.bookInfoId,
                b.title,
                b.author,
                b.coverImage,
                bl.createdAt
            )
            from BookLike bl
            join bl.bookInfo bi
            left join Book b on b.isbn = bi.isbn
            where bl.userPK = :userPK
            order by bl.createdAt desc, bi.bookInfoId desc
            """)
    List<InterestBookResponseDto> findInterestBooksFirstPage(
        @Param("userPK") String userPK,
        Pageable pageable
    );

    @Query("""
            select new com.bookwheel.server.community.dto.InterestBookResponseDto(
                bi.bookInfoId,
                b.title,
                b.author,
                b.coverImage,
                bl.createdAt
            )
            from BookLike bl
            join bl.bookInfo bi
            left join Book b on b.isbn = bi.isbn
            where bl.userPK = :userPK
            and (
                bl.createdAt < :cursorInterestedAt
                or (bl.createdAt = :cursorInterestedAt and bi.bookInfoId < :cursorBookId)
            )
            order by bl.createdAt desc, bi.bookInfoId desc
            """)
    List<InterestBookResponseDto> findInterestBooksAfterCursor(
        @Param("userPK") String userPK,
        @Param("cursorInterestedAt") LocalDateTime cursorInterestedAt,
        @Param("cursorBookId") Long cursorBookId,
        Pageable pageable
    );
}
