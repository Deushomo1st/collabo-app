package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.ProfileResponse;
import com.collabo.backend.dto.UpdateProfileRequest;
import com.collabo.backend.service.ProfileService;
import org.springframework.web.bind.annotation.*;

/** Profiles: read anyone's, edit your own. Thin shell over ProfileService. Sign-in required (default rule). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileService profiles;
    private final CurrentUser current;

    public UserController(ProfileService profiles, CurrentUser current) {
        this.profiles = profiles;
        this.current = current;
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        var me = current.require();
        return profiles.view(me.getUsername(), me);
    }

    @PatchMapping("/me")
    public ProfileResponse update(@RequestBody UpdateProfileRequest req) {
        return profiles.update(current.require(), req);
    }

    @GetMapping("/{username}")
    public ProfileResponse view(@PathVariable String username) {
        return profiles.view(username, current.require());
    }
}
