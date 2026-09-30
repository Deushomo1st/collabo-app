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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flagging a quiet member: the nudge, the clock, pleas, the founder cancel, and removal when time runs out. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:removals;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class RemovalProcessTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat, dan;
    Cookie annS, bobS, catS, danS;
    String postId, bobApp, catApp;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}")
                .andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        bobApp = apply(bobS);
        catApp = apply(catS);
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

    private String apply(Cookie who) throws Exception {
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"me\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions decide(String appId, String decision) throws Exception {
        return send(patch("/api/applications/" + appId), annS, "{\"decision\":\"" + decision + "\"}");
    }

    private String form(String json) throws Exception {
        decide(bobApp, "ACCEPT").andExpect(status().isOk());
        String body = send(post("/api/posts/" + postId + "/space"), annS, json).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String space;

    private void formAndJoin() throws Exception {
        space = form("{}");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
    }


    @Autowired com.collabo.backend.service.RemovalService removals;
    @Autowired com.collabo.backend.repository.PleaRepository pleas;

    private String url() { return "/api/spaces/" + space + "/removals"; }

    /** ann owns; bob and cat are both accepted and in the room. */
    private void everyoneIn() throws Exception {
        space = form("{}");
        decide(catApp, "ACCEPT").andExpect(status().isOk());
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());
    }

    private ResultActions flag(Cookie by, String target, String reason) throws Exception {
        return send(post(url()), by, "{\"username\":\"" + target + "\",\"reason\":\"" + reason + "\"}");
    }

    private String flagId(Cookie by, String target) throws Exception {
        String body = flag(by, target, "gone quiet").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions history() throws Exception {
        String room = com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
        return send(get("/api/yarns/threads/" + room + "/yarns"), annS, null);
    }

    private java.time.Instant hoursFromNow(long hours) { return java.time.Instant.now().plusSeconds(3600L * hours); }

    @Test
    void aMemberFlagsAnotherWhoIsNudgedInMySpaceAndTheRoomIsTold() throws Exception {
        everyoneIn();
        flag(bobS, cat, "gone quiet").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.target.username").value(cat)).andExpect(jsonPath("$.initiator.username").value(bob))
                .andExpect(jsonPath("$.reason").value("gone quiet")).andExpect(jsonPath("$.deadline").exists()).andExpect(jsonPath("$.plea").value(nullValue()));
        send(get("/api/yarns/threads?tier=MYSPACE"), catS, null).andExpect(jsonPath("$[0].name").value(bob))
                .andExpect(jsonPath("$[0].lastBody", allOf(containsString("gone quiet"), containsString("72 hours"))));
        history().andExpect(jsonPath("$[0].body", allOf(containsString(bob + " flagged " + cat), containsString("gone quiet"))));
    }

    @Test
    void theFlagNeedsARealMemberARealReasonAndOnlyOneRunsAtATime() throws Exception {
        everyoneIn();
        flag(bobS, bob, "me").andExpect(status().isBadRequest());
        flag(bobS, ann, "founder").andExpect(status().isBadRequest());   // the owner cannot be flagged
        flag(bobS, dan, "not here").andExpect(status().isBadRequest());
        flag(bobS, cat, "  ").andExpect(status().isBadRequest());
        flag(bobS, cat, "x".repeat(301)).andExpect(status().isBadRequest());
        flag(danS, cat, "outsider").andExpect(status().isNotFound());
        flagId(bobS, cat);
        flag(annS, cat, "again").andExpect(status().isBadRequest());
    }

    @Test
    void collaboratorsAreNotFlaggedHereTheirProcessBelongsToTheWeSpace() throws Exception {
        send(put("/api/users/" + dan + "/follow"), annS, null).andExpect(status().isOk());
        send(put("/api/users/" + ann + "/follow"), danS, null).andExpect(status().isOk());
        send(post("/api/posts/" + postId + "/collaborators"), annS, "{\"username\":\"" + dan + "\"}").andExpect(status().isOk());
        send(post("/api/posts/" + postId + "/collaborators/accept"), danS, null).andExpect(status().isOk());
        everyoneIn();
        flag(bobS, dan, "quiet").andExpect(status().isBadRequest());
    }

    @Test
    void theTargetRespondsAndOnlyTheTargetCanAndItEnds() throws Exception {
        everyoneIn();
        String id = flagId(bobS, cat);
        send(post(url() + "/" + id + "/respond"), bobS, null).andExpect(status().isForbidden());
        send(post(url() + "/" + id + "/respond"), catS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RESPONDED"));
        send(post(url() + "/" + id + "/respond"), catS, null).andExpect(status().isBadRequest());
        removals.settleDue(hoursFromNow(1000));
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isOk());   // still in
        flagId(bobS, cat);   // and may be flagged again later
    }

    @Test
    void whenTheTimeRunsOutTheMemberIsRemovedTheRoomAndTheirDmSayWhy() throws Exception {
        everyoneIn();
        flagId(bobS, cat);
        removals.settleDue(java.time.Instant.now());   // not due yet
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isOk());
        removals.settleDue(hoursFromNow(73));
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isNotFound());
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(2)));
        history().andExpect(jsonPath("$[0].body", allOf(containsString(bob + " removed " + cat), containsString("gone quiet"))));
        send(get("/api/yarns/threads?tier=WORKSPACE"), catS, null).andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/yarns/threads?tier=MYSPACE"), catS, null).andExpect(jsonPath("$[0].lastBody", allOf(containsString("removed"), containsString("gone quiet"))));
        send(get(url()), annS, null).andExpect(jsonPath("$[0].state").value("COMPLETED"));
    }

    @Test
    void aPleaBuysTwelveHoursOneAtATimeAndNotForTheTargetOrTheAccuser() throws Exception {
        everyoneIn();
        String id = flagId(bobS, cat);
        java.time.Instant before = java.time.Instant.parse(com.jayway.jsonpath.JsonPath.read(send(get(url()), annS, null).andReturn().getResponse().getContentAsString(), "$[0].deadline"));
        send(post(url() + "/" + id + "/plea"), catS, null).andExpect(status().isBadRequest());   // not for yourself
        send(post(url() + "/" + id + "/plea"), bobS, null).andExpect(status().isBadRequest());   // not by the one who flagged
        String body = send(post(url() + "/" + id + "/plea"), annS, null).andExpect(status().isOk()).andExpect(jsonPath("$.plea.by.username").value(ann))
                .andReturn().getResponse().getContentAsString();
        java.time.Instant after = java.time.Instant.parse(com.jayway.jsonpath.JsonPath.read(body, "$.deadline"));
        org.junit.jupiter.api.Assertions.assertEquals(12 * 3600, after.getEpochSecond() - before.getEpochSecond());
        send(post(url() + "/" + id + "/plea"), annS, null).andExpect(status().isBadRequest());   // one active at a time
        history().andExpect(jsonPath("$[0].body", containsString("plea")));
        removals.settleDue(hoursFromNow(73));   // 72 + 12 hours now, so not yet
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isOk());
        removals.settleDue(hoursFromNow(85));
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isNotFound());
    }

    @Test
    void eachPersonGetsOnePleaAWeekPerSpaceAndTheFounderCanSwitchPleasOff() throws Exception {
        everyoneIn();
        String id = flagId(bobS, cat);
        java.util.UUID pleader = users.findByUsername(ann).orElseThrow().getId();
        java.util.UUID process = java.util.UUID.fromString(id), room = java.util.UUID.fromString(space);
        java.time.Instant twoDaysAgo = java.time.Instant.now().minusSeconds(2 * 86400);
        pleas.save(new com.collabo.backend.entity.Plea(process, room, pleader, twoDaysAgo, twoDaysAgo.plusSeconds(12 * 3600)));
        send(post(url() + "/" + id + "/plea"), annS, null).andExpect(status().isBadRequest());   // used this week
        pleas.deleteAll();
        java.time.Instant eightDaysAgo = java.time.Instant.now().minusSeconds(8 * 86400);
        pleas.save(new com.collabo.backend.entity.Plea(process, room, pleader, eightDaysAgo, eightDaysAgo.plusSeconds(12 * 3600)));
        send(patch("/api/spaces/" + space + "/settings"), annS, "{\"pleasEnabled\":false}").andExpect(status().isOk());
        send(post(url() + "/" + id + "/plea"), annS, null).andExpect(status().isBadRequest());   // switched off
        send(patch("/api/spaces/" + space + "/settings"), annS, "{\"pleasEnabled\":true}").andExpect(status().isOk());
        send(post(url() + "/" + id + "/plea"), annS, null).andExpect(status().isOk());
    }

    @Test
    void onlyTheFounderCancelsAndACancelledProcessNeverRemoves() throws Exception {
        everyoneIn();
        String id = flagId(bobS, cat);
        send(post(url() + "/" + id + "/cancel"), bobS, null).andExpect(status().isForbidden());
        send(post(url() + "/" + id + "/cancel"), catS, null).andExpect(status().isForbidden());
        send(post(url() + "/" + id + "/cancel"), annS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
        send(post(url() + "/" + id + "/cancel"), annS, null).andExpect(status().isBadRequest());
        removals.settleDue(hoursFromNow(1000));
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isOk());
        history().andExpect(jsonPath("$[0].body", containsString("cancelled")));
    }
}
