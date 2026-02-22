package com.bookwheel.server.wheel.repository;

import com.bookwheel.server.wheel.entity.WheelState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
public interface WheelStateRepository extends JpaRepository<WheelState, String> {
    Optional<WheelState> findByBookIdAndStatus(String roundId, String memberId);

    List<WheelState> findByRoundIdAndOwnBook_OwnBookId(String roundId, String ownbookId);

    Optional<WheelState> findFirstByRoundIdAndOwnBook_OwnBookId(String roundId, String ownbookId);
}
