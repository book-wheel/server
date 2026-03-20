package com.bookwheel.server.admin.repository;

import com.bookwheel.server.admin.entity.Penalty;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PenaltyRepository extends JpaRepository<Penalty, Long> {
    List<Penalty> findByUserOrderByBannedAtDesc(User user);
}
