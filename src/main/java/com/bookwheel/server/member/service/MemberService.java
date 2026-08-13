package com.bookwheel.server.member.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.dto.member.GroupCurrentRoundResponse;
import com.bookwheel.server.group.dto.member.GroupMemberListResponse;
import com.bookwheel.server.group.dto.member.GroupMemberCurrentRoundAssignmentResponse;
import com.bookwheel.server.group.dto.member.GroupMemberResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    public boolean isUserInGroup(String userPK) {
        return memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE);
    }

    public GroupMemberListResponse getGroupMembers(String groupId) {
        Optional<Round> currentRound = roundRepository.findCurrentRound(
                groupId,
                LocalDate.now(clock),
                State.IN_PROGRESS
        );
        Map<String, WheelState> assignmentByMemberId = currentRound
                .map(round -> wheelStateRepository.findAllByRoundIdWithMemberAndBook(round.getRoundId()))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(
                        wheelState -> wheelState.getMember().getMemberId(),
                        Function.identity()
                ));

        List<GroupMemberResponse> members = memberRepository
                .findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(member -> convertToMemberResponse(member, assignmentByMemberId.get(member.getMemberId())))
                .toList();

        return GroupMemberListResponse.from(
                currentRound.map(GroupCurrentRoundResponse::from).orElse(null),
                members
        );
    }

    // 조회된 멤버를 DTO로 변환
    private GroupMemberResponse convertToMemberResponse(Member member, WheelState currentRoundAssignment) {
        String profileImageKey = member.getUser().getProfileImageKey();
        String profileImageUrl = getProfileImageUrl(profileImageKey);
        GroupMemberCurrentRoundAssignmentResponse assignmentResponse = currentRoundAssignment == null
                ? null
                : GroupMemberCurrentRoundAssignmentResponse.from(currentRoundAssignment);

        return GroupMemberResponse.from(member, profileImageUrl, assignmentResponse);
    }

    // 프로필 이미지 호출
    private String getProfileImageUrl(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            return null;
        }
        return s3Service.getPresignedGetUrl(imageKey);
    }
}
