package com.collabo.backend.repository;

import com.collabo.backend.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    /** Just the timestamp, so a profile view doesn't load the image. */
    @Query("select a.updatedAt from UserAvatar a where a.userId = :userId")
    Optional<Instant> findUpdatedAt(UUID userId);

    /** {userId, updatedAt} for everyone in the list who has a picture, without loading any image. */
    @Query("select a.userId, a.updatedAt from UserAvatar a where a.userId in :userIds")
    java.util.List<Object[]> versionsOf(java.util.Collection<UUID> userIds);
}
