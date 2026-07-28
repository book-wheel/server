package com.bookwheel.server.group.entity;

import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@Table(name = "reading_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Group {
    @Id
    @Column(name = "group_id", length = 50)
    private String groupId;

    @Column(name = "group_name", nullable = false, length = 20)
    private String groupName;

    @Column(name = "group_comment", length = 50)
    private String groupComment;

    @Column(name = "group_rule", columnDefinition = "TEXT")
    private String groupRule;

    @Builder.Default
    @Column(name = "group_public")
    private boolean groupPublic = false;

    @Column(name = "group_password", length = 255)
    private String groupPassword;

    @Builder.Default
    @Column(name = "group_offline")
    private boolean groupOffline = false;

    @Column(name = "group_region")
    @Enumerated(EnumType.STRING)
    private Region groupRegion;

    @Column(name = "reading_period")
    private Integer readingPeriod;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "schedule_end_date")
    private LocalDate scheduleEndDate;

    @Column(name = "schedule_excluded_dates", columnDefinition = "TEXT")
    private String scheduleExcludedDates;

    @Column(name = "schedule_excluded_date_ranges", columnDefinition = "TEXT")
    private String scheduleExcludedDateRanges;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Builder.Default
    @Column(name = "group_round_count")
    private int groupRoundCount = 1;

    @Column(name = "target_member_count")
    private Integer targetMemberCount;

    // enum 스키마 갱신 대상임을 명확히 해 소프트 삭제 상태를 저장할 수 있게 한다.
    @Column(name = "group_state", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private State groupState = State.RECRUITING;

    // DB 컬럼이 아닌, 쿼리 실행 시 서브쿼리로 계산되는 가상 필드
    @Formula("(SELECT count(1) FROM member m WHERE m.group_id = group_id AND m.member_status = 'ACTIVE')")
    private int currentMembers;

    public void updateScheduleInfo(LocalDate startDate, int groupRoundCount) {
        this.startDate = startDate;
        this.groupRoundCount = groupRoundCount;
    }

    public void updateSchedulePlan(LocalDate startDate, int groupRoundCount, int targetMemberCount) {
        this.startDate = startDate;
        this.groupRoundCount = groupRoundCount;
        this.targetMemberCount = targetMemberCount;
    }

    // 시작 시점의 실제 ACTIVE 인원으로 실행 가능한 라운드 수를 확정한다.
    // 목표 인원 기준으로 만든 나머지 Round 행은 일정 틀로 보존하고 완료 판단에서는 제외한다.
    public void confirmExecutableRoundCount(int executableRoundCount) {
        this.groupRoundCount = executableRoundCount;
    }

    // target_member_count 도입 전 생성된 일정은 기존 라운드 수로 목표 인원을 복원한다.
    public void initializeTargetMemberCount(int targetMemberCount) {
        if (this.targetMemberCount == null) {
            this.targetMemberCount = targetMemberCount;
        }
    }

    public void updateScheduleConstraints(
            LocalDate scheduleEndDate,
            String scheduleExcludedDates,
            String scheduleExcludedDateRanges
    ) {
        this.scheduleEndDate = scheduleEndDate;
        this.scheduleExcludedDates = scheduleExcludedDates;
        this.scheduleExcludedDateRanges = scheduleExcludedDateRanges;
    }

    public void updateGroupPassword(String groupPassword) {
        this.groupPassword = groupPassword;
    }

    // 독서 기간은 모임 기본 정보가 아니라 일정 생성·재생성 API에서만 변경한다.
    public void updateReadingPeriod(Integer readingPeriod) {
        this.readingPeriod = readingPeriod;
    }

    // 일정 필드를 건드리지 않고 화면의 기본 모임 정보만 변경한다.
    public void updateGroupInfo(
            String groupName,
            String groupComment,
            String groupRule,
            boolean groupPublic,
            String groupPassword,
            boolean groupOffline,
            Region groupRegion,
            Integer maxMembers
    ) {
        this.groupName = groupName;
        this.groupComment = groupComment;
        this.groupRule = groupRule;
        this.groupPublic = groupPublic;
        this.groupPassword = groupPassword;
        this.groupOffline = groupOffline;
        this.groupRegion = groupRegion;
        this.maxMembers = maxMembers;
    }

    // 게시물 등 모임의 기록을 보존한 채 운영 상태만 비활성화한다.
    public void markDeleted() {
        this.groupState = State.DELETED;
    }

    @PrePersist
    private void prePersist() {
        if (groupState == null) {
            groupState = State.RECRUITING;
        }
    }

}
