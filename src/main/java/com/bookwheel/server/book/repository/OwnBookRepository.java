package com.bookwheel.server.book.repository;

import com.bookwheel.server.book.entity.OwnBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnBookRepository extends JpaRepository<OwnBook, String> {
    // 그룹 ID와 소유자 ID로 소유 도서 존재 여부 확인
    boolean existsByGroup_GroupIdAndOwner_Id(String groupId, String ownerId);

    // 그룹 ID와 도서 ISBN으로 그룹 내 도서 등록 여부 확인
    boolean existsByGroup_GroupIdAndBook_Isbn(String groupId, String isbn);

    // 그룹 ID로 해당 그룹에 속한 도서 총 개수 조회
    long countByGroup_GroupId(String groupId);

    // 그룹 ID와 소유자 ID로 특정 소유 도서 정보 단건 조회
    Optional<OwnBook> findByGroup_GroupIdAndOwner_Id(String groupId, String id);

    // 그룹 ID로 해당 그룹의 전체 도서 목록 리스트 조회
    List<OwnBook> findByGroup_GroupId(String groupId);
}
