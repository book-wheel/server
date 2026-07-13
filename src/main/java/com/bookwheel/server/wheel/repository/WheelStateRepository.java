package com.bookwheel.server.wheel.repository;

import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // 미래 배정을 교체하는 동안 인증 처리와 같은 행을 동시에 변경하지 못하게 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WheelState w where w.roundId in :roundIds")
    List<WheelState> findByRoundIdInForUpdate(@Param("roundIds") Collection<String> roundIds);

    List<WheelState> findByRoundId(String roundId);

    // 인증 시작부터 완료 저장까지 같은 WheelState를 단독으로 다룬다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WheelState w where w.wheelStateId = :wheelStateId")
    Optional<WheelState> findByWheelStateIdForUpdate(@Param("wheelStateId") String wheelStateId);

    // 일정 조회에서 저장된 내 배정과 책 정보를 한 번에 조회한다.
    @Query("""
            select ws
            from WheelState ws
            join fetch ws.ownBook ownBook
            join fetch ownBook.book
            join fetch ownBook.owner
            where ws.member.memberId = :memberId
              and ws.roundId in :roundIds
            """)
    List<WheelState> findAllByMemberIdAndRoundIdInWithBook(
            @Param("memberId") String memberId,
            @Param("roundIds") Collection<String> roundIds
    );

    // 라운드별 책의 직전 독자를 계산할 수 있도록 일정에 포함된 전체 배정을 조회한다.
    @Query("""
            select ws
            from WheelState ws
            join fetch ws.member member
            join fetch member.user
            join fetch ws.ownBook ownBook
            where ws.roundId in :roundIds
            """)
    List<WheelState> findAllByRoundIdInWithMemberAndBook(@Param("roundIds") Collection<String> roundIds);

    void deleteByRoundIdIn(Collection<String> roundIds);

    void deleteByRoundIdInAndWheelState(Collection<String> roundIds, WheelStatus wheelState);

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
