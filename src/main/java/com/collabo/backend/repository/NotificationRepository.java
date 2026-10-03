package com.collabo.backend.repository;

import com.collabo.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndReadFalse(UUID userId);
    void deleteByUserId(UUID userId);
    boolean existsByUserIdAndLinkAndReadFalse(UUID userId, String link);
    boolean existsByUserIdAndLinkAndBodyAndReadFalse(UUID userId, String link, String body);
    List<Notification> findByUserIdAndReadFalse(UUID userId);
    List<Notification> findByUserIdAndLinkAndReadFalse(UUID userId, String link);
    List<Notification> findByRefKeyAndActionRequiredTrue(String refKey);
}
