package com.bookwheel.server.community.service;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.repository.PostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GalleryVisibilityServiceTest {
    @Mock PostRepository posts;
    @Spy CursorUtils cursors = new CursorUtils(new ObjectMapper().findAndRegisterModules());
    @InjectMocks BookService service;
    private final String userPK = "viewer-pk";
    private final String isbn = "9780132350884";
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 3, 12, 0);

    @Test
    void galleryCursorUsesLastVisiblePostAndNextPageKeepsViewer() {
        when(posts.findGalleryPage(null, 3, userPK)).thenReturn(List.of(post(9L), post(5L), post(2L)));
        when(posts.countGalleryPosts(userPK)).thenReturn(3L);
        var first = service.getGallery(null, 2, userPK);
        assertThat(first.content()).extracting(GalleryResponseDto::postId).containsExactly(9L, 5L);
        assertThat(first.totalElements()).isEqualTo(3L);
        assertThat(first.hasNext()).isTrue();
        GalleryCursor cursor = cursors.decode(first.nextCursor(), GalleryCursor.class);
        assertThat(cursor).isEqualTo(new GalleryCursor(time, 5L));

        when(posts.findGalleryPage(cursor, 3, userPK)).thenReturn(List.of(post(2L)));
        var next = service.getGallery(first.nextCursor(), 2, userPK);
        assertThat(next.content()).extracting(GalleryResponseDto::postId).containsExactly(2L);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(next.totalElements()).isNull();
        verify(posts, times(1)).countGalleryPosts(userPK);
    }

    @Test
    void bookGalleryPassesSameViewerToPagesAndCount() {
        when(posts.findGalleryPageByIsbn(isbn, null, 2, userPK)).thenReturn(List.of(post(9L), post(5L)));
        when(posts.countGalleryPostsByIsbn(isbn, userPK)).thenReturn(2L);
        var first = service.getGalleryByIsbn(isbn, null, 1, userPK);
        assertThat(first.totalElements()).isEqualTo(2L);
        assertThat(first.content()).extracting(GalleryResponseDto::postId).containsExactly(9L);
        GalleryCursor cursor = cursors.decode(first.nextCursor(), GalleryCursor.class);
        when(posts.findGalleryPageByIsbn(isbn, cursor, 2, userPK)).thenReturn(List.of(post(5L)));

        var next = service.getGalleryByIsbn(isbn, first.nextCursor(), 1, userPK);
        assertThat(next.content()).extracting(GalleryResponseDto::postId).containsExactly(5L);
        assertThat(next.totalElements()).isNull();
        assertThat(next.hasNext()).isFalse();
        verify(posts, times(1)).countGalleryPostsByIsbn(isbn, userPK);
    }

    @Test
    void emptyVisibleGalleryDoesNotReturnNextCursor() {
        when(posts.findGalleryPage(null, 3, userPK)).thenReturn(List.of());
        var response = service.getGallery(null, 2, userPK);
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    private Post post(Long postId) {
        return Post.builder().postId(postId).bookInfo(BookInfo.builder().isbn(isbn).build()).createdAt(time).build();
    }
}
