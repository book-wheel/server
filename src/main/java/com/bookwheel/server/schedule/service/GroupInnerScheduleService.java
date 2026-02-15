package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupInnerScheduleService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OwnBookRepository ownBookRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<GroupScheduleRoundResponse> createSchedule(
            String groupId,
            GroupScheduleCreateRequest request,
            String userId
    ) {
        Group group = findGroupById(groupId);
        findActiveUserByUserId(userId);
        validateLeaderPermission(groupId, userId);

        ensureRoundTableExists();

        long ownBookCount = ownBookRepository.countByGroup_GroupId(groupId);
        if (ownBookCount <= 0) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_OWN_BOOK_REQUIRED);
        }

        Integer readingPeriod = group.getReadingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        int roundCount = Math.toIntExact(ownBookCount);
        jdbcTemplate.update("DELETE FROM `round` WHERE group_id = ?", groupId);

        group.updateScheduleInfo(request.startDate(), roundCount);

        List<GroupScheduleRoundResponse> rounds = new ArrayList<>(roundCount);
        LocalDate currentStart = request.startDate();
        for (int roundNumber = 1; roundNumber <= roundCount; roundNumber++) {
            LocalDate endDate = currentStart.plusDays(readingPeriod - 1L);
            rounds.add(GroupScheduleRoundResponse.of(roundNumber, currentStart, endDate));

            jdbcTemplate.update(
                    "INSERT INTO `round` (round_id, group_id, round_number, start_date, end_date) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(),
                    groupId,
                    roundNumber,
                    currentStart,
                    endDate
            );

            currentStart = endDate.plusDays(1);
        }

        return rounds;
    }

    private void ensureRoundTableExists() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS `round` (
                        `round_id` VARCHAR(50) PRIMARY KEY,
                        `group_id` VARCHAR(50) NOT NULL,
                        `round_number` INT NOT NULL,
                        `start_date` DATE,
                        `end_date` DATE,
                        CONSTRAINT `fk_round_group`
                            FOREIGN KEY (`group_id`) REFERENCES `reading_group`(`group_id`) ON DELETE CASCADE
                    )
                    """);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.GROUP_ROUND_TABLE_NOT_FOUND);
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

    private void validateLeaderPermission(String groupId, String userId) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_UserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_LEADER_ONLY));

        boolean isLeader = member.getMemberRole() == MemberRole.LEADER;
        boolean isActive = member.getMemberStatus() == MemberStatus.ACTIVE;
        if (!isLeader || !isActive) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_ONLY);
        }
    }
}
