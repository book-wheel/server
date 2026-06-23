package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WheelAssignmentService {
    private final MemberRepository memberRepository;
    private final OwnBookRepository ownBookRepository;

    // 특정 라운드에서 해당 멤버가 읽어야 하는 책 조회
    public Optional<OwnBook> findAssignedBook(String groupId, Member member, int roundNumber) {
        List<Member> members = memberRepository.findByGroup_GroupIdInAndMemberStatusOrderByReadOrderAsc(
                List.of(groupId),
                MemberStatus.ACTIVE
        );
        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));

        return findAssignedBook(members, books, member, roundNumber);
    }

    // ACTIVE 멤버 중 아직 책을 등록하지 않은 멤버 조회
    public List<Member> findMembersWithoutBook(String groupId) {
        List<Member> members = memberRepository.findByGroup_GroupIdInAndMemberStatusOrderByReadOrderAsc(
                List.of(groupId),
                MemberStatus.ACTIVE
        );
        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));

        return findMembersWithoutBook(members, books);
    }

    // 이미 조회된 멤버/책 목록을 기준으로 책 미등록 멤버 찾기
    public List<Member> findMembersWithoutBook(List<Member> members, List<OwnBook> books) {
        if (members.isEmpty()) {
            return List.of();
        }

        // 책을 등록한 유저 PK 목록 추출
        Set<String> ownerPKs = books.stream()
                .map(book -> book.getOwner().getId())
                .collect(Collectors.toSet());

        // ACTIVE 멤버 중 책 소유자 목록에 없는 멤버만 반환
        return members.stream()
                .filter(member -> !ownerPKs.contains(member.getUser().getId()))
                .toList();
    }

    // 이미 조회된 멤버/책 목록에서 해당 멤버의 배정 책 조회
    public Optional<OwnBook> findAssignedBook(
            List<Member> members,
            List<OwnBook> books,
            Member member,
            int roundNumber
    ) {
        return assignBooks(members, books, roundNumber).stream()
                .filter(assignment -> assignment.member().getMemberId().equals(member.getMemberId()))
                .map(WheelAssignment::ownBook)
                .findFirst();
    }

    // 라운드 번호를 기준으로 멤버별 읽을 책 배정
    public List<WheelAssignment> assignBooks(List<Member> members, List<OwnBook> books, int roundNumber) {
        if (members.isEmpty() || books.isEmpty()) {
            return List.of();
        }

        // 읽기 순서 기준으로 멤버 정렬
        List<Member> sortedMembers = sortMembers(members);

        // 책 주인 PK 기준으로 책을 빠르게 찾기 위한 Map 생성
        Map<String, OwnBook> bookMap = books.stream()
                .collect(Collectors.toMap(book -> book.getOwner().getId(), book -> book));

        // 멤버 순서와 같은 순서로 책 목록 재정렬
        List<OwnBook> sortedBooks = sortedMembers.stream()
                .map(member -> bookMap.get(member.getUser().getId()))
                .toList();

        // 책을 등록하지 않은 멤버가 있다면 배정하지 않음
        if (sortedBooks.stream().anyMatch(book -> book == null)) {
            return List.of();
        }

        // 현재 라운드만큼 책 인덱스를 밀어서 책바퀴 배정 생성
        int size = sortedMembers.size();
        List<WheelAssignment> assignments = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int bookIndex = (i + roundNumber) % size;
            assignments.add(new WheelAssignment(sortedMembers.get(i), sortedBooks.get(bookIndex)));
        }

        return assignments;
    }

    // 읽기 순서가 없다면 memberId 기준으로 임의 정렬
    private List<Member> sortMembers(List<Member> members) {
        if (!members.isEmpty() && members.get(0).getReadOrder() == null) {
            return members.stream()
                    .sorted(Comparator.comparing(Member::getMemberId))
                    .toList();
        }

        return members;
    }

    // 한 멤버에게 어떤 책이 배정되었는지 담는 값 객체
    public record WheelAssignment(Member member, OwnBook ownBook) {
    }
}
