package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Posts to The Gaze: create, read, delete and the application window, over HTTP against an in-memory H2. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:posts;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class PostApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob;
    Cookie annS, bobS;

    @BeforeEach
    void accounts() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag;
        annS = signIn(ann); bobS = signIn(bob);
    }

    private Cookie signIn(String username) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Cookie session, String json) throws Exception {
        req.cookie(XSRF, session).header("X-XSRF-TOKEN", "t");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private String createPost(Cookie session, String title, String applyBy) throws Exception {
        String json = "{\"title\":\"" + title + "\",\"body\":\"We need a designer.\"" + (applyBy == null ? "" : ",\"applyBy\":\"" + applyBy + "\"") + "}";
        String body = send(post("/api/posts"), session, json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mvc.perform(get("/api/posts/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void createThenRead() throws Exception {
        String id = createPost(annS, "Film crew", null);
        send(get("/api/posts/" + id), bobS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Film crew"))
                .andExpect(jsonPath("$.author.username").value(ann))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.applyBy").doesNotExist())
                .andExpect(jsonPath("$.mine").value(false));
        send(get("/api/posts/" + id), annS, null).andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void aWindowInThePastIsRefusedAndOneInTheFutureIsPending() throws Exception {
        send(post("/api/posts"), annS, "{\"title\":\"x\",\"body\":\"y\",\"applyBy\":\"" + Instant.now().minus(1, ChronoUnit.DAYS) + "\"}")
                .andExpect(status().isBadRequest());
        String id = createPost(annS, "Later", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        send(get("/api/posts/" + id), bobS, null).andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void emptyOrOversizedPostsAreRefused() throws Exception {
        send(post("/api/posts"), annS, "{\"title\":\" \",\"body\":\"y\"}").andExpect(status().isBadRequest());
        send(post("/api/posts"), annS, "{\"title\":\"x\",\"body\":\"" + "a".repeat(2001) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorDeletes() throws Exception {
        String id = createPost(annS, "Mine", null);
        send(delete("/api/posts/" + id), bobS, null).andExpect(status().isNotFound());
        send(delete("/api/posts/" + id), annS, null).andExpect(status().isNoContent());
        send(get("/api/posts/" + id), annS, null).andExpect(status().isNotFound());
    }

    @Test
    void extendingIsFreeAndShorteningKeepsA24HourGrace() throws Exception {
        String id = createPost(annS, "Window", Instant.now().plus(60, ChronoUnit.DAYS).toString());
        // shorten to one hour from now: takes effect no sooner than 24h from now
        String out = send(patch("/api/posts/" + id + "/window"), annS, "{\"applyBy\":\"" + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn().getResponse().getContentAsString();
        Instant effective = Instant.parse(com.jayway.jsonpath.JsonPath.read(out, "$.applyBy"));
        org.junit.jupiter.api.Assertions.assertTrue(effective.isAfter(Instant.now().plus(23, ChronoUnit.HOURS)));
        // indefinite again
        send(patch("/api/posts/" + id + "/window"), annS, "{}").andExpect(jsonPath("$.applyBy").doesNotExist());
        // only the author edits
        send(patch("/api/posts/" + id + "/window"), bobS, "{}").andExpect(status().isNotFound());
    }
}
