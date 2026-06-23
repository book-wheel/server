package com.bookwheel.server.schedule.entity;

import com.bookwheel.server.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@Table(
        name = "round",
        indexes = {
                @Index(name = "idx_round_group_id", columnList = "group_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_round_group_round_number",
                        columnNames = {"group_id", "round_number"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Round {
    @Id
    @Column(name = "round_id", length = 50)
    private String roundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public void updateSchedule(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
