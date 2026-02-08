package com.bookwheel.server.group.entity;

import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "group_password", length = 20)
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

    @Column(name = "max_members")
    private Integer maxMembers;

    @Builder.Default
    @Column(name = "group_round_count")
    private int groupRoundCount = 1;

    @Column(name = "group_state")
    @Enumerated(EnumType.STRING)
    private State groupState;

}
