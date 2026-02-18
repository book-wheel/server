package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.dto.GroupSearchCondition;
import com.bookwheel.server.group.entity.Group;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class GroupSpecification {
    public static Specification<Group> searchWith(GroupSearchCondition condition) {
        // - root: 조회한 대상 엔티티 (Group 테이블)
        // - query: 쿼리 자체 (SELECT, WHERE, ORDER BY 등)
        // - builder: 조건 만드는 도구 (equal, like 등)

        Specification<Group> spec = (root, query, builder) -> builder.conjunction();

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
            spec = spec.and((r, q, b) -> b.equal(r.get("groupState"), condition.state()));
        }

        if (StringUtils.hasText(condition.keyword())) {
            spec = spec.and((r, q, b) -> b.like(r.get("groupName"), "%" + condition.keyword() + "%")
            );
        }
        return spec;
    }
}
