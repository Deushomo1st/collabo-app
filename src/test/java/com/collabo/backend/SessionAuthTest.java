package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login / logout / me against an in-memory H2 (no Postgres needed). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessions;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class SessionAuthTest {

    static final String PASSWORD = "Passw0rd!x9";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String name;

    @BeforeEach
    void account() {
        name = "u" + UUID.randomUUID().toString().substring(0, 8);
        make(name, true);
    }

    private void make(String username, boolean verified) {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(verified);
        users.save(u);
    }

    private String body(String who, String pw) {
        return "{\"identifier\":\"" + who + "\",\"password\":\"" + pw + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions login(String who, String pw) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json").content(body(who, pw)));
    }

    @Test
    void loginByUsernameOrEmailOpensASessionThatMeAndLogoutUse() throws Exception {
        Cookie session = login(name, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name)).andReturn().getResponse().getCookie("COLLABO_SESSION");
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        org.junit.jupiter.api.Assertions.assertTrue(session.isHttpOnly(), "session cookie must be HttpOnly");

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(name));
        login(name + "@T.dev", PASSWORD).andExpect(status().isOk());   // email works, case-insensitively

        mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCallsAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownAccountLookIdentical() throws Exception {
        String wrong = login(name, "nope-nope").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = login("nobody-" + name, "nope-nope").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(wrong, unknown);
    }

    @Test
    void unverifiedAccountIsToldToConfirmOnlyAfterTheRightPassword() throws Exception {
        String pending = "p" + name;
        make(pending, false);
        login(pending, "nope-nope").andExpect(status().isUnauthorized());
        login(pending, PASSWORD).andExpect(status().isConflict()).andExpect(jsonPath("$.requiresVerification").value(true));
    }

    @Test
    void fiveFailuresLockThatAccountOutEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) login(name, "wrong-" + i).andExpect(status().isUnauthorized());
        login(name, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.retryAfterSeconds").exists());
        login(name + "2", "x").andExpect(status().isUnauthorized());   // other identifiers are unaffected
    }

    @Test
    void yarnsNeedALoginAndIgnoreTheOldDevHeader() throws Exception {
        mvc.perform(get("/api/yarns/threads")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/yarns/threads").header("X-Dev-User", name)).andExpect(status().isUnauthorized());

        Cookie session = login(name, PASSWORD).andReturn().getResponse().getCookie("COLLABO_SESSION");
        mvc.perform(get("/api/yarns/threads").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
}
