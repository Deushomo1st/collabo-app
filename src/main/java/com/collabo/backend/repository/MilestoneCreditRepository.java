package com.collabo.backend.repository;

import com.collabo.backend.entity.MilestoneCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneCreditRepository extends JpaRepository<MilestoneCredit, UUID> {
    List<MilestoneCredit> findByMilestoneIdIn(Collection<UUID> milestoneIds);
    Optional<MilestoneCredit> findByMilestoneIdAndUserId(UUID milestoneId, UUID userId);
}
