package com.bookwheel.server.notification.repository;

import com.bookwheel.server.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserPK(String userPK);

    List<NotificationPreference> findAllByUserPKIn(Collection<String> userPKs);

    @Modifying(flushAutomatically = true)
    @Query("""
            update NotificationPreference preference
               set preference.expoPushToken = null
             where preference.expoPushToken = :expoPushToken
               and preference.userPK <> :userPK
            """)
    int clearExpoPushTokenFromOtherUsers(
            @Param("expoPushToken") String expoPushToken,
            @Param("userPK") String userPK
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update NotificationPreference preference
               set preference.expoPushToken = null
             where preference.userPK = :userPK
            """)
    int clearExpoPushTokenByUserPK(@Param("userPK") String userPK);

    @Modifying(flushAutomatically = true)
    @Query("""
            update NotificationPreference preference
               set preference.expoPushToken = null
             where preference.expoPushToken = :expoPushToken
            """)
    int clearExpoPushTokenByValue(@Param("expoPushToken") String expoPushToken);
}
