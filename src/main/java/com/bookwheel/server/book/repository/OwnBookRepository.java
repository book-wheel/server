package com.bookwheel.server.book.repository;

import com.bookwheel.server.book.entity.OwnBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OwnBookRepository extends JpaRepository<OwnBook, String> {
    // 그룹 ID와 사용자 PK로 소유 도서 존재 여부 확인
    boolean existsByGroup_GroupIdAndOwner_Id(String groupId, String userPK);

    // 그룹 ID와 도서 ISBN으로 그룹 내 도서 등록 여부 확인
    boolean existsByGroup_GroupIdAndBook_Isbn(String groupId, String isbn);

    // 그룹 ID로 해당 그룹에 속한 도서 총 개수 조회
    long countByGroup_GroupId(String groupId);

    // 그룹 ID와 사용자 PK로 특정 소유 도서 정보 단건 조회
    Optional<OwnBook> findByGroup_GroupIdAndOwner_Id(String groupId, String userPK);

    // 도서별 완독 히스토리 조회 시, 요청한 모임에 속한 도서인지 함께 검증한다.
    Optional<OwnBook> findByOwnBookIdAndGroup_GroupId(String ownBookId, String groupId);

    // 리스트 안의 그룹 ID에 속한 책들을 전부 가져오기
    List<OwnBook> findByGroup_GroupIdIn(List<String> groupIds);

    // 홈 독서 카드에 필요한 사용자의 등록 도서와 서지 정보를 모임별로 일괄 조회한다.
    @Query("""
            select ownBook
            from OwnBook ownBook
            join fetch ownBook.group
            join fetch ownBook.book
            where ownBook.owner.id = :userPK
              and ownBook.group.groupId in :groupIds
            """)
    List<OwnBook> findAllByOwnerIdAndGroupIdInWithBook(
            @Param("userPK") String userPK,
            @Param("groupIds") Collection<String> groupIds
    );

    @Modifying
    @Query("delete from OwnBook ownBook where ownBook.group.groupId = :groupId")
    // 모임 삭제 전에 해당 모임의 참여 도서만 일괄 제거한다.
    void deleteAllByGroupId(@Param("groupId") String groupId);
}
