package com.bookwheel.server.community.image;

import com.bookwheel.server.common.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostThumbnailService {

    private final S3Service s3Service;

    // 업로드된 원본으로 갤러리용 축소본을 만들어 올리고 그 objectKey 를 돌려준다.
    // 실패하면 null 을 돌려준다. 썸네일이 없다고 게시물 작성까지 실패시키지 않는다.
    // 갤러리 조회는 썸네일 키가 없으면 원본으로 폴백한다.
    public String createThumbnail(String originalKey) {
        try {
            byte[] original = s3Service.getObjectBytes(originalKey);
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
}
