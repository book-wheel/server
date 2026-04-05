package com.bookwheel.server.member.service;

import com.bookwheel.server.group.dto.GroupMemberListResponse;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    public boolean isUserInGroup(String userPK) {
        return memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE);
    }

    public GroupMemberListResponse getGroupMembers(String groupId) {
        List<Member> members = memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE);

        // 2. Entity를 DTO로 변환
        // 3. GroupMemberListResponse로 포장해서 반환

        return null; // 일단 뼈대만!
    }
}
