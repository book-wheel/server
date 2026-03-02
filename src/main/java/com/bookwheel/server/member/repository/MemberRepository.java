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
    // 특정 그룹에 특정 사용자가 이미 멤버로 등록되어 있는지 확인
    // 특정 사용자가 해당 그룹에 이미 가입했거나 신청 중인지 중복 여부를 확인할 때 사용
    boolean existsByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    // 특정 그룹 안에서 이 사용자가 '어떤 멤버'로 등록되어 있는지 단 한 명의 정보 조회
    // 로그인한 '나(User)'의 정보를 기준으로 해당 그룹에서의 멤버 프로필을 찾는 용도
    Optional<Member> findByGroup_GroupIdAndUser_UserId(String groupId, String userId);

    // 모임에 속한 특정 멤버 한 명을 조회하는 기능
    // 특정 멤버의 '가입 번호(MemberId)'를 알고 있을 때, 그 멤버가 우리 그룹 소속이 맞는지 콕 집어 확인할 때 사용
    Optional<Member> findByMemberIdAndGroup_GroupId(String memberId, String groupId);

    // 특정 그룹 내 특정 상태(ex. PENDING, ACTIVE)의 멤버 목록 조회
    List<Member> findByGroup_GroupIdAndMemberStatus(String groupId, MemberStatus memberStatus);

    // 특정 그룹의 읽는 순서대로 멤버 목록 조회
    List<Member> findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(String groupId, MemberStatus memberStatus);

    // 모임 멤버들의 명단을 불러오는 기능
    // 독서 순서를 바꾸는 동안 다른 사람이 건드리지 못하게 잠금(Lock)을 걸고 이름 정보(User)까지 한 번에 조회
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

    // 해당 그룹 안에 특정 상태의 멤버들을 정렬해서 전부 가져오기
    List<Member> findByGroup_GroupIdInAndMemberStatusOrderByReadOrderAsc(List<String> groupIds, MemberStatus memberStatus);
}