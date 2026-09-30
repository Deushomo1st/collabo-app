package com.collabo.backend.repository;

import com.collabo.backend.entity.Yarn;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface YarnRepository extends JpaRepository<Yarn, UUID> {

    List<Yarn> findByThreadIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID threadId, Instant before, Pageable page);

    long countByThreadId(UUID threadId);

    /** Yarns I have not read: newer than my marker and not sent by me (system yarns count). */
    @Query("select count(y) from Yarn y where y.threadId = :threadId and y.createdAt > :after "
            + "and (y.senderId is null or y.senderId <> :me)")
    long countUnread(@Param("threadId") UUID threadId, @Param("after") Instant after, @Param("me") UUID me);
}
