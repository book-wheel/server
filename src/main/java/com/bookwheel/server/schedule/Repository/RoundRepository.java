package com.bookwheel.server.schedule.Repository;

import com.bookwheel.server.schedule.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoundRepository extends JpaRepository<Round, String> {
    void deleteByGroup_GroupId(String groupId);
    List<Round> findByGroup_GroupIdOrderByRoundNumberAsc(String groupId);
}
