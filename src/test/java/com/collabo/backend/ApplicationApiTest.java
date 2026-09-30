package com.collabo.backend;

import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.PostRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Applying to a post: the statement, the window, withdrawing, and what stays private. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:applications;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ApplicationApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat;
    Cookie annS, bobS, catS;
    String postId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}")
                .andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
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

    private ResultActions send(MockHttpServletRequestBuilder req, Cookie session, String json) throws Exception {
        req.cookie(XSRF, session).header("X-XSRF-TOKEN", "t");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private String uid(String username) { return users.findByUsername(username).orElseThrow().getId().toString(); }

    private ResultActions apply(Cookie who, String pid, String statement) throws Exception {
        return send(post("/api/posts/" + pid + "/applications"), who, "{\"statement\":\"" + statement + "\"}");
    }

    private String applyOk(Cookie who, String statement) throws Exception {
        String body = apply(who, postId, statement).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mvc.perform(get("/api/applications/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void applyShowsOnMineAndOnThePostForTheRightPeopleOnly() throws Exception {
        apply(bobS, postId, "I design calm apps.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUBMITTED"))
                .andExpect(jsonPath("$.statement").value("I design calm apps.")).andExpect(jsonPath("$.postTitle").value("Idea"));
        send(get("/api/posts/" + postId), bobS, null).andExpect(jsonPath("$.applied").value("SUBMITTED")).andExpect(jsonPath("$.applicants").doesNotExist());
        send(get("/api/posts/" + postId), annS, null).andExpect(jsonPath("$.applicants").value(1)).andExpect(jsonPath("$.applied").doesNotExist());
        send(get("/api/posts/" + postId), catS, null).andExpect(jsonPath("$.applicants").doesNotExist()).andExpect(jsonPath("$.applied").doesNotExist());
        send(get("/api/applications/mine"), bobS, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].postId").value(postId));
        send(get("/api/applications/mine"), catS, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void feedItemsCarryTheSameFields() throws Exception {
        applyOk(bobS, "me");
        send(get("/api/gaze"), bobS, null).andExpect(jsonPath("$.items[0].applied").value("SUBMITTED"));
        send(get("/api/users/" + ann + "/posts"), annS, null).andExpect(jsonPath("$.items[0].applicants").value(1));
    }

    @Test
    void notYourOwnNotTwiceNotBlockedNotClosed() throws Exception {
        apply(annS, postId, "me").andExpect(status().isBadRequest());
        applyOk(bobS, "me");
        apply(bobS, postId, "again").andExpect(status().isBadRequest());
        send(put("/api/yarns/blocks/" + uid(ann)), catS, null).andExpect(status().is2xxSuccessful());
        apply(catS, postId, "hi").andExpect(status().isNotFound());

        User annU = users.findByUsername(ann).orElseThrow();
        Post old = posts.save(new Post(annU.getId(), "Old", "b", Instant.now().minus(1, ChronoUnit.DAYS)));
        apply(bobS, old.getId().toString(), "too late").andExpect(status().isBadRequest());
    }

    @Test
    void statementNeedsWordsButNotTooMany() throws Exception {
        apply(bobS, postId, "   ").andExpect(status().isBadRequest());
        apply(bobS, postId, "word ".repeat(151).trim()).andExpect(status().isBadRequest());
        apply(bobS, postId, "word ".repeat(150).trim()).andExpect(status().isOk());
    }

    @Test
    void withdrawLeavesTheCountAndAllowsReapplying() throws Exception {
        String id = applyOk(bobS, "first try");
        send(post("/api/applications/" + id + "/withdraw"), catS, null).andExpect(status().isNotFound());
        send(post("/api/applications/" + id + "/withdraw"), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WITHDRAWN"));
        send(get("/api/posts/" + postId), annS, null).andExpect(jsonPath("$.applicants").value(0));
        send(get("/api/posts/" + postId), bobS, null).andExpect(jsonPath("$.applied").doesNotExist());
        apply(bobS, postId, "second try").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUBMITTED"))
                .andExpect(jsonPath("$.statement").value("second try"));
        send(get("/api/applications/mine"), bobS, null).andExpect(jsonPath("$", hasSize(1)));
    }
}
