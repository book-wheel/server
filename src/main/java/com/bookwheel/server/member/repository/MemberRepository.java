package com.bookwheel.server.member.repository;

import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {
    boolean existsByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    Optional<Member> findByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    List<Member> findByGroup_GroupIdAndMemberStatus(String groupId, MemberStatus memberStatus);

    List<Member> findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(String groupId, MemberStatus memberStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select m
            from Member m
            where m.group.groupId = :groupId
              and m.memberStatus = :memberStatus
            """)
    List<Member> findByGroupIdAndMemberStatusForUpdate(
            @Param("groupId") String groupId,
            @Param("memberStatus") MemberStatus memberStatus
    );

    Optional<Member> findByMemberIdAndGroup_GroupId(String memberId, String groupId);
}
