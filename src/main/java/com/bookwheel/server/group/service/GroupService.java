package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
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
        if (request.groupOffline() && !StringUtils.hasText(String.valueOf(request.groupRegion()))) {
            throw new BusinessException(ErrorCode.GROUP_REGION_REQUIRED);
        }

        Group saveGroup = groupRepository.save(group);
        return GroupCreateResponse.of(saveGroup.getGroupId());
    }
}
