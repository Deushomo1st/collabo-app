package com.collabo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserDto {

    @NotBlank(message = "Please set email")
    @Email(message = "Not a valid email")
    private String email;

    @NotBlank(message = "Choose a username")
    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    private String username;

    // Blank is caught by @NotBlank; the >=6 floor and the "secure" bar are
    // enforced in AuthService (they have a two-tier bypass flow).
    @NotBlank(message = "Please set password")
    private String password;

    // true when the client acknowledged a weak password via "proceed anyway".
    private boolean weakPasswordAccepted;

    // Rate-limit challenge fields (optional — only present when re-submitting
    // after the server asked the client to prove it is human).
    private String challengeToken;
    private String challengeAnswer;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isWeakPasswordAccepted() { return weakPasswordAccepted; }
    public void setWeakPasswordAccepted(boolean weakPasswordAccepted) { this.weakPasswordAccepted = weakPasswordAccepted; }

    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }

    public String getChallengeAnswer() { return challengeAnswer; }
    public void setChallengeAnswer(String challengeAnswer) { this.challengeAnswer = challengeAnswer; }
}
