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

/** Removal records: written when a removal completes, badge only after real work, premium gate, addresses. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:removalrecords;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class RemovalRecordTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RemovalService removals;

    String ann, bob, dan;
    Cookie annS, bobS, danS;
    String postId, bobApp, space;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String app = send(post("/api/posts/" + postId + "/applications"), bobS, "{\"statement\":\"me\"}").andReturn().getResponse().getContentAsString();
        bobApp = com.jayway.jsonpath.JsonPath.read(app, "$.id");
        send(patch("/api/applications/" + bobApp), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String sp = send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        space = com.jayway.jsonpath.JsonPath.read(sp, "$.id");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
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

    private void fulfilMilestones(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            String body = send(post("/api/spaces/" + space + "/milestones"), annS, "{\"title\":\"M" + i + "\"}").andReturn().getResponse().getContentAsString();
            String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
            send(post("/api/spaces/" + space + "/milestones/" + id + "/fulfil"), annS, "{\"note\":\"done\"}").andExpect(status().isOk());
        }
    }

    /** Ann flags bob and the clock runs out. */
    private void removeBob() throws Exception {
        send(post("/api/spaces/" + space + "/removals"), annS, "{\"username\":\"" + bob + "\",\"reason\":\"gone quiet\"}").andExpect(status().isOk());
        removals.settleDue(Instant.now().plusSeconds(3600L * 1000));
    }

    private ResultActions recordsOf(String username, Cookie who) throws Exception { return send(get("/api/users/" + username + "/removals"), who, null); }

    private String recordId() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(recordsOf(bob, bobS).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    private void makePremium(String username) {
        User u = users.findByUsername(username).orElseThrow();
        u.setPremium(true);
        users.save(u);
    }

    @Test
    void aCompletedRemovalLeavesARecordAndTheRemovedPersonSeesIt() throws Exception {
        removeBob();
        recordsOf(bob, bobS).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reason").value("gone quiet")).andExpect(jsonPath("$[0].removedBy.username").value(ann))
                .andExpect(jsonPath("$[0].removed.username").value(bob)).andExpect(jsonPath("$[0].spaceName").isNotEmpty())
                .andExpect(jsonPath("$[0].addresses", hasSize(0)));
    }

    @Test
    void aFlagThatIsAnsweredLeavesNoRecord() throws Exception {
        String body = send(post("/api/spaces/" + space + "/removals"), annS, "{\"username\":\"" + bob + "\",\"reason\":\"quiet\"}").andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(post("/api/spaces/" + space + "/removals/" + id + "/respond"), bobS, null).andExpect(status().isOk());
        recordsOf(bob, bobS).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theBadgeSticksOnlyWhenTheSpaceHadMoreThanOneFulfilledMilestone() throws Exception {
        fulfilMilestones(1);
        removeBob();
        recordsOf(bob, bobS).andExpect(jsonPath("$[0].badge").value(false));
    }

    @Test
    void withRealWorkBehindItTheBadgeStays() throws Exception {
        fulfilMilestones(2);
        removeBob();
        recordsOf(bob, bobS).andExpect(jsonPath("$[0].badge").value(true));
    }

    @Test
    void otherPeoplesRecordsNeedPremium() throws Exception {
        removeBob();
        recordsOf(bob, danS).andExpect(status().isForbidden());
        makePremium(dan);
        recordsOf(bob, danS).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        recordsOf("nobody-here", bobS).andExpect(status().isNotFound());
    }

    @Test
    void thePartiesAddressEachOtherAndEachIsNotified() throws Exception {
        removeBob();
        String id = recordId();
        send(post("/api/removals/" + id + "/addresses"), bobS, "{\"body\":\"I was travelling with no signal.\"}").andExpect(status().isOk());
        send(get("/api/notifications?filter=activity"), annS, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].bucket").value("ACTIVITY"));
        send(post("/api/removals/" + id + "/addresses"), annS, "{\"body\":\"Two weeks is long.\"}").andExpect(status().isOk());
        send(get("/api/notifications?filter=activity"), bobS, null).andExpect(jsonPath("$", hasSize(1)));
        recordsOf(bob, bobS).andExpect(jsonPath("$[0].addresses", hasSize(2))).andExpect(jsonPath("$[0].addresses[0].by.username").value(bob))
                .andExpect(jsonPath("$[0].addresses[1].by.username").value(ann));
    }

    @Test
    void onlyThePartiesAddressAndTheBodyIsChecked() throws Exception {
        removeBob();
        String id = recordId();
        send(post("/api/removals/" + id + "/addresses"), danS, "{\"body\":\"hot take\"}").andExpect(status().isForbidden());
        send(post("/api/removals/" + id + "/addresses"), bobS, "{\"body\":\"  \"}").andExpect(status().isBadRequest());
        send(post("/api/removals/" + id + "/addresses"), bobS, "{\"body\":\"" + "x".repeat(1001) + "\"}").andExpect(status().isBadRequest());
        send(post("/api/removals/" + UUID.randomUUID() + "/addresses"), bobS, "{\"body\":\"hi\"}").andExpect(status().isNotFound());
    }

    @Test
    void anAdminGrantsPremium() throws Exception {
        User dan2 = users.findByUsername(dan).orElseThrow();
        mvc.perform(patch("/api/admin/users/" + dan2.getId() + "/premium").header("X-Admin-Key", "test-admin-key")
                .contentType("application/json").content("{\"premium\":true}")).andExpect(status().isOk());
        removeBob();
        recordsOf(bob, danS).andExpect(status().isOk());
    }
}
