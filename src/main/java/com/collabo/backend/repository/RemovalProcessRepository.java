package com.collabo.backend.repository;

import com.collabo.backend.entity.RemovalProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemovalProcessRepository extends JpaRepository<RemovalProcess, UUID> {
    List<RemovalProcess> findTop50BySpaceIdOrderByStartedAtDesc(UUID spaceId);
    Optional<RemovalProcess> findByIdAndSpaceId(UUID id, UUID spaceId);
    boolean existsBySpaceIdAndTargetIdAndState(UUID spaceId, UUID targetId, RemovalProcess.State state);
    List<RemovalProcess> findByStateAndDeadlineBefore(RemovalProcess.State state, Instant now);
    List<RemovalProcess> findBySpaceIdAndState(UUID spaceId, RemovalProcess.State state);
}
