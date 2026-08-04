package com.bookwheel.server.group.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomReadStateRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.dto.GroupDetailButtonType;
import com.bookwheel.server.group.dto.GroupDetailResponse;
import com.bookwheel.server.group.dto.setting.GroupUpdateRequest;
import com.bookwheel.server.group.dto.setting.LeadershipTransferResponse;
import com.bookwheel.server.group.dto.setting.MemberExitResponse;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.group.dto.setting.MemberRoleChangeResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.ScheduleReconfigurationStatus;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.schedule.service.RecruitingScheduleAssignmentService;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import com.bookwheel.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 모임 설정과 멤버 권한 변경을 한 트랜잭션 안에서 처리해 상태와 일정의 불일치를 막는다.
public class GroupSettingService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final OwnBookRepository ownBookRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomReadStateRepository chatRoomReadStateRepository;
    private final NotificationService notificationService;
    private final S3Service s3Service;
    private final WheelReassignmentService wheelReassignmentService;
    private final RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final EntityManager entityManager;

    @Transactional
    public GroupDetailResponse updateGroup(String groupId, String leaderUserPK, GroupUpdateRequest request) {
        // 설정 변경 중 다른 요청이 같은 모임의 멤버·일정 상태를 바꾸지 못하도록 모임 행을 먼저 잠근다.
        Group group = groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        validateGroupEditable(group);
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);
        // 일정 진행 여부와 관계없이 일정 필드를 제외한 모임 정보는 리더가 수정할 수 있다.
        validateGroupUpdate(group, request);

        group.updateGroupInfo(
                request.groupName(),
                request.groupComment(),
                request.groupRule(),
                request.groupPublic(),
                resolveGroupPassword(request),
                request.groupOffline(),
                request.groupOffline() ? request.groupRegion() : null,
                request.maxMembers()
        );

        return GroupDetailResponse.from(group, GroupDetailButtonType.LEADER_SETTING);
    }

    @Transactional
    public void deleteGroup(String groupId, String leaderUserPK) {
        // 삭제 중 신규 멤버·도서·일정이 추가되지 않도록 삭제 전체 과정에서 모임 행 잠금을 유지한다.
        Group group = groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        validateDeletableGroup(group);
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        Set<String> objectKeys = new LinkedHashSet<>();

        // 그룹 알림은 비동기 저장 경로와도 같은 그룹 잠금을 사용하므로 먼저 정리한다.
        notificationService.deleteByGroupId(groupId);
        // 게시물·댓글·좋아요·신고는 모임의 기록이므로 삭제하지 않고 보존한다.
        // 외래 키 의존성이 있는 운영 데이터부터 삭제하면서 S3 키를 먼저 수집한다.
        objectKeys.addAll(deleteChatData(groupId));
        objectKeys.addAll(deleteScheduleData(groupId));
        ownBookRepository.deleteAllByGroupId(groupId);
        memberRepository.deleteAllByGroupId(groupId);

        // 그룹 행은 삭제하지 않고 DELETED 상태로 바꿔 게시물과 모임 기록의 참조를 유지한다.
        group.markDeleted();
        entityManager.flush();
        entityManager.clear();
        registerPostCommitImageCleanup(objectKeys);
    }

    @Transactional
    public MemberKickResponse kickMember(String groupId, String targetUserPK, String leaderUserPK) {
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_YOURSELF);
        }

        // 멤버 구성 변경과 일정 재확정 상태 전환을 하나의 잠긴 시점에서 처리한다.
        LockedActiveMembers lockedMembers = lockedActiveMembers(groupId);
        List<Member> activeMembers = lockedMembers.activeMembers();

        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        Member targetMember = findKickTargetMember(groupId, activeMembers, targetUserPK);
        validateKickableTarget(targetMember);
        Runnable postStatusMutation = prepareRemovalBeforeStatusMutation(
                lockedMembers.group(),
                targetMember,
                false
        );
        targetMember.kick();
        postStatusMutation.run();

        return MemberKickResponse.of(targetUserPK, targetMember.getMemberStatus());
    }


    @Transactional
    public MemberRoleChangeResponse changeRole(String groupId, String targetUserPK, String leaderUserPK, MemberRole role) {
        // 부방장 권한 관련 내용만 다룸
        if (role == MemberRole.LEADER) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_TRANSITION);
        }
        // 멤버 혹은 부방장만 다룸
        if (role != MemberRole.MEMBER && role != MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_TRANSITION);
        }
        // 요청자와 타겟은 동일하게 X
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        // 요청자가 방장인지 권한 검증
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        // 타겟 멤버가 그룹에 속하는지 확인
        Member targetMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 타겟 멤버의 현재 권한 검증
        MemberRole currentRole = targetMember.getMemberRole();

        if (currentRole == MemberRole.LEADER || currentRole == MemberRole.OUT) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_CHANGE);
        }
        if (currentRole == role) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        // 권한 변경
        if (currentRole == MemberRole.MEMBER) {
            targetMember.promoteToSubLeader();
        } else {
            targetMember.demoteToMember();
        }

        return MemberRoleChangeResponse.of(groupId, targetUserPK, targetMember.getMemberRole());
    }

    @Transactional
    public LeadershipTransferResponse transferLeadership(String groupId, String targetUserPK, String leaderUserPK) {
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }

        // 미래 일정 재생성의 리더 검증과 같은 그룹 잠금을 사용해 권한 변경을 직렬화한다.
        groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        Member leaderMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, leaderUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Member targetMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // ACTIVE 아닌 멤버에게는 위임 불가
        if (targetMember.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }

        MemberRole targetCurrentRole = targetMember.getMemberRole();
        // 타겟 유저의 권한이 이상하면 오류 발생
        if (targetCurrentRole != MemberRole.MEMBER && targetCurrentRole != MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }

        // 방장으로 위임 후 일반 멤버로 돌아가기
        leaderMember.transferLeaderTo(targetMember);

        return LeadershipTransferResponse.of(
                groupId,
                targetUserPK,
                targetMember.getMemberRole(),
                leaderUserPK,
                leaderMember.getMemberRole()
        );
    }

    @Transactional
    public MemberExitResponse exitMember(String groupId, String userPK) {
        LockedActiveMembers lockedMembers = lockedActiveMembers(groupId);
        List<Member> activeMembers = lockedMembers.activeMembers();
        Member member = findActiveMember(activeMembers, userPK);

        // 그룹장/부그룹장은 중도하차 불가 — 권한 위임/해제 선행 필요
        MemberRole role = member.getMemberRole();
        if (role == MemberRole.LEADER || role == MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_EXIT);
        }

        Runnable postStatusMutation = prepareRemovalBeforeStatusMutation(
                lockedMembers.group(),
                member,
                true
        );

        member.exit();
        postStatusMutation.run();

        return MemberExitResponse.of(groupId, userPK, member.getMemberStatus());
    }

    private void validateDeletableGroup(Group group) {
        // 이미 비활성화된 모임은 중복 삭제할 수 없다.
        if (group.getGroupState() == State.DELETED) {
            throw new BusinessException(ErrorCode.GROUP_DELETED);
        }
        // 진행 중인 모임은 현재·과거 독서 기록을 보존해야 하므로 삭제를 허용하지 않는다.
        if (group.getGroupState() == State.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.GROUP_DELETE_IN_PROGRESS_NOT_ALLOWED);
        }
        // 모집 중 또는 완료된 모임만 운영 데이터를 정리하고 비활성화할 수 있다.
        if (group.getGroupState() != State.RECRUITING && group.getGroupState() != State.COMPLETE) {
            throw new BusinessException(ErrorCode.GROUP_DELETE_STATE_INVALID);
        }
    }

    private void validateGroupEditable(Group group) {
        // 삭제된 모임은 기록 보존을 위해 조회만 허용하고 설정 변경은 막는다.
        if (group.getGroupState() == State.DELETED) {
            throw new BusinessException(ErrorCode.GROUP_DELETED);
        }
    }

    private void validateGroupUpdate(Group group, GroupUpdateRequest request) {
        // 모임 이름은 다른 모임과 중복될 수 없지만 자기 자신의 기존 이름은 허용한다.
        if (!group.getGroupName().equals(request.groupName())
                && groupRepository.existsNotDeletedByGroupNameAndGroupIdNot(
                        request.groupName(),
                        group.getGroupId(),
                        State.DELETED
                )) {
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }

        // 비공개 설정은 기존 공개 여부와 관계없이 매번 새 비밀번호를 받아야 한다.
        if (!request.groupPublic() && !StringUtils.hasText(request.groupPassword())) {
            throw new BusinessException(ErrorCode.GROUP_PASSWORD_REQUIRED);
        }

        // 공개 모임에 비밀번호가 함께 오면 입력을 조용히 버리지 않고 잘못된 요청으로 처리한다.
        if (request.groupPublic() && StringUtils.hasText(request.groupPassword())) {
            throw new BusinessException(ErrorCode.GROUP_PUBLIC_PASSWORD_NOT_ALLOWED);
        }

        // 오프라인 모임만 활동 지역을 사용하므로 오프라인 설정에는 지역이 필수다.
        if (request.groupOffline() && request.groupRegion() == null) {
            throw new BusinessException(ErrorCode.GROUP_REGION_REQUIRED);
        }

        if (!request.groupOffline() && request.groupRegion() != null) {
            throw new BusinessException(ErrorCode.GROUP_REGION_NOT_ALLOWED_FOR_ONLINE);
        }

        long activeMemberCount = memberRepository.countByGroup_GroupIdAndMemberStatus(group.getGroupId(), MemberStatus.ACTIVE);
        // 현재 참여 인원보다 작은 정원으로 줄이면 기존 멤버를 수용할 수 없으므로 거부한다.
        if (request.maxMembers() < activeMemberCount) {
            throw new BusinessException(ErrorCode.GROUP_MAX_MEMBERS_BELOW_CURRENT_MEMBERS);
        }
        if (group.getTargetMemberCount() != null && request.maxMembers() < group.getTargetMemberCount()) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }
    }

    private String resolveGroupPassword(GroupUpdateRequest request) {
        // 공개 모임은 가입 비밀번호를 사용하지 않으므로 저장된 해시도 함께 제거한다.
        if (request.groupPublic()) {
            return null;
        }

        if (StringUtils.hasText(request.groupPassword())) {
            // 평문 비밀번호를 그대로 저장하지 않고 변경 요청 시 새 해시를 생성한다.
            return passwordEncoder.encode(request.groupPassword());
        }

        throw new IllegalStateException("비공개 모임은 검증된 비밀번호가 필요합니다.");
    }

    // 채팅 데이터를 삭제하고 DB에서 확인된 채팅 이미지 키만 반환한다.
    private List<String> deleteChatData(String groupId) {
        List<String> objectKeys = new ArrayList<>();
        chatRoomRepository.findByGroup_GroupId(groupId).ifPresent(chatRoom -> {
            List<ChatMessage> messages = chatMessageRepository.findByChatRoom(chatRoom);
            messages.stream()
                    .map(ChatMessage::getImageKey)
                    .filter(StringUtils::hasText)
                    .forEach(objectKeys::add);

            // 채팅방을 참조하는 읽음 상태와 메시지를 먼저 제거해야 채팅방 삭제가 가능하다.
            chatRoomReadStateRepository.deleteAllByChatRoom(chatRoom);
            chatMessageRepository.deleteAllByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
            chatRoomRepository.flush();
        });
        return objectKeys;
    }

    // 라운드·책바퀴 기록을 FK 순서대로 삭제하고 인증 이미지 키를 반환한다.
    private List<String> deleteScheduleData(String groupId) {
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        if (rounds.isEmpty()) {
            return List.of();
        }

        List<String> roundIds = rounds.stream()
                .map(Round::getRoundId)
                .toList();
        List<WheelState> wheelStates = wheelStateRepository.findByRoundIdIn(roundIds);
        List<String> objectKeys = wheelStates.stream()
                .flatMap(wheelState -> wheelState.getAuthImages().stream())
                .map(image -> image.getObjectKey())
                .filter(StringUtils::hasText)
                .toList();

        // WheelState를 먼저 삭제하면 라운드와 인증 이미지 사이의 외래 키 의존성도 함께 정리된다.
        wheelStateRepository.deleteAll(wheelStates);
        wheelStateRepository.flush();
        roundRepository.deleteAll(rounds);
        roundRepository.flush();
        return objectKeys;
    }

    // 트랜잭션이 실제 커밋된 뒤 수집된 S3 객체만 best-effort로 삭제한다.
    private void registerPostCommitImageCleanup(Set<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("모임 이미지 정리는 트랜잭션 커밋 이후에만 실행할 수 있습니다.");
        }

        // DB가 롤백되면 이미지도 남겨야 하므로 커밋이 성공한 뒤에만 S3 삭제를 시작한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectKeys.forEach(s3Service::deleteObject);
            }
        });
    }

    private LockedActiveMembers lockedActiveMembers(String groupId) {
        // 멤버 변동 전 모임과 활성 멤버 목록을 같은 잠금 시점에 읽어 상태 전환 기준을 고정한다.
        Group group = groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        List<Member> activeMembers = memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE);
        return new LockedActiveMembers(group, activeMembers);
    }

    private Runnable prepareRemovalBeforeStatusMutation(
            Group group,
            Member member,
            boolean validateCurrentReading
    ) {
        switch (group.getGroupState()) {
            case IN_PROGRESS -> {
                if (validateCurrentReading) {
                    validateCurrentRoundCompletion(group.getGroupId(), member);
                }
                // 기존 미래 일정은 리더의 수정 화면에 남기되 실행 범위에서 제외해 자동 시작을 막는다.
                return () -> pauseFutureRoundsForManualReconfiguration(group);
            }
            case RECRUITING -> {
                // 라운드는 고정하고 상태 변경이 반영된 뒤 PLANNED 배정만 다시 판단한다.
                return () -> recruitingScheduleAssignmentService.refreshPlannedAssignments(group);
            }
            case COMPLETE -> {
                return () -> {
                };
            }
            case DELETED -> throw new BusinessException(ErrorCode.GROUP_DELETED);
        }
        throw new IllegalStateException("Unsupported group state: " + group.getGroupState());
    }

    private void pauseFutureRoundsForManualReconfiguration(Group group) {
        LocalDate today = LocalDate.now(clock);
        // groupRoundCount까지만 스케줄러가 실행하므로 미래 행을 삭제하지 않고도 안전하게 보류할 수 있다.
        List<Round> rounds = roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(group.getGroupId());
        List<Round> futureRounds = rounds.stream()
                .filter(round -> isFutureRound(round, today))
                .toList();

        // 미래 실행 라운드 없음 + 재확정 상태 NONE: 재확정 생략
        // 재확정 진행 중 추가 인원 변동: 변경된 멤버 기준 읽기 순서 확인 단계로 복귀
        if (futureRounds.isEmpty()) {
            if (group.getScheduleReconfigurationStatus() != ScheduleReconfigurationStatus.NONE) {
                group.requireReadOrderConfirmation();
            }
            return;
        }

        int lastProtectedRoundNumber = rounds.stream()
                // 날짜가 없는 손상된 라운드는 보호 대상으로 간주하지 않아 실행 범위에 남기지 않는다.
                .filter(round -> round.getStartDate() != null && !round.getStartDate().isAfter(today))
                .map(Round::getRoundNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        // 멤버 구성이 바뀌면 기존 미래 배정 전체가 유효하지 않으므로 배정만 제거하고 라운드 날짜는 남긴다.
        wheelReassignmentService.deleteReplaceableFutureAssignments(futureRounds);
        group.updateScheduleInfo(group.getStartDate(), lastProtectedRoundNumber);
        group.requireReadOrderConfirmation();
    }

    private boolean isFutureRound(Round round, LocalDate today) {
        return round.getStartDate() != null && round.getStartDate().isAfter(today);
    }

    private Member findActiveMember(List<Member> activeMembers, String userPK) {
        return activeMembers.stream()
                .filter(member -> member.getUser().getId().equals(userPK))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member findKickTargetMember(String groupId, List<Member> activeMembers, String targetUserPK) {
        return activeMembers.stream()
                .filter(member -> member.getUser().getId().equals(targetUserPK))
                .findFirst()
                .or(() -> memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateKickableTarget(Member targetMember) {
        if (targetMember.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }
        if (targetMember.getMemberRole() != MemberRole.MEMBER) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ONLY);
        }
    }

    private void validateCurrentRoundCompletion(String groupId, Member member) {
        // 다른 로직과 같은 Clock을 사용해 날짜 경계에서 현재 라운드 기준이 달라지는 일을 막는다.
        Round currentRound = roundRepository.findCurrentRound(
                        groupId,
                        LocalDate.now(clock),
                        State.IN_PROGRESS
                )
                .orElse(null);

        if (currentRound != null) {
            WheelState myWheelState = wheelStateRepository
                    .findFirstByRoundIdAndMember_MemberId(currentRound.getRoundId(), member.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_COMPLETED));

            if (!Boolean.TRUE.equals(myWheelState.getIsCompleted())) {
                throw new BusinessException(ErrorCode.READING_NOT_COMPLETED);
            }
        }
    }

    private record LockedActiveMembers(Group group, List<Member> activeMembers) {
    }
}
