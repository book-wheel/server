package com.bookwheel.server.member.repository;

import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {
    boolean existsByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    Optional<Member> findByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    List<Member> findByGroup_GroupIdAndMemberStatus(String groupId, MemberStatus memberStatus);

    Optional<Member> findByMemberIdAndGroup_GroupId(String memberId, String groupId);
}
