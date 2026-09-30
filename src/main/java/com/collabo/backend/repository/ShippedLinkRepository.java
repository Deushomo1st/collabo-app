package com.collabo.backend.repository;

import com.collabo.backend.entity.ShippedLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShippedLinkRepository extends JpaRepository<ShippedLink, UUID> {
    List<ShippedLink> findByEntryIdIn(Collection<UUID> entryIds);
    long countByEntryId(UUID entryId);
    void deleteByEntryId(UUID entryId);
}
