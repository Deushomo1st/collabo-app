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

/** Appeals: the removed person reports a termination once; it becomes an investigation and the admin decides the badge. */
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

    String ann, bob, dan;
    Cookie annS, bobS, danS;
    String postId, space, record;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag;
        annS = signIn(ann, Role.USER); bobS = signIn(bob, Role.USER); danS = signIn(dan, Role.USER);
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

    /** The investigation this appeal opened, as the admin's queue shows it. */
    private String investigationId(String appealId) throws Exception {
        String list = admin(get("/api/admin/investigations"), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(list, "$[?(@.kind=='APPEAL' && @.reporter=='" + bob + "')].id");
        return ids.get(0);
    }

    private ResultActions admin(MockHttpServletRequestBuilder req, String json) throws Exception {
        req.header("X-Admin-Key", "test-admin-key");
        if (json != null) req.cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content(json);
        return mvc.perform(req);
    }

    @Test
    void anAppealOpensAnInvestigationForTheAdminAndNobodyElse() throws Exception {
        removeBobWithABadge(2);
        String id = appeal();
        String inv = investigationId(id);
        admin(get("/api/admin/investigations"), null).andExpect(jsonPath("$[?(@.id=='" + inv + "')].status", contains("OPEN")))
                .andExpect(jsonPath("$[?(@.id=='" + inv + "')].tier", contains("WORKSPACE")));
        mvc.perform(get("/api/admin/investigations")).andExpect(status().isForbidden());   // no key, no queue
        send(get("/api/admin/investigations"), danS, null).andExpect(status().isForbidden());   // a signed-in user is not the admin
    }

    @Test
    void theAdminSeesTheRoomAroundTheRemovalAndNotTheDms() throws Exception {
        removeBobWithABadge(2);
        String inv = investigationId(appeal());
        admin(get("/api/admin/investigations/" + inv), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.note").value("I was on leave.")).andExpect(jsonPath("$.appeal.record.reason").value("gone quiet"))
                .andExpect(jsonPath("$.appeal.history[*].body", hasItem(containsString("removed " + bob))))
                .andExpect(jsonPath("$.appeal.history[*].body", not(hasItem(containsString("You were removed")))));   // the DM note is not in the room
        admin(get("/api/admin/investigations/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
    }

    @Test
    void dropsTakesTheBadgeAwayAndTheRemovalStands() throws Exception {
        removeBobWithABadge(2);
        String inv = investigationId(appeal());
        admin(post("/api/admin/investigations/" + inv + "/decide"), "{\"outcome\":\"nonsense\"}").andExpect(status().isBadRequest());
        admin(post("/api/admin/investigations/" + inv + "/decide"), "{\"outcome\":\"DROPS\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].badge").value(false)).andExpect(jsonPath("$[0].appeal").value("DROPS"));
        admin(get("/api/admin/investigations"), null).andExpect(jsonPath("$[?(@.id=='" + inv + "')]", hasSize(0)));   // left the active queue
        admin(get("/api/admin/investigations?status=closed"), null).andExpect(jsonPath("$[?(@.id=='" + inv + "')]", hasSize(1)));
        admin(post("/api/admin/investigations/" + inv + "/decide"), "{\"outcome\":\"STICKS\"}").andExpect(status().isBadRequest());   // decided once
        send(get("/api/spaces/" + space), bobS, null).andExpect(status().isNotFound());                                                     // still removed
        send(get("/api/notifications"), bobS, null).andExpect(jsonPath("$[0].title", containsString("appeal")));
    }

    @Test
    void sticksLeavesTheBadge() throws Exception {
        removeBobWithABadge(2);
        String inv = investigationId(appeal());
        admin(post("/api/admin/investigations/" + inv + "/decide"), "{\"outcome\":\"STICKS\"}").andExpect(status().isOk());
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].badge").value(true)).andExpect(jsonPath("$[0].appeal").value("STICKS"));
    }

    @Test
    void anAppealsModeratorSeesOnlyTheWindowAndMayRecommendButTheAdminDecides() throws Exception {
        removeBobWithABadge(2);
        String inv = investigationId(appeal());
        String email = "mo" + UUID.randomUUID().toString().substring(0, 8) + "@t.dev";
        String modId = com.jayway.jsonpath.JsonPath.read(admin(post("/api/admin/moderators"), "{\"name\":\"Mo\",\"email\":\"" + email + "\",\"password\":\"long-enough-pw\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{\"moderatorId\":\"" + modId + "\"}").andExpect(status().isOk());
        send(get("/api/notifications"), bobS, null).andExpect(jsonPath("$[0].title").value("A moderator was introduced"));
        Cookie modS = mvc.perform(post("/api/moderator/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + email + "\",\"password\":\"long-enough-pw\"}")).andReturn().getResponse().getCookie("COLLABO_SESSION");
        send(get("/api/moderator/investigations/" + inv), modS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.yarns[*].body", hasItem(containsString("removed " + bob))))
                .andExpect(jsonPath("$.yarns[*].body", not(hasItem(containsString("You were removed")))));
        send(post("/api/moderator/investigations/" + inv + "/findings"), modS, "{\"text\":\"Bob was on leave.\",\"recommendation\":\"nonsense\"}").andExpect(status().isBadRequest());
        send(post("/api/moderator/investigations/" + inv + "/findings"), modS, "{\"text\":\"Bob was on leave.\",\"recommendation\":\"DROPS\"}").andExpect(status().isCreated());
        admin(get("/api/admin/investigations/" + inv), null).andExpect(jsonPath("$.findings[0].recommendation").value("DROPS"));
        admin(post("/api/admin/investigations/" + inv + "/close"), null).andExpect(status().isBadRequest());   // an appeal closes by deciding
        send(get("/api/users/" + bob + "/removals"), bobS, null).andExpect(jsonPath("$[0].badge").value(true));   // a recommendation changes nothing
    }

    @Test
    void theOldUserModeratorEndpointsAreGone() throws Exception {
        send(get("/api/moderation/appeals"), annS, null).andExpect(status().isNotFound());
    }
}
