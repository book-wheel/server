package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WheelReassignmentService {
    private final RoundRepository roundRepository;
    private final OwnBookRepository ownBookRepository;
    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    public WheelReassignmentService(
            RoundRepository roundRepository,
            OwnBookRepository ownBookRepository,
            WheelStateRepository wheelStateRepository,
            Clock clock
    ) {
        this.roundRepository = roundRepository;
        this.ownBookRepository = ownBookRepository;
        this.wheelStateRepository = wheelStateRepository;
        this.clock = clock;
    }

    public WheelAssignmentPlan reassignFutureRounds(String groupId, Member targetMember, List<Member> remainingMembers) {
        LocalDate today = LocalDate.now(clock);
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        // 오늘 이전에 시작한 라운드는 독서 기록으로 보고 절대 다시 배정하지 않는다.
        List<Round> futureRounds = futureRounds(rounds, today);
        if (futureRounds.isEmpty()) {
            return WheelAssignmentPlan.empty();
        }

        List<String> protectedRoundIds = protectedRoundIds(rounds, today);
        List<WheelState> protectedStates = protectedRoundIds.isEmpty()
                ? List.of()
                : wheelStateRepository.findByRoundIdIn(protectedRoundIds);
        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));

        return planFutureAssignments(futureRounds, remainingMembers, books, protectedStates);
    }

    public WheelAssignmentPlan planFutureAssignments(
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            List<WheelState> protectedStates
    ) {
        List<Round> sortedFutureRounds = sortFutureRounds(futureRounds);
        if (sortedFutureRounds.isEmpty()) {
            return WheelAssignmentPlan.empty();
        }

        List<Member> sortedMembers = sortMembers(members);
        List<OwnBook> sortedBooks = sortedEligibleBooks(books, sortedMembers);
        Map<String, Set<String>> assignedBookIdsByMember = assignedBookIdsByMember(sortedMembers, protectedStates);
        List<WheelAssignmentPlan.Assignment> assignments =
                new ArrayList<>(sortedFutureRounds.size() * sortedMembers.size());

        // 한 라운드씩 탐욕적으로 고르면 뒤 라운드가 막힐 수 있어, 모든 미래 라운드를 백트래킹으로 함께 검증한다.
        if (sortedMembers.isEmpty()
                || !matchFutureRounds(
                0,
                sortedFutureRounds,
                sortedMembers,
                sortedBooks,
                assignedBookIdsByMember,
                assignments
        )) {
            throw new BusinessException(ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE);
        }

        return new WheelAssignmentPlan(assignments);
    }

    @Transactional
    public void replaceFuturePlannedAssignments(
            String groupId,
            WheelAssignmentPlan plan,
            List<Member> remainingMembers
    ) {
        LocalDate today = LocalDate.now(clock);
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        List<Round> futureRounds = futureRounds(rounds, today);
        if (futureRounds.isEmpty()) {
            return;
        }

        deleteReplaceableFutureAssignments(futureRounds);
        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));
        savePlannedAssignments(plan, remainingMembers, books);
    }

    @Transactional
    public void deleteReplaceableFutureAssignments(List<Round> futureRounds) {
        if (futureRounds.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        boolean containsProtectedRound = futureRounds.stream()
                .anyMatch(round -> round.getStartDate() == null || !round.getStartDate().isAfter(today));
        if (containsProtectedRound) {
            throw new IllegalArgumentException("Only future rounds can have replaceable assignments");
        }

        List<String> futureRoundIds = futureRounds.stream()
                .map(Round::getRoundId)
                .toList();

        // 삭제 검증과 삭제 사이에 인증 기록이 생기지 않도록 대상 배정을 잠근다.
        List<WheelState> futureStates = wheelStateRepository.findByRoundIdInForUpdate(futureRoundIds);
        boolean hasProgressRecord = futureStates.stream()
                .anyMatch(wheelState -> !isReplaceableFutureAssignment(wheelState));
        if (hasProgressRecord) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_WHEEL_STATE_INVALID);
        }

        // READY는 선행 정책에서 생성된 미완료 미래 배정만 허용하며, 실제 기록은 자동 삭제하지 않는다.
        wheelStateRepository.deleteByRoundIdIn(futureRoundIds);
        // 동일한 라운드·멤버 조합을 다시 저장하기 전에 DELETE를 먼저 반영해 유니크 제약 충돌을 막는다.
        wheelStateRepository.flush();
    }

    private boolean isReplaceableFutureAssignment(WheelState wheelState) {
        boolean hasReviewRecord = wheelState.getReviewText() != null
                || (wheelState.getAuthImages() != null && !wheelState.getAuthImages().isEmpty());
        if (Boolean.TRUE.equals(wheelState.getIsCompleted()) || hasReviewRecord) {
            return false;
        }
        return wheelState.getWheelState() == WheelStatus.PLANNED
                || wheelState.getWheelState() == WheelStatus.READY;
    }

    @Transactional
    public void savePlannedAssignments(
            WheelAssignmentPlan plan,
            List<Member> members,
            List<OwnBook> books
    ) {
        if (plan.assignments().isEmpty()) {
            return;
        }

        Map<String, Member> memberById = members.stream()
                .collect(Collectors.toMap(Member::getMemberId, member -> member));
        Map<String, OwnBook> bookById = books.stream()
                .collect(Collectors.toMap(OwnBook::getOwnBookId, book -> book));

        List<WheelState> plannedStates = plan.assignments().stream()
                .map(assignment -> WheelState.builder()
                        .wheelStateId(UUID.randomUUID().toString())
                        .roundId(assignment.roundId())
                        .member(requiredMember(memberById, assignment.memberId()))
                        .ownBook(requiredBook(bookById, assignment.ownBookId()))
                        .wheelState(WheelStatus.PLANNED)
                        .isCompleted(false)
                        .build())
                .toList();

        wheelStateRepository.saveAll(plannedStates);
    }

    private Member requiredMember(Map<String, Member> memberById, String memberId) {
        Member member = memberById.get(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE);
        }
        return member;
    }

    private OwnBook requiredBook(Map<String, OwnBook> bookById, String ownBookId) {
        OwnBook ownBook = bookById.get(ownBookId);
        if (ownBook == null) {
            throw new BusinessException(ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE);
        }
        return ownBook;
    }

    private boolean matchFutureRounds(
            int roundIndex,
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            Map<String, Set<String>> assignedBookIdsByMember,
            List<WheelAssignmentPlan.Assignment> assignments
    ) {
        if (roundIndex == futureRounds.size()) {
            return true;
        }

        return matchRoundMember(
                roundIndex,
                0,
                futureRounds,
                members,
                books,
                assignedBookIdsByMember,
                new HashSet<>(),
                assignments
        );
    }

    private boolean matchRoundMember(
            int roundIndex,
            int memberIndex,
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            Map<String, Set<String>> assignedBookIdsByMember,
            Set<String> usedBookIds,
            List<WheelAssignmentPlan.Assignment> assignments
    ) {
        if (memberIndex == members.size()) {
            return matchFutureRounds(roundIndex + 1, futureRounds, members, books, assignedBookIdsByMember, assignments);
        }

        Round round = futureRounds.get(roundIndex);
        Member member = members.get(memberIndex);
        for (OwnBook book : books) {
            String ownBookId = book.getOwnBookId();
            if (usedBookIds.contains(ownBookId) || !canAssign(member, book, assignedBookIdsByMember)) {
                continue;
            }

            usedBookIds.add(ownBookId);
            assignedBookIdsByMember.get(member.getMemberId()).add(ownBookId);
            assignments.add(new WheelAssignmentPlan.Assignment(
                    round.getRoundId(),
                    member.getMemberId(),
                    ownBookId
            ));
            if (matchRoundMember(
                    roundIndex,
                    memberIndex + 1,
                    futureRounds,
                    members,
                    books,
                    assignedBookIdsByMember,
                    usedBookIds,
                    assignments
            )) {
                return true;
            }
            assignments.remove(assignments.size() - 1);
            assignedBookIdsByMember.get(member.getMemberId()).remove(ownBookId);
            usedBookIds.remove(ownBookId);
        }

        return false;
    }

    private boolean canAssign(Member member, OwnBook book, Map<String, Set<String>> assignedBookIdsByMember) {
        return !member.getUser().getId().equals(book.getOwner().getId())
                && !assignedBookIdsByMember.get(member.getMemberId()).contains(book.getOwnBookId());
    }

    private Map<String, Set<String>> assignedBookIdsByMember(List<Member> members, List<WheelState> protectedStates) {
        Set<String> memberIds = members.stream()
                .map(Member::getMemberId)
                .collect(Collectors.toSet());
        Map<String, Set<String>> assignedBookIdsByMember = new HashMap<>();
        members.forEach(member -> assignedBookIdsByMember.put(member.getMemberId(), new HashSet<>()));

        protectedStates.stream()
                .filter(state -> memberIds.contains(state.getMember().getMemberId()))
                .forEach(state -> assignedBookIdsByMember.get(state.getMember().getMemberId())
                        .add(state.getOwnBook().getOwnBookId()));

        return assignedBookIdsByMember;
    }

    private List<OwnBook> sortedEligibleBooks(List<OwnBook> books, List<Member> members) {
        Map<String, Integer> ownerOrderByUserPK = new HashMap<>();
        for (int index = 0; index < members.size(); index++) {
            ownerOrderByUserPK.put(members.get(index).getUser().getId(), index);
        }

        return books.stream()
                // 남아 있는 ACTIVE 멤버의 책만 후보로 두어 탈퇴자 책을 이후 배정에서 제외한다.
                .filter(book -> ownerOrderByUserPK.containsKey(book.getOwner().getId()))
                .sorted(Comparator
                        .comparing((OwnBook book) -> ownerOrderByUserPK.get(book.getOwner().getId()))
                        .thenComparing(OwnBook::getOwnBookId))
                .toList();
    }

    private List<Member> sortMembers(List<Member> members) {
        return members.stream()
                .sorted(Comparator
                        .comparing(Member::getReadOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Member::getMemberId))
                .toList();
    }

    private List<Round> futureRounds(List<Round> rounds, LocalDate today) {
        return sortFutureRounds(rounds.stream()
                .filter(round -> round.getStartDate() != null && round.getStartDate().isAfter(today))
                .toList());
    }

    private List<Round> sortFutureRounds(List<Round> rounds) {
        return rounds.stream()
                .sorted(Comparator.comparing(Round::getStartDate).thenComparing(Round::getRoundNumber))
                .toList();
    }

    private List<String> protectedRoundIds(List<Round> rounds, LocalDate today) {
        return rounds.stream()
                // 현재 진행 중인 라운드도 보호 대상이라, 이미 읽은 책으로 간주한다.
                .filter(round -> round.getStartDate() != null && !round.getStartDate().isAfter(today))
                .map(Round::getRoundId)
                .toList();
    }

}
