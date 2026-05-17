package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {

    Optional<BookLike> findByBookInfoAndUserPK(BookInfo bookInfo, String userPK);
}
