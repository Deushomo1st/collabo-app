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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Gaze (everyone, newest first) and Shared Gaze (your network only). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gaze;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class GazeFeedTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder encoder;

    String tag, ann, bob, cat, dan;
    User annU;
    Cookie annS, bobS, catS, danS;

    @BeforeEach
    void accounts() throws Exception {
        tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan);
        annU = users.findByUsername(ann).orElseThrow();
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

    private ResultActions write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Cookie s, String json) throws Exception {
        req.cookie(XSRF, s).header("X-XSRF-TOKEN", "t");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private void newPost(Cookie s, String title) throws Exception {
        write(post("/api/posts"), s, "{\"title\":\"" + title + "\",\"body\":\"b\"}").andExpect(status().isOk());
        Thread.sleep(5);   // distinct createdAt so the order is certain
    }

    private ResultActions feed(Cookie s, String query) throws Exception {
        return mvc.perform(get("/api/gaze" + query).cookie(s));
    }

    private String uid(String username) { return users.findByUsername(username).orElseThrow().getId().toString(); }

    @Test
    void signedOutIsRefused() throws Exception {
        mvc.perform(get("/api/gaze")).andExpect(status().isUnauthorized());
    }

    @Test
    void gazeIsNewestFirstAndPagesWithACursor() throws Exception {
        newPost(annS, "A" + tag); newPost(catS, "B" + tag); newPost(annS, "C" + tag);
        String page1 = feed(bobS, "?limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].title").value("C" + tag))
                .andExpect(jsonPath("$.items[1].title").value("B" + tag))
                .andExpect(jsonPath("$.next").exists())
                .andReturn().getResponse().getContentAsString();
        String next = com.jayway.jsonpath.JsonPath.read(page1, "$.next");
        feed(bobS, "?limit=2&before=" + next)
                .andExpect(jsonPath("$.items[0].title").value("A" + tag))
                .andExpect(jsonPath("$.next").doesNotExist());
    }

    @Test
    void pendingOnlyHidesClosedPosts() throws Exception {
        posts.save(new Post(annU.getId(), "Old" + tag, "b", Instant.now().minus(1, ChronoUnit.DAYS)));
        newPost(annS, "New" + tag);
        feed(bobS, "?pending=true&limit=50").andExpect(jsonPath("$.items[?(@.title=='Old" + tag + "')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.title=='New" + tag + "')]", hasSize(1)));
        feed(bobS, "?limit=50").andExpect(jsonPath("$.items[?(@.title=='Old" + tag + "')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.status=='closed')]").exists());
    }

    @Test
    void blocksHideEachOthersPostsQuietly() throws Exception {
        newPost(annS, "Hidden" + tag);
        write(put("/api/yarns/blocks/" + uid(bob)), annS, null).andExpect(status().is2xxSuccessful());   // ann blocks bob
        feed(bobS, "?limit=50").andExpect(jsonPath("$.items[?(@.title=='Hidden" + tag + "')]", hasSize(0)));
        feed(annS, "?limit=50").andExpect(status().isOk());
    }

    @Test
    void sharedGazeIsOnlyYourNetwork() throws Exception {
        write(put("/api/users/" + ann + "/follow"), bobS, null).andExpect(status().isOk());   // bob follows ann
        write(put("/api/users/" + bob + "/follow"), catS, null).andExpect(status().isOk());   // cat follows bob
        newPost(annS, "FromFollowed" + tag); newPost(catS, "FromFollower" + tag); newPost(danS, "Stranger" + tag);
        feed(bobS, "?feed=shared&limit=50")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.title=='Stranger" + tag + "')]", hasSize(0)));
        feed(danS, "?feed=shared").andExpect(jsonPath("$.items", hasSize(0)));   // nobody in the network: empty, not an error
    }

    @Test
    void yourOwnPostShowsInYourOwnGaze() throws Exception {
        newPost(annS, "Mine" + tag);
        feed(annS, "?limit=50").andExpect(jsonPath("$.items[?(@.title=='Mine" + tag + "')]", hasSize(1)));
    }

    @Test
    void anUnknownFeedIsRefused() throws Exception {
        feed(bobS, "?feed=nope").andExpect(status().isBadRequest());
    }
}
