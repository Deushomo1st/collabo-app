package com.collabo.backend.service;

import com.collabo.backend.dto.InvestigationDtos.FindingView;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** An assigned moderator's findings and screenshots on one investigation. Only the assigned moderator writes; the admin reads. */
@Service
@Transactional
public class FindingService {

    public static final int MAX_BYTES = 1024 * 1024, MAX_PER_FINDING = 5;
    static final int MAX_TEXT = 2000, MAX_SIDE = 6000;   // a 4K screen fits; this only stops pixel bombs

    private final InvestigationRepository investigations;
    private final FindingRepository findings;
    private final FindingImageRepository images;
    private final ModeratorRepository moderators;

    public FindingService(InvestigationRepository investigations, FindingRepository findings, FindingImageRepository images, ModeratorRepository moderators) {
        this.investigations = investigations; this.findings = findings; this.images = images; this.moderators = moderators;
    }

    /** The investigation, if it is this moderator's and still open. Anything else looks like it does not exist. */
    @Transactional(readOnly = true)
    public Investigation owned(Moderator m, UUID investigationId) {
        return investigations.findById(investigationId).filter(i -> m.getId().equals(i.getModeratorId()) && !i.isClosed())
                .orElseThrow(() -> new ResourceNotFoundException("No such investigation."));
    }

    public FindingView add(Moderator m, UUID investigationId, String rawText, String rawRecommendation) {
        Investigation i = owned(m, investigationId);
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT) throw new InvalidProfileException("Write what you found, up to " + MAX_TEXT + " characters.");
        String rec = rawRecommendation == null || rawRecommendation.isBlank() ? null : rawRecommendation.trim().toUpperCase();
        if (rec != null && (i.getAppealId() == null || !(rec.equals("STICKS") || rec.equals("DROPS"))))
            throw new InvalidProfileException("Only an appeal takes a recommendation, and it is STICKS or DROPS.");
        Finding f = findings.save(new Finding(i.getId(), m.getId(), text, rec));
        i.markReported();
        investigations.save(i);
        return new FindingView(f.getId(), m.getName(), f.getText(), f.getRecommendation(), f.getCreatedAt(), List.of());
    }

    public UUID addScreenshot(Moderator m, UUID investigationId, UUID findingId, String contentType, byte[] bytes) {
        owned(m, investigationId);
        Finding f = findings.findById(findingId).filter(x -> x.getInvestigationId().equals(investigationId) && x.getModeratorId().equals(m.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No such finding."));
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.equals("image/png") && !type.equals("image/jpeg")) throw new InvalidProfileException("A screenshot is a PNG or a JPEG.");
        if (images.countByFindingId(f.getId()) >= MAX_PER_FINDING) throw new InvalidProfileException("At most " + MAX_PER_FINDING + " screenshots per finding.");
        checkImage(bytes, type.equals("image/png") ? "png" : "jpeg");
        return images.save(new FindingImage(f.getId(), type, bytes)).getId();
    }

    @Transactional(readOnly = true)
    public List<FindingView> list(UUID investigationId) {
        List<Finding> rows = findings.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, List<UUID>> shots = new HashMap<>();
        for (Object[] r : images.idsFor(rows.stream().map(Finding::getId).toList()))
            shots.computeIfAbsent((UUID) r[1], k -> new ArrayList<>()).add((UUID) r[0]);
        Map<UUID, String> names = moderators.findAllById(rows.stream().map(Finding::getModeratorId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Moderator::getId, Moderator::getName));
        return rows.stream().map(f -> new FindingView(f.getId(), names.getOrDefault(f.getModeratorId(), "(removed)"), f.getText(), f.getRecommendation(),
                f.getCreatedAt(), shots.getOrDefault(f.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public FindingImage screenshot(UUID id) {
        return images.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such screenshot."));
    }

    /** Really decodes it, so a header with garbage after it fails here. */
    static void checkImage(byte[] bytes, String format) {
        if (bytes == null || bytes.length == 0) throw new InvalidProfileException("Send a screenshot.");
        if (bytes.length > MAX_BYTES) throw new InvalidProfileException("A screenshot can be at most 1 MB.");
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? Collections.emptyIterator() : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw wrong(format);
            ImageReader reader = readers.next();
            try {
                if (!format.equalsIgnoreCase(reader.getFormatName())) throw wrong(format);
                reader.setInput(in);
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) throw new InvalidProfileException("The screenshot is too large in pixels.");
                reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidProfileException ipe) throw ipe;
            throw wrong(format);
        }
    }

    private static InvalidProfileException wrong(String format) { return new InvalidProfileException("That is not a valid " + format.toUpperCase() + " image."); }
}
