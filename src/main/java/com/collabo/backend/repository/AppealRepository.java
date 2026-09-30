package com.collabo.backend.repository;

import com.collabo.backend.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppealRepository extends JpaRepository<Appeal, UUID> {
    Optional<Appeal> findByRecordId(UUID recordId);
    List<Appeal> findByRecordIdIn(Collection<UUID> recordIds);
    List<Appeal> findTop100ByOutcomeIsNullOrderByCreatedAtAsc();
    List<Appeal> findTop100ByOutcomeIsNotNullOrderByDecidedAtDesc();
}
