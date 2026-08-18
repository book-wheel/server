package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GroupSpecificationTest {

    @Mock
    private Root<Group> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<State> groupStatePath;

    @Mock
    private Path<LocalDate> startDatePath;

    @Mock
    private Path<Integer> currentMembersPath;

    @Mock
    private Path<Integer> maxMembersPath;

    @Mock
    private Path<Integer> targetMemberCountPath;

    @Mock
    private Predicate conjunction;

    @Test
    @DisplayName("탐색 조건은 시작일이 지났거나 인원이 찬 모집 모임을 제외한다")
    void searchWith_ExcludesExpiredOrFullRecruitingGroups() {
        LocalDate currentDate = LocalDate.of(2026, 8, 19);
        given(builder.conjunction()).willReturn(conjunction);
        given(root.<State>get("groupState")).willReturn(groupStatePath);
        given(root.<LocalDate>get("startDate")).willReturn(startDatePath);
        given(root.<Integer>get("currentMembers")).willReturn(currentMembersPath);
        given(root.<Integer>get("maxMembers")).willReturn(maxMembersPath);
        given(root.<Integer>get("targetMemberCount")).willReturn(targetMemberCountPath);
        Specification<Group> specification = GroupSpecification.searchWith(null, currentDate);

        specification.toPredicate(root, query, builder);

        then(builder).should(times(2)).equal(groupStatePath, State.RECRUITING);
        then(builder).should().lessThan(startDatePath, currentDate);
        then(builder).should().greaterThanOrEqualTo(currentMembersPath, maxMembersPath);
        then(builder).should().greaterThanOrEqualTo(currentMembersPath, targetMemberCountPath);
    }
}
