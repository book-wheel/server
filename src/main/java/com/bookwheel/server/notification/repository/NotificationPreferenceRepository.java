package com.bookwheel.server.notification.repository;

import com.bookwheel.server.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserPK(String userPK);

    List<NotificationPreference> findAllByUserPKIn(Collection<String> userPKs);
}
