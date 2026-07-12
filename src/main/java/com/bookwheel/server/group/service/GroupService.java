package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.dto.member.*;
import com.bookwheel.server.group.dto.search.*;
import com.bookwheel.server.group.dto.setting.*;
import com.bookwheel.server.group.entity.*;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.event.GroupJoinDecidedEvent;
import com.bookwheel.server.group.event.GroupJoinRequestedEvent;
import com.bookwheel.server.group.repository.*;
import com.bookwheel.server.member.entity.*;
import com.bookwheel.server.member.enums.*;
import com.bookwheel.server.member.repository.*;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;

    @Transactional
    public GroupCreateResponse createGroup(GroupCreateRequest request, String userPK) {
        validateGroupCreateRequest(request);

        User user = findActiveUserById(userPK);
        Group group = request.toEntity();
        if (!request.groupPublic() && StringUtils.hasText(group.getGroupPassword())) {
            group.updateGroupPassword(passwordEncoder.encode(group.getGroupPassword()));
        }
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
    public GroupJoinResponse joinGroup(String groupId, GroupJoinRequest request, String userPK) {
        Group group = findGroupByIdForUpdate(groupId);
        User user = findActiveUserById(userPK);

        if (group.getGroupState() != State.RECRUITING) {
            throw new BusinessException(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);
        }

        validateJoinRequest(group, request);

        if (memberRepository.existsByGroup_GroupIdAndUser_Id(groupId, userPK)) {
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

        eventPublisher.publishEvent(new GroupJoinRequestedEvent(
                group.getGroupId(),
                group.getGroupName(),
                user.getId(),
                user.getNickname()
        ));

        return GroupJoinResponse.of(savedMember.getMemberId(), savedMember.getMemberStatus());
    }

    public Page<GroupSearchResponse> getGroups(GroupSearchCondition condition, Pageable pageable, String userPK) {
        Page<Group> groupPage = groupRepository.findAll(GroupSpecification.searchWith(condition), pageable);
        // 페이지 내 그룹들의 버튼 상태를 한 번에 계산해 응답에 채운다.
        Map<String, GroupDetailButtonType> bottomButtonTypes = resolveBottomButtonTypes(groupPage.getContent(), userPK);
        return groupPage.map(group -> GroupSearchResponse.from(
                group,
                bottomButtonTypes.getOrDefault(group.getGroupId(), GroupDetailButtonType.JOIN)
        ));
    }

    public List<GroupSearchResponse> getMyGroups(String userPK) {
        List<Group> groups = memberRepository.findGroupsByUserPKAndMemberStatus(userPK, MemberStatus.ACTIVE);
        // 내 모임 목록도 상세/탐색과 같은 버튼 상태 규칙을 재사용한다.
        Map<String, GroupDetailButtonType> bottomButtonTypes = resolveBottomButtonTypes(groups, userPK);

        return groups.stream()
                .map(group -> GroupSearchResponse.from(
                        group,
                        bottomButtonTypes.getOrDefault(group.getGroupId(), GroupDetailButtonType.JOINED)
                ))
                .toList();
    }

    public GroupDetailResponse getGroup(String groupId, String userPK) {
        Group group = findGroupById(groupId);
        GroupDetailButtonType bottomButtonType = resolveBottomButtonType(groupId, userPK);
        return GroupDetailResponse.from(group, bottomButtonType);
    }

    public List<MemberRequestResponse> getMemberRequests(String groupId, String leaderUserPk) {
        findGroupById(groupId);
        memberPermissionValidator.validateLeader(groupId, leaderUserPk);

        return memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.PENDING)
                .stream()
                .map(MemberRequestResponse::from)
                .toList();
    }

    @Transactional
    public MemberRequestStatusUpdateResponse updateMemberRequestStatus(
            String groupId,
            String memberId,
            String leaderUserPk,
            MemberRequestStatus status
    ) {
        Group group = (status == MemberRequestStatus.APPROVED)
                ? findGroupByIdForUpdate(groupId)
                : findGroupById(groupId);
        memberPermissionValidator.validateLeader(groupId, leaderUserPk);

        Member targetMember = memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (targetMember.getMemberStatus() != MemberStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEMBER_REQUEST_NOT_PENDING);
        }

        if (status == MemberRequestStatus.APPROVED) {
            if (group.getGroupState() != State.RECRUITING) {
                throw new BusinessException(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);
            }
            if (group.getCurrentMembers() >= group.getMaxMembers()) {
                throw new BusinessException(ErrorCode.GROUP_FULL);
            }
            // 승인으로 ACTIVE 멤버 수가 달라지므로, 기존 계획표는 승인 전에 폐기한다.
            invalidateGeneratedScheduleIfPresent(group);
            targetMember.approve();
        } else {
            targetMember.reject();
        }

        eventPublisher.publishEvent(new GroupJoinDecidedEvent(
                group.getGroupId(),
                group.getGroupName(),
                targetMember.getUser().getId(),
                status
        ));

        return MemberRequestStatusUpdateResponse.of(targetMember.getMemberId(), status);
    }

    private void invalidateGeneratedScheduleIfPresent(Group group) {
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        if (rounds.isEmpty()) {
            return;
        }

        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();
        List<WheelState> wheelStates = wheelStateRepository.findByRoundIdIn(roundIds);
        boolean hasStartedWheelState = wheelStates.stream()
                .anyMatch(wheelState -> wheelState.getWheelState() != WheelStatus.PLANNED);
        if (hasStartedWheelState) {
            // 진행 기록이 있으면 일정만 삭제해 데이터 연결이 끊기는 일을 막는다.
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_INVALIDATION_BLOCKED_BY_WHEEL_STATE);
        }

        wheelStateRepository.deleteByRoundIdInAndWheelState(roundIds, WheelStatus.PLANNED);
        roundRepository.deleteByGroup_GroupId(group.getGroupId());
        group.invalidateSchedule();
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
            if (!StringUtils.hasText(request.password())
                    || !isGroupPasswordMatched(request.password(), group.getGroupPassword())) {
                throw new BusinessException(ErrorCode.INVALID_GROUP_PASSWORD);
            }
        }

        if (group.getCurrentMembers() >= group.getMaxMembers()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }
    }

    private GroupDetailButtonType resolveBottomButtonType(String groupId, String userPK) {
        return memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .map(this::resolveBottomButtonType)
                .orElse(GroupDetailButtonType.JOIN);
    }

    private Map<String, GroupDetailButtonType> resolveBottomButtonTypes(List<Group> groups, String userPK) {
        List<String> groupIds = groups.stream()
                .map(Group::getGroupId)
                .toList();

        if (groupIds.isEmpty() || !StringUtils.hasText(userPK)) {
            // 비로그인 목록 조회에서는 멤버십 조회 없이 기본 JOIN 상태를 사용한다.
            return Map.of();
        }

        return memberRepository.findMembershipSummariesByUserPKAndGroupIds(userPK, groupIds)
                .stream()
                .collect(Collectors.toMap(
                        MemberRepository.GroupMembershipSummary::getGroupId,
                        this::resolveBottomButtonType,
                        (left, right) -> left
                ));
    }

    private GroupDetailButtonType resolveBottomButtonType(Member member) {
        return resolveBottomButtonType(member.getMemberRole(), member.getMemberStatus());
    }

    private GroupDetailButtonType resolveBottomButtonType(MemberRepository.GroupMembershipSummary membership) {
        return resolveBottomButtonType(membership.getMemberRole(), membership.getMemberStatus());
    }

    private GroupDetailButtonType resolveBottomButtonType(MemberRole memberRole, MemberStatus memberStatus) {
        if (memberStatus == MemberStatus.ACTIVE) {
            if (memberRole == MemberRole.LEADER) {
                return GroupDetailButtonType.LEADER_SETTING;
            }
            return GroupDetailButtonType.JOINED;
        }

        if (memberStatus == MemberStatus.PENDING) {
            return GroupDetailButtonType.JOINED;
        }

        return GroupDetailButtonType.JOIN;
    }

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserById(String userPK) {
        User user = userRepository.findById(userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }

    private boolean isGroupPasswordMatched(String rawPassword, String savedPassword) {
        if (!StringUtils.hasText(savedPassword)) {
            return false;
        }

        // Backward compatibility for rows saved before hash migration.
        return passwordEncoder.matches(rawPassword, savedPassword) || rawPassword.equals(savedPassword);
    }
}
