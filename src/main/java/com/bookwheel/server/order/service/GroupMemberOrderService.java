package com.bookwheel.server.order.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.order.dto.MemberReadOrderRequest;
import com.bookwheel.server.order.dto.MemberReadOrderResponse;
import com.bookwheel.server.order.event.ReadOrderAssignedEvent;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupMemberOrderService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<MemberReadOrderResponse> assignReadOrder(
            String groupId,
            MemberReadOrderRequest request,
            String userId
    ) {
        Group group = findGroupById(groupId);
        findActiveUserByUserId(userId);
        validateManagerPermission(groupId, userId);
        validateRequestShape(request);

        List<Member> activeMembers = memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE);
        List<Member> orderedMembers = request.isRandom()
                ? resolveRandomOrder(activeMembers)
                : resolveManualOrder(activeMembers, request.memberIds());

        for (Member member : activeMembers) {
            member.setReadOrder(null);
        }

        int order = 1;
        for (Member member : orderedMembers) {
            member.setReadOrder(order++);
        }

        memberRepository.saveAll(activeMembers);

        eventPublisher.publishEvent(new ReadOrderAssignedEvent(
                group.getGroupId(),
                group.getGroupName(),
                orderedMembers.stream().map(m -> m.getUser().getUserId()).toList()
        ));

        return orderedMembers.stream()
                .map(member -> MemberReadOrderResponse.of(
                        member.getReadOrder(),
                        member.getMemberId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageKey()
                ))
                .toList();
    }

    private void validateRequestShape(MemberReadOrderRequest request) {
        boolean hasMemberIds = !CollectionUtils.isEmpty(request.memberIds());

        if (Boolean.TRUE.equals(request.isRandom())) {
            if (hasMemberIds) {
                throw new BusinessException(ErrorCode.GROUP_ORDER_REQUEST_INVALID);
            }
            return;
        }

        if (!hasMemberIds) {
            throw new BusinessException(ErrorCode.GROUP_ORDER_REQUEST_INVALID);
        }
    }

    private List<Member> resolveRandomOrder(List<Member> activeMembers) {
        List<Member> shuffled = new ArrayList<>(activeMembers);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    private List<Member> resolveManualOrder(List<Member> activeMembers, List<String> memberIds) {
        Set<String> uniqueRequestedIds = new HashSet<>(memberIds);
        if (memberIds.size() != uniqueRequestedIds.size()) {
            throw new BusinessException(ErrorCode.GROUP_ORDER_MEMBER_SET_INVALID);
        }

        Set<String> activeMemberIds = new HashSet<>();
        Map<String, Member> activeMemberById = new HashMap<>();
        for (Member member : activeMembers) {
            activeMemberIds.add(member.getMemberId());
            activeMemberById.put(member.getMemberId(), member);
        }

        if (memberIds.size() != activeMembers.size() || !activeMemberIds.equals(uniqueRequestedIds)) {
            throw new BusinessException(ErrorCode.GROUP_ORDER_MEMBER_SET_INVALID);
        }

        List<Member> orderedMembers = new ArrayList<>(memberIds.size());
        for (String memberId : memberIds) {
            Member member = activeMemberById.get(memberId);
            if (member == null) {
                throw new BusinessException(ErrorCode.GROUP_ORDER_MEMBER_SET_INVALID);
            }
            orderedMembers.add(member);
        }
        return orderedMembers;
    }

    private void validateManagerPermission(String groupId, String userId) {
        Member manager = memberRepository.findByGroup_GroupIdAndUser_UserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ORDER_MANAGER_ONLY));

        boolean isActive = manager.getMemberStatus() == MemberStatus.ACTIVE;
        boolean isManagerRole = manager.getMemberRole() == MemberRole.LEADER
                || manager.getMemberRole() == MemberRole.SUB_LEADER;
        if (!isActive || !isManagerRole) {
            throw new BusinessException(ErrorCode.GROUP_ORDER_MANAGER_ONLY);
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
}
