package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.dto.search.GroupSearchCondition;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

public class GroupSpecification {
    public static Specification<Group> searchWith(GroupSearchCondition condition, LocalDate currentDate) {
        // - root: 조회한 대상 엔티티 (Group 테이블)
        // - query: 쿼리 자체 (SELECT, WHERE, ORDER BY 등)
        // - builder: 조건 만드는 도구 (equal, like 등)

        Specification<Group> spec = (root, query, builder) -> builder.conjunction();

        // 삭제된 모임은 게시물 보존과 별개로 일반 모임 탐색 결과에는 노출하지 않는다.
        spec = spec.and((root, query, builder) -> builder.or(
                builder.isNull(root.get("groupState")),
                builder.notEqual(root.get("groupState"), State.DELETED)
        ));

        // 시작일이 지난 모집 모임은 더 이상 자동 시작되지 않으므로 일반 탐색 결과에서 제외한다.
        // null 상태는 기존 데이터 호환을 위해 RECRUITING으로 취급하며, 시작일이 없는 모임은 제외하지 않는다.
        spec = spec.and((root, query, builder) -> builder.not(builder.and(
                builder.or(
                        builder.equal(root.get("groupState"), State.RECRUITING),
                        builder.isNull(root.get("groupState"))
                ),
                builder.isNotNull(root.get("startDate")),
                builder.lessThan(root.<LocalDate>get("startDate"), currentDate)
        )));

        // 최대 정원 또는 일정 목표 인원에 도달한 모집 모임은 가입 API에서도 거절되므로 탐색에서 제외한다.
        spec = spec.and((root, query, builder) -> builder.not(builder.and(
                builder.or(
                        builder.equal(root.get("groupState"), State.RECRUITING),
                        builder.isNull(root.get("groupState"))
                ),
                builder.or(
                        builder.and(
                                builder.isNotNull(root.get("maxMembers")),
                                builder.greaterThanOrEqualTo(
                                        root.<Integer>get("currentMembers"),
                                        root.<Integer>get("maxMembers")
                                )
                        ),
                        builder.and(
                                builder.isNotNull(root.get("targetMemberCount")),
                                builder.greaterThanOrEqualTo(
                                        root.<Integer>get("currentMembers"),
                                        root.<Integer>get("targetMemberCount")
                                )
                        )
                )
        )));

        if (condition == null) {
            return spec;
        }

        // 온/오프라인 필터
        if (StringUtils.hasText(condition.type())) {
            boolean isOffline = "OFFLINE".equals(condition.type());
            spec = spec.and((r, q, b) -> b.equal(r.get("groupOffline"), isOffline));
        }

        // 지역 필터
        if (condition.region() != null) {
            spec = spec.and((r, q, b) -> b.equal(r.get("groupRegion"), condition.region()));
        }

        if (condition.state() != null) {
            if (condition.state() == State.RECRUITING) {
                spec = spec.and((r, q, b) -> b.or(
                        b.equal(r.get("groupState"), State.RECRUITING),
                        b.isNull(r.get("groupState"))
                ));
            } else {
                spec = spec.and((r, q, b) -> b.equal(r.get("groupState"), condition.state()));
            }
        }

        if (StringUtils.hasText(condition.keyword())) {
            spec = spec.and((r, q, b) -> b.like(r.get("groupName"), "%" + condition.keyword() + "%")
            );
        }
        return spec;
    }
}
