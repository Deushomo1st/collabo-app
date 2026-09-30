package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.RemovalService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Appeals: the removed person reports a termination once; a moderator sees only the room around it and decides the badge. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:appeals;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class AppealTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RemovalService removals;

    String ann, bob, dan, mod;
    Cookie annS, bobS, danS, modS;
    String postId, space, record;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag; mod = "mod" + tag;
        annS = signIn(ann, Role.USER); bobS = signIn(bob, Role.USER); danS = signIn(dan, Role.USER); modS = signIn(mod, Role.MODERATOR);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String app = send(post("/api/posts/" + postId + "/applications"), bobS, "{\"statement\":\"me\"}").andReturn().getResponse().getContentAsString();
        send(patch("/api/applications/" + com.jayway.jsonpath.JsonPath.read(app, "$.id")), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String sp = send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        space = com.jayway.jsonpath.JsonPath.read(sp, "$.id");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
    }

    private Cookie signIn(String username, Role role) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(role);
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

    /** Two milestones done, then bob is flagged and removed; sets {@code record}. */
    private void removeBobWithABadge(int milestones) throws Exception {
        for (int i = 0; i < milestones; i++) {
            String body = send(post("/api/spaces/" + space + "/milestones"), annS, "{\"title\":\"M" + i + "\"}").andReturn().getResponse().getContentAsString();
            send(post("/api/spaces/" + space + "/milestones/" + com.jayway.jsonpath.JsonPath.read(body, "$.id") + "/fulfil"), annS, "{\"note\":\"done\"}").andExpect(status().isOk());
        }
        send(post("/api/spaces/" + space + "/removals"), annS, "{\"username\":\"" + bob + "\",\"reason\":\"gone quiet\"}").andExpect(status().isOk());
        removals.settleDue(Instant.now().plusSeconds(3600L * 1000));
        record = com.jayway.jsonpath.JsonPath.read(send(get("/api/users/" + bob + "/removals"), bobS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    private String appeal() throws Exception {
        String body = send(post("/api/removals/" + record + "/appeal"), bobS, "{\"note\":\"I was on leave.\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void theRemovedPersonAppealsOnceAndOnlyTheyCan() throws Exception {
        removeBobWithABadge(2);
        send(post("/api/removals/" + record + "/appeal"), annS, "{\"note\":\"x\"}").andExpect(status().isForbidden());
        send(post("/api/removals/" + record + "/appeal"), bobS, "{\"note\":\"  \"}").andExpect(status().isBadRequest());
        appeal();
        send(post("/api/removals/" + record + "/appeal"), bobS, "{\"note\":\"again\"}").andExpect(status().isBadRequest());
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].appeal").value("OPEN"));
    }

    @Test
    void thereIsNothingToAppealWhenNoBadgeStuck() throws Exception {
        removeBobWithABadge(0);
        send(post("/api/removals/" + record + "/appeal"), bobS, "{\"note\":\"x\"}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyModeratorsAndAdminsSeeTheQueue() throws Exception {
        removeBobWithABadge(2);
        appeal();
        send(get("/api/moderation/appeals"), danS, null).andExpect(status().isForbidden());
        send(get("/api/moderation/appeals"), bobS, null).andExpect(status().isForbidden());
        send(get("/api/moderation/appeals"), modS, null).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.record.removed.username=='" + bob + "')]", hasSize(1)));
    }

    @Test
    void theModeratorSeesTheRoomAroundTheRemovalAndNotTheDms() throws Exception {
        removeBobWithABadge(2);
        String id = appeal();
        send(get("/api/moderation/appeals/" + id), modS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("I was on leave.")).andExpect(jsonPath("$.record.reason").value("gone quiet"))
                .andExpect(jsonPath("$.history[*].body", hasItem(containsString("removed " + bob))))
                .andExpect(jsonPath("$.history[*].body", not(hasItem(containsString("You were removed")))));   // the DM note is not in the room
        send(get("/api/moderation/appeals/" + id), danS, null).andExpect(status().isForbidden());
        send(get("/api/moderation/appeals/" + UUID.randomUUID()), modS, null).andExpect(status().isNotFound());
    }

    @Test
    void dropsTakesTheBadgeAwayAndTheRemovalStands() throws Exception {
        removeBobWithABadge(2);
        String id = appeal();
        send(post("/api/moderation/appeals/" + id + "/decide"), modS, "{\"outcome\":\"nonsense\"}").andExpect(status().isBadRequest());
        send(post("/api/moderation/appeals/" + id + "/decide"), danS, "{\"outcome\":\"DROPS\"}").andExpect(status().isForbidden());
        send(post("/api/moderation/appeals/" + id + "/decide"), modS, "{\"outcome\":\"DROPS\"}").andExpect(status().isOk());
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].badge").value(false)).andExpect(jsonPath("$[0].appeal").value("DROPS"));
        send(get("/api/moderation/appeals"), modS, null).andExpect(jsonPath("$[?(@.record.removed.username=='" + bob + "')]", hasSize(0)));
        send(post("/api/moderation/appeals/" + id + "/decide"), modS, "{\"outcome\":\"STICKS\"}").andExpect(status().isBadRequest());   // decided once
        send(get("/api/spaces/" + space), bobS, null).andExpect(status().isNotFound());                                                   // still removed
        send(get("/api/notifications"), bobS, null).andExpect(jsonPath("$[0].title", containsString("appeal")));
    }

    @Test
    void sticksLeavesTheBadge() throws Exception {
        removeBobWithABadge(2);
        String id = appeal();
        send(post("/api/moderation/appeals/" + id + "/decide"), modS, "{\"outcome\":\"STICKS\"}").andExpect(status().isOk());
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].badge").value(true)).andExpect(jsonPath("$[0].appeal").value("STICKS"));
    }
}
