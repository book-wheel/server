package com.bookwheel.server.wheel.repository;

import com.bookwheel.server.wheel.entity.WheelState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WheelStateRepository extends JpaRepository<WheelState, String> {
    // 그룹 대시보드 조회 시, '내 책이 현재 라운드에서 어떤 멤버에게 가 있는지(myBookStep)'를 찾을 때 사용
    Optional<WheelState> findFirstByRoundIdAndOwnBook_OwnbookId(String roundId, String ownbookId);
    // 그룹 대시보드 조회 시, '내가 현재 라운드에서 어떤 책을 읽어야 하는지(myStep)'를 찾을 때 사용
    Optional<WheelState> findFirstByRoundIdAndMember_MemberId(String roundId, String memberId);
}