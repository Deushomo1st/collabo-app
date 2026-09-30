package com.collabo.backend.repository;

import com.collabo.backend.entity.FindingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FindingImageRepository extends JpaRepository<FindingImage, UUID> {
    long countByFindingId(UUID findingId);

    /** Ids only ({imageId, findingId}), so the bytes stay in the table. */
    @Query("select i.id, i.findingId from FindingImage i where i.findingId in :findingIds order by i.createdAt")
    List<Object[]> idsFor(Collection<UUID> findingIds);
}
