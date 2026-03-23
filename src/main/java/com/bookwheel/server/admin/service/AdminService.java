package com.bookwheel.server.admin.service;


import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.entity.Penalty;
import com.bookwheel.server.admin.repository.PenaltyRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {
    // TODO: 신고 목록 조회, 신고처리 로직 추가

    private final UserRepository userRepository;
    private final PenaltyRepository penaltyRepository;
    private final PostRepository postRepository;
    private final S3Service s3Service;

    //회원 강제 탈퇴/정지 시키기
    @Transactional
    public AdminBanResponse banUser(String userPK, AdminBanRequest request) {

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ALREADY_BANNED_USER);
        }

        user.applyBan(request.banType());



        String reasonMessage = (request.reasonCode() == BanReason.ETC)
            ? request.detailedReason()
            : request.reasonCode().getDescription();

        Penalty history = Penalty.builder()
            .user(user)
            .banType(request.banType())
            .reasonMessage(reasonMessage)
            .releaseDate(user.getBanExpiredAt())
            .build();

        penaltyRepository.save(history);

        return AdminBanResponse.builder()
            .userPK(user.getId())
            .nickname(user.getNickname())
            .status(user.getBanStatus())
            .banType(request.banType())
            .reasonMessage(reasonMessage)
            .bannedAt(LocalDateTime.now())
            .releaseDate(user.getBanExpiredAt())
            .build();
    }

    public List<PenaltyResponse> getPenalties(String userPK) {
        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Penalty> histories = penaltyRepository.findByUserOrderByBannedAtDesc(user);

        return histories.stream()
            .map(PenaltyResponse::from)
            .collect(Collectors.toList());

    }

    public List<AdminPostResponse> getAllPost() {
        List<Post> posts = postRepository.findAllWithDetails();
        return posts.stream()
            .map(AdminPostResponse::from)
            .toList();
    }

    @Transactional
    public void deletePost(Long postId, AdminPostDeleteRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        log.info("게시물 삭제 - ID: {}, 사유: {}", post.getPostId(), request.reason());//TODO: 알림 기능과 연결

        post.getImages().forEach(postImage -> {
            s3Service.deleteObject(postImage.getObjectKey());
        });
        postRepository.delete(post);
    }
}



