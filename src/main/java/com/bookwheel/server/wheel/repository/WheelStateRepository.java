package com.bookwheel.server.wheel.repository;

import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WheelStateRepository extends JpaRepository<WheelState, String> {
    // 그룹 대시보드 조회 시, '내 책이 현재 라운드에서 어떤 멤버에게 가 있는지(myBookStep)'를 찾을 때 사용
    Optional<WheelState> findFirstByRoundIdAndOwnBook_OwnBookId(String roundId, String ownbookId);

    // 그룹 대시보드 조회 시, '내가 현재 라운드에서 어떤 책을 읽어야 하는지(myStep)'를 찾을 때 사용
    Optional<WheelState> findFirstByRoundIdAndMember_MemberId(String roundId, String memberId);

    boolean existsByRoundId(String roundId);

    boolean existsByRoundIdIn(Collection<String> roundIds);

    List<WheelState> findByRoundIdIn(Collection<String> roundIds);

    void deleteByRoundIdIn(Collection<String> roundIds);

    // 하루가 지났지만, 마감되지 않은 책바퀴(BookWheel)을 찾아내기.
    @Modifying
    @Query("UPDATE WheelState w SET w.wheelState = :status, w.isCompleted = true " +
            "WHERE w.roundId IN :roundIds AND w.isCompleted = false")
    int bulkCloseWheelStates(@Param("roundIds") List<String> roundIds, @Param("status") WheelStatus status);

    // 특정 멤버의 기록 중 COMPLETED 상태인 WheelState 리스트 가져오기
    @Query("SELECT DISTINCT ws FROM WheelState ws " +
            "JOIN FETCH ws.ownBook ob " +
            "JOIN FETCH ob.book b " +
            "LEFT JOIN FETCH ws.authImages " +
            "WHERE ws.member.group.groupId = :groupId " +
            "AND ws.member.user.id = :userPK " +
            "AND ws.wheelState = :status " +
            "ORDER BY ws.reviewedAt DESC")
    List<WheelState> findMyCompletedHistories(
            @Param("groupId") String groupId,
            @Param("userPK") String userPK,
            @Param("status") WheelStatus status
    );

    // 특정 책에 대한 모든 멤버의 완독 기록 조회
    @Query("SELECT DISTINCT ws FROM WheelState ws " +
            "JOIN FETCH ws.member m " +
            "JOIN FETCH m.user u " +
            "LEFT JOIN FETCH ws.authImages " +
            "WHERE ws.ownBook.ownBookId = :ownBookId " +
            "AND m.group.groupId = :groupId " +
            "AND ws.wheelState = :status " +
            "ORDER BY ws.reviewedAt ASC")
    List<WheelState> findAllByOwnBookIdWithMemberAndImages(
            @Param("groupId") String groupId,
            @Param("ownBookId") String ownBookId,
            @Param("status") WheelStatus status
    );
}
