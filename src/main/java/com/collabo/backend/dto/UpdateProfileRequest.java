package com.collabo.backend.dto;

import com.collabo.backend.entity.CredentialsPrivacy;

/** Any field left out (null) stays as it is; an empty string clears a text field. */
public record UpdateProfileRequest(String preferredTitle, String bio, CredentialsPrivacy credentialsPrivacy, com.collabo.backend.entity.MessagePrivacy messagePrivacy) {}
