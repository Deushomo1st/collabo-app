package com.collabo.backend.service;

import com.collabo.backend.dto.SpaceDtos.SpaceResponse;
import com.collabo.backend.entity.Space;
import com.collabo.backend.entity.SpacePermission;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Where power in a space quietly concentrates, so every change is announced in the room. The response clock has a
 * hard floor, so it cannot be turned into a way to purge people.
 */
@Service
@Transactional
public class SpaceSettingsService {

    private final SpaceRepository spaces;
    private final SpaceService spaceService;
    private final SpaceThreadService spaceThreads;

    public SpaceSettingsService(SpaceRepository spaces, SpaceService spaceService, SpaceThreadService spaceThreads) {
        this.spaces = spaces; this.spaceService = spaceService; this.spaceThreads = spaceThreads;
    }

    /** Owner or EDIT_SETTINGS. Missing fields stay as they are; nothing is applied unless everything is valid. */
    public SpaceResponse update(User me, UUID spaceId, String rawName, Integer clockHours, Boolean pleas) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("No such space."));
        if (spaceService.roleOf(me, s) == null) throw new ResourceNotFoundException("No such space.");
        if (!spaceService.can(me, s, SpacePermission.EDIT_SETTINGS)) throw new ForbiddenException("You do not have permission to change this space's settings.");

        String name = rawName == null ? null : rawName.trim();
        if (name != null && (name.isEmpty() || name.length() > SpaceService.MAX_NAME)) {
            throw new InvalidProfileException("Give the space a name of up to " + SpaceService.MAX_NAME + " characters.");
        }
        if (clockHours != null) SpaceService.checkClock(clockHours);

        if (name != null && !name.equals(s.getName())) {
            s.rename(name);
            spaceThreads.renameWorkspace(s);
            spaceThreads.announce(s, me.getUsername() + " renamed the space to " + name + ".");
        }
        if (clockHours != null && clockHours != s.getResponseClockHours()) {
            spaceThreads.announce(s, me.getUsername() + " changed the response clock from " + s.getResponseClockHours() + " to " + clockHours + " hours.");
            s.setResponseClockHours(clockHours);
        }
        if (pleas != null && pleas != s.isPleasEnabled()) {
            s.setPleasEnabled(pleas);
            spaceThreads.announce(s, me.getUsername() + " switched pleas " + (pleas ? "on" : "off") + ".");
        }
        spaces.save(s);
        return spaceService.get(me, spaceId);
    }
}
