package com.primefuel.fulltank.platform.notification.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.notification.infrastructure.persistence.jpa.entities.NotificationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPersistenceRepository extends JpaRepository<NotificationPersistenceEntity, Long> {
    List<NotificationPersistenceEntity> findByUserId(Long userId);
    List<NotificationPersistenceEntity> findByUserIdAndReadFalse(Long userId);
    Optional<NotificationPersistenceEntity> findBySourceEventKey(String sourceEventKey);
}
