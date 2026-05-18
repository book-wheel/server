package com.bookwheel.server.member.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.dto.member.GroupMemberListResponse;
import com.bookwheel.server.group.dto.member.GroupMemberResponse;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public boolean isUserInGroup(String userPK) {
        return memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE);
    }

    public GroupMemberListResponse getGroupMembers(String groupId) {
        List<GroupMemberResponse> members = memberRepository
                .findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(this::convertToMemberResponse)
                .toList();

        return GroupMemberListResponse.from(members);
    }

    // 조회된 멤버를 DTO로 변환
    private GroupMemberResponse convertToMemberResponse(Member member) {
        String profileImageKey = member.getUser().getProfileImageKey();
        String profileImageUrl = getProfileImageUrl(profileImageKey);

        return GroupMemberResponse.from(member, profileImageUrl);
    }

    // 프로필 이미지 호출
    private String getProfileImageUrl(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            return null;
        }
        return s3Service.getPresignedGetUrl(imageKey);
    }
}
