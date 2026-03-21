package com.bookwheel.server.member.service;

import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    public boolean isUserInGroup(String userPK) {
        return memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE);
    }
}
