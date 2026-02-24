package com.bookwheel.server.schedule.Repository;

import com.bookwheel.server.schedule.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    void deleteByGroup_GroupId(String groupId);
    List<Round> findByGroup_GroupIdOrderByRoundNumberAsc(String groupId);
    Optional<Round> findByGroup_GroupIdAndRoundNumber(String groupId, Integer roundNumber);
}
