package com.bookwheel.server.community.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostDeletionService {

    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final S3Service s3Service;

    @Transactional
    public void delete(Post post) {
        Long postId = post.getPostId();
        // 게시물이 사라진 뒤에는 이미지 키를 다시 읽을 수 없으므로 삭제 전에 수집한다.
        List<String> imageObjectKeys = post.getImages().stream()
            .map(PostImage::getObjectKey)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        // 게시물을 가리키는 알림은 게시물이 사라지면 열 수 없는 링크가 되므로 함께 정리한다.
        notificationService.deleteByPostId(postId);
        // 댓글·좋아요는 Post의 cascade 대상이 아니므로 게시물보다 먼저 지운다. (이미지·신고는 cascade로 정리된다)
        postCommentRepository.deleteAllByPost(post);
        postLikeRepository.deleteAllByPost(post);
        postRepository.delete(post);
        registerPostCommitImageCleanup(imageObjectKeys);
    }

    // 트랜잭션이 실제 커밋된 뒤 수집된 S3 객체만 best-effort로 삭제한다.
    private void registerPostCommitImageCleanup(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("게시물 이미지 정리는 트랜잭션 커밋 이후에만 실행할 수 있습니다.");
        }

        // DB가 롤백되면 이미지도 남겨야 하므로 커밋이 성공한 뒤에만 S3 삭제를 시작한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectKeys.forEach(s3Service::deleteObject);
            }
        });
    }
}
