package com.collabo.backend.repository;

import com.collabo.backend.entity.RemovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemovalRecordRepository extends JpaRepository<RemovalRecord, UUID> {
    List<RemovalRecord> findTop50ByRemovedIdOrderByCreatedAtDesc(UUID removedId);
}
