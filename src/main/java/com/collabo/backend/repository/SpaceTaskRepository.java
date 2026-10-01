package com.collabo.backend.repository;

import com.collabo.backend.entity.SpaceTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceTaskRepository extends JpaRepository<SpaceTask, UUID> {
    List<SpaceTask> findBySpaceIdOrderByCreatedAtAsc(UUID spaceId);
    Optional<SpaceTask> findByIdAndSpaceId(UUID id, UUID spaceId);
}
