package com.bookwheel.server.admin.listener;

import com.bookwheel.server.admin.dto.AdminBanRequest;
import com.bookwheel.server.admin.dto.BanReason;
import com.bookwheel.server.admin.repository.PenaltyRepository;
import com.bookwheel.server.admin.service.AdminService;
import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import com.bookwheel.server.common.jwt.JwtAuthenticationFilter;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.service.PostDeletionService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserBanTokenRevocationListenerTest {

    private AnnotationConfigApplicationContext context;
    private AdminService adminService;
    private JwtAuthenticationFilter filter;
    private User user;
    private String token;
    private final Set<String> revokedKeys = new HashSet<>();
    private final TransactionTemplate transaction = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object value, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    });

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            revokedKeys.add(invocation.getArgument(0));
            return null;
        }).when(values).set(anyString(), eq("true"), any(Duration.class));
        when(redis.hasKey(anyString())).thenAnswer(invocation -> revokedKeys.contains(invocation.getArgument(0)));
        AccessTokenRevocationService revocation = new AccessTokenRevocationService(redis);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(AccessTokenRevocationService.class, () -> revocation);
        context.registerBean(UserBanTokenRevocationListener.class);
        context.refresh();

        user = User.builder().nickname("author").build();
        UserRepository users = mock(UserRepository.class);
        when(users.findByUserPKForUpdate(user.getId())).thenReturn(Optional.of(user));
        adminService = new AdminService(users, mock(PenaltyRepository.class), mock(PostRepository.class),
                mock(PostDeletionService.class), context, Clock.systemUTC());
        JwtTokenProvider provider = new JwtTokenProvider(
                "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n");
        token = provider.createAccessToken(user.getId(), AuthRole.USER);
        filter = new JwtAuthenticationFilter(provider, revocation);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void committedBanRejectsPreviouslyIssuedToken() throws Exception {
        assertThat(authenticatesExistingToken()).isTrue();

        transaction.executeWithoutResult(status -> {
            adminService.banUser(user.getId(), new AdminBanRequest("THREE_DAYS", BanReason.ETC, "spam"));
            assertThat(revokedKeys).isEmpty();
        });

        assertThat(revokedKeys).containsExactly("AUTH:REVOKED_USER:" + user.getId());
        assertThat(authenticatesExistingToken()).isFalse();
    }

    @Test
    void rolledBackBanDoesNotRevokeToken() throws Exception {
        transaction.executeWithoutResult(status -> {
            adminService.banUser(user.getId(), new AdminBanRequest("THREE_DAYS", BanReason.ETC, "spam"));
            status.setRollbackOnly();
        });

        assertThat(revokedKeys).isEmpty();
        assertThat(authenticatesExistingToken()).isTrue();
    }

    private boolean authenticatesExistingToken() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts/1/comments");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }
}
