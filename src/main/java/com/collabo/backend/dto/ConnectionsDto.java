package com.collabo.backend.dto;

import java.util.List;

/** Everyone around one profile: who follows them, who they follow, and the people who do both (shown as "MyGuy"). */
public record ConnectionsDto(List<PersonDto> followers, List<PersonDto> following, List<PersonDto> myGuy) {}
