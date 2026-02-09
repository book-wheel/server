package com.bookwheel.server.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.group.dto.GroupCreateRequest;
import com.bookwheel.server.group.dto.GroupCreateResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("dev")
public class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Test
    @DisplayName("그룹 생성 성공 (DB 연동 확인 완료)")
    void createGroup_db_success() {
        // Given
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("천안 교환독서방")
                .groupComment("천안에서 독서해요")
                .groupRule("상호존중")
                .groupPublic(true)
                .groupOffline(false)
                .readingPeriod(7)
                .startDate(LocalDate.now().plusDays(1))
                .maxMembers(4)
                .build();

        // When
        GroupCreateResponse response = groupService.createGroup(request);

        // Then
        Group saveGroup = groupRepository.findById(response.groupId()).orElseThrow();

        assertThat(saveGroup.getGroupName()).isEqualTo("천안 교환독서방");
        assertThat(saveGroup.isGroupPublic()).isTrue();
    }

    @Test
    @DisplayName("그룹 생성 실패 (중복 이름 생성)")
    void createGroup_db_fail_duplicate() {
        // Given
        GroupCreateRequest request1 = GroupCreateRequest.builder()
                .groupName("중복이")
                .groupComment("소개")
                .groupRule("규칙")
                .groupPublic(true)
                .maxMembers(4)
                .startDate(LocalDate.now().plusDays(1))
                .readingPeriod(7)
                .build();

        groupService.createGroup(request1);

        GroupCreateRequest request2 = GroupCreateRequest.builder()
                .groupName("중복이")
                .groupComment("소개2")
                .groupRule("규칙2")
                .groupPublic(true)
                .maxMembers(4)
                .startDate(LocalDate.now().plusDays(1))
                .readingPeriod(7)
                .build();

        // When & Then
        assertThatThrownBy(() -> groupService.createGroup(request2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 존재하는 그룹 이름입니다.");
    }
}