package com.bookwheel.server.member.entity;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_group_user",
                        columnNames = {"group_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id
    @Column(name = "member_id", length = 50)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    private MemberRole memberRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false)
    private MemberStatus memberStatus;

    @Column(name = "read_order")
    private Integer readOrder;

    @CreationTimestamp
    @Column(name = "request_date", updatable = false)
    private LocalDateTime requestDate;

    @Column(name = "join_ment", length = 50)
    private String joinMent;

    // 강퇴
    public void kick() {
        validateActiveMember();
        changeStatusAndRole(MemberStatus.BANNED, MemberRole.OUT);
    }

    // 중도하차
    public void withdraw() {
        validateActiveMember();
        changeStatusAndRole(MemberStatus.WITHDRAWN, MemberRole.OUT);
    }

    // 부리더 승격
    public void promoteToSubLeader() {
        validateActiveMember();
        this.memberRole = MemberRole.SUB_LEADER;
    }

    // 일반 멤버로 변경
    private void demoteToMember() {
        this.memberRole = MemberRole.MEMBER;
    }

    // 본인 role 강등 + 상대 role Leader로.
    public void transferLeaderTo(Member targetMember) {
        demoteToMember();
        targetMember.promoteToLeader();
    }

    // 초대 승인
    public void approve() {
        validatePending();
        changeStatusAndRole(MemberStatus.ACTIVE, MemberRole.MEMBER);
    }

    // 초대 거절
    public void reject() {
        validatePending();
        changeStatusAndRole(MemberStatus.REJECTED, MemberRole.OUT);
    }

    private void promoteToLeader() {
        this.memberRole = MemberRole.LEADER;
    }

    private void changeStatusAndRole(MemberStatus status, MemberRole role) {
        this.memberStatus = status;
        this.memberRole = role;
    }

    private void validatePending() {
        if (this.memberStatus != MemberStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_PENDING);
        }
    }
    private void validateActiveMember() {
        if (this.memberStatus != MemberStatus.ACTIVE || this.memberRole != MemberRole.MEMBER) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ONLY);
        }
    }

    // 그룹 읽기 순서 설정
    public void updateReadOrder(int num) {
        this.readOrder = num;
    }

}
