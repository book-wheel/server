package com.bookwheel.server.community.image;

import com.bookwheel.server.admin.repository.PostImageRepository;
import com.bookwheel.server.community.entity.PostImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

// 썸네일 도입 이전에 올라온 이미지의 축소본을 한 번에 만들어 둔다.
//
// 기본값은 꺼짐이다. 랩 서버에서 한 번만 돌리고 다시 끈다.
//   docker compose 환경변수에 POST_THUMBNAIL_BACKFILL_ENABLED=true 를 넣고 backend 를 재기동한 뒤,
//   docker compose logs -f backend | grep "썸네일 백필" 로 진행 상황을 본다.
//   "썸네일 백필 종료" 로그를 확인하면 환경변수를 지우고 다시 재기동한다.
// 여러 번 돌려도 이미 채워진 행은 건너뛰므로 결과는 같다(멱등).
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "post.thumbnail.backfill.enabled", havingValue = "true")
public class PostThumbnailBackfillRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 100;

    private final PostImageRepository postImageRepository;
    private final PostThumbnailService postThumbnailService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("썸네일 백필 시작: batchSize={}", BATCH_SIZE);

        PageRequest batchRequest = PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "postImageId"));
        long lastPostImageId = 0L;
        int succeededCount = 0;
        int failedCount = 0;

        try {
            while (true) {
                List<PostImage> batch = postImageRepository
                        .findByThumbnailKeyIsNullAndPostImageIdGreaterThan(lastPostImageId, batchRequest);
                if (batch.isEmpty()) {
                    break;
                }

                for (PostImage image : batch) {
                    // 성공 여부와 무관하게 커서를 전진시킨다. 디코딩할 수 없는 원본에서 멈추지 않기 위함이다.
                    lastPostImageId = image.getPostImageId();

                    String thumbnailKey = postThumbnailService.createThumbnail(image.getObjectKey());
                    if (thumbnailKey == null) {
                        failedCount++;
                        continue;
                    }

                    image.applyThumbnailKey(thumbnailKey);
                    succeededCount++;
                }

                postImageRepository.saveAll(batch);
                log.info("썸네일 백필 진행: lastPostImageId={}, 성공={}, 실패={}",
                        lastPostImageId, succeededCount, failedCount);
            }
        } catch (RuntimeException exception) {
            // ApplicationRunner 에서 예외가 올라가면 Spring Boot 가 기동을 실패시켜 컨테이너가 죽는다.
            // 백필이 중단됐다고 서버를 못 뜨게 할 이유는 없다. 이미 저장된 배치는 남아 있으므로
            // 원인을 고치고 다시 켜서 돌리면 마지막 지점부터 이어진다.
            log.error("썸네일 백필 중단: lastPostImageId={}, 성공={}, 실패={}, error={}",
                    lastPostImageId, succeededCount, failedCount, exception.getMessage());
            return;
        }

        // 실패한 행은 thumbnail_key 가 비어 있고, 갤러리는 그 행만 원본으로 폴백한다.
        log.info("썸네일 백필 종료: 성공={}, 실패={}", succeededCount, failedCount);
    }
}
