package com.bookwheel.server.community.image;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.PostImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostThumbnailService {

    private final S3Service s3Service;
    private final PostThumbnailStore postThumbnailStore;

    // 게시물 저장 트랜잭션이 커밋된 뒤에 썸네일을 만들어 반영한다.
    //
    // 생성 한 건은 MinIO 왕복 2회(원본 다운로드 + 썸네일 업로드)라 이미지 5장이면 수 초가 걸린다.
    // 이 작업을 게시물 작성 트랜잭션 안에서 돌리면 그동안 모임 행 잠금(findByGroupIdForUpdate)과
    // DB 커넥션을 붙잡아, 같은 모임에 동시에 글을 쓰는 사람이 잠금 대기로 막힌다.
    // 커밋 이후로 미루면 롤백된 게시물의 썸네일이 MinIO 에 남는 문제도 함께 사라진다.
    public void registerPostCommitThumbnailGeneration(List<PostImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("썸네일 생성은 트랜잭션 커밋 이후에만 실행할 수 있습니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                generateAndStore(images);
            }
        });
    }

    // 업로드된 원본으로 갤러리용 축소본을 만들어 올리고 그 objectKey 를 돌려준다.
    // 실패하면 null 을 돌려준다. 썸네일이 없다고 게시물 작성까지 실패시키지 않는다.
    // 갤러리 조회는 썸네일 키가 없으면 원본으로 폴백한다.
    public String createThumbnail(String originalKey) {
        try {
            byte[] original = s3Service.getObjectBytes(originalKey, PostThumbnailGenerator.MAX_SOURCE_BYTES);
            byte[] thumbnail = PostThumbnailGenerator.generate(original);
            String thumbnailKey = PostThumbnailGenerator.toThumbnailKey(originalKey);

            s3Service.putObject(thumbnailKey, thumbnail, PostThumbnailGenerator.CONTENT_TYPE);

            log.debug("썸네일 생성 완료: originalKey={}, thumbnailKey={}, bytes={}",
                    originalKey, thumbnailKey, thumbnail.length);
            return thumbnailKey;
        } catch (RuntimeException exception) {
            log.warn("썸네일 생성 실패, 원본으로 폴백한다: originalKey={}, error={}",
                    originalKey, exception.getMessage());
            return null;
        }
    }

    private void generateAndStore(List<PostImage> images) {
        try {
            Map<Long, String> thumbnailKeysByPostImageId = new LinkedHashMap<>();
            for (PostImage image : images) {
                if (image.getPostImageId() == null || !StringUtils.hasText(image.getObjectKey())) {
                    continue;
                }

                String thumbnailKey = createThumbnail(image.getObjectKey());
                if (thumbnailKey != null) {
                    thumbnailKeysByPostImageId.put(image.getPostImageId(), thumbnailKey);
                }
            }

            postThumbnailStore.applyThumbnailKeys(thumbnailKeysByPostImageId);
        } catch (RuntimeException exception) {
            // 게시물은 이미 커밋됐다. 여기서 예외를 올리면 성공한 작성 요청이 500 으로 뒤집힌다.
            // 썸네일이 비면 갤러리가 원본으로 폴백하므로 기록만 남기고 넘어간다.
            log.warn("썸네일 반영 실패, 원본으로 폴백한다: error={}", exception.getMessage());
        }
    }
}
