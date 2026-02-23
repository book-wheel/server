package com.bookwheel.server.book.repository;

import com.bookwheel.server.book.entity.OwnBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnBookRepository extends JpaRepository<OwnBook, String> {
    boolean existsByGroup_GroupIdAndOwner_Id(String groupId, String ownerId);

    boolean existsByGroup_GroupIdAndBook_Isbn(String groupId, String isbn);

    long countByGroup_GroupId(String groupId);
}
