package com.bookwheel.server.wheel.dto;

import java.util.List;

public record WheelAssignmentPlan(List<Assignment> assignments) {
    public WheelAssignmentPlan {
        assignments = List.copyOf(assignments);
    }

    public static WheelAssignmentPlan empty() {
        return new WheelAssignmentPlan(List.of());
    }

    public record Assignment(String roundId, String memberId, String ownBookId) {
    }
}
