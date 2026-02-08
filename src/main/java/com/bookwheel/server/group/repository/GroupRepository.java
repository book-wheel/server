package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, String> {
    boolean existsByGroupName(String groupName);
}