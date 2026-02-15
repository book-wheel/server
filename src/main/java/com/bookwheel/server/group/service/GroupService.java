package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.entity.*;
import com.bookwheel.server.group.repository.*;
import com.bookwheel.server.member.entity.*;
import com.bookwheel.server.member.enums.*;
import com.bookwheel.server.member.repository.*;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupCreateResponse createGroup(GroupCreateRequest request) {
        return createGroup(request, resolveCurrentUserId());
    }

    @Transactional
    public GroupCreateResponse createGroup(GroupCreateRequest request, String userId) {
        validateGroupCreateRequest(request);

        User user = findActiveUserByUserId(userId);
        Group group = request.toEntity();
        Group savedGroup = groupRepository.save(group);

        Member leader = Member.builder()
                .memberId(UUID.randomUUID().toString())
                .group(savedGroup)
                .user(user)
                .memberRole(MemberRole.LEADER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();

        memberRepository.save(leader);
        return GroupCreateResponse.of(savedGroup.getGroupId());
    }

    @Transactional
    public GroupJoinResponse joinGroup(String groupId, GroupJoinRequest request) {
        return joinGroup(groupId, request, resolveCurrentUserId());
    }

    @Transactional
    public GroupJoinResponse joinGroup(String groupId, GroupJoinRequest request, String userId) {
        Group group = findGroupById(groupId);
        User user = findActiveUserByUserId(userId);

        validateJoinRequest(group, request);

        if (memberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_MEMBER);
        }

        Member member = Member.builder()
                .memberId(UUID.randomUUID().toString())
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.PENDING)
                .joinMent(request.joinMent())
                .build();

        Member savedMember = memberRepository.save(member);
        return GroupJoinResponse.of(savedMember.getMemberId(), savedMember.getMemberStatus());
    }

    public Page<GroupSearchResponse> getGroups(GroupSearchCondition condition, Pageable pageable) {
        Page<Group> groupPage = groupRepository.findAll(GroupSpecification.searchWith(condition), pageable);
        return groupPage.map(GroupSearchResponse::from);
    }

    public GroupDetailResponse getGroup(String groupId) {
        Group group = findGroupById(groupId);
        return GroupDetailResponse.from(group);
    }

    public List<MemberRequestResponse> getMemberRequests(String groupId, String leaderUserId) {
        findGroupById(groupId);
        validateLeaderPermission(groupId, leaderUserId);

        return memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.PENDING)
                .stream()
                .map(MemberRequestResponse::from)
                .toList();
    }

    @Transactional
    public MemberRequestStatusUpdateResponse updateMemberRequestStatus(
            String groupId,
            String memberId,
            String leaderUserId,
            MemberRequestStatus status
    ) {
        Group group = findGroupById(groupId);
        validateLeaderPermission(groupId, leaderUserId);

        Member targetMember = memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (targetMember.getMemberStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_PENDING);
        }

        if (status == MemberRequestStatus.APPROVED) {
            if (group.getCurrentMembers() >= group.getMaxMembers()) {
                throw new BusinessException(ErrorCode.GROUP_FULL);
            }
            targetMember.setMemberStatus(MemberStatus.ACTIVE);
        } else {
            targetMember.setMemberStatus(MemberStatus.REJECTED);
        }

        return MemberRequestStatusUpdateResponse.of(targetMember.getMemberId(), status);
    }

    private void validateGroupCreateRequest(GroupCreateRequest request) {
        if (groupRepository.existsByGroupName(request.groupName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }

        if (!request.groupPublic() && !StringUtils.hasText(request.groupPassword())) {
            throw new BusinessException(ErrorCode.GROUP_PASSWORD_REQUIRED);
        }

        if (request.groupOffline() && request.groupRegion() == null) {
            throw new BusinessException(ErrorCode.GROUP_REGION_REQUIRED);
        }
    }

    private void validateJoinRequest(Group group, GroupJoinRequest request) {
        if (!group.isGroupPublic()) {
            if (!StringUtils.hasText(request.password()) || !request.password().equals(group.getGroupPassword())) {
                throw new BusinessException(ErrorCode.INVALID_GROUP_PASSWORD);
            }
        }

        if (group.getCurrentMembers() >= group.getMaxMembers()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }
    }

    private void validateLeaderPermission(String groupId, String leaderUserId) {
        Member leaderMember = memberRepository.findByGroup_GroupIdAndUser_UserId(groupId, leaderUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_LEADER_ONLY));

        boolean isLeader = leaderMember.getMemberRole() == MemberRole.LEADER;
        boolean isActive = leaderMember.getMemberStatus() == MemberStatus.ACTIVE;
        if (!isLeader || !isActive) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_ONLY);
        }
    }

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }

    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return authentication.getName();
    }
}
