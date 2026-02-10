package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.*;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final GroupRepository groupRepository;

    @Transactional
    public GroupCreateResponse createGroup(GroupCreateRequest request) {
        Group group = request.toEntity();

        // TODO: [User구현후] 로그인한 유저 ID를 가져와서 방장(Leader)으로 설정하기
        // -> 그룹 엔티티에 방장 닉네임 연결

        // 그룹 이름 중복 검사
        if (groupRepository.existsByGroupName(request.groupName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }

        // 비공개방이면서, 비밀번호가 없다면 오류
        if (!request.groupPublic() && !StringUtils.hasText(request.groupPassword())) {
            throw new BusinessException(ErrorCode.GROUP_PASSWORD_REQUIRED);
        }
        // 오프라인 모임이면서, 지역 선택이 없다면 오류
        if (request.groupOffline() && request.groupRegion() == null) {
            throw new BusinessException(ErrorCode.GROUP_REGION_REQUIRED);
        }

        Group saveGroup = groupRepository.save(group);
        return GroupCreateResponse.of(saveGroup.getGroupId());
    }

    public Page<GroupSearchResponse> getGroups(GroupSearchCondition condition, Pageable pageable) {
        // 1. Specification을 이용해 동적 쿼리 생성 및 조회
        Page<Group> groupPage = groupRepository.findAll(GroupSpecification.searchWith(condition), pageable);

        // 2. Entity -> DTO 변환 (Page의 map 메서드 활용)
        return groupPage.map(GroupSearchResponse::from);
    }

    public GroupDetailResponse getGroup(String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        return GroupDetailResponse.from(group);
    }
}
