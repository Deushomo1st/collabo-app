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

/** Milestones: who logs them, who is credited, opting out. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:milestones;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class MilestoneApiTest {

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


    private String url() { return "/api/spaces/" + space + "/milestones"; }

    private String log(Cookie who, String title) throws Exception {
        String body = send(post(url()), who, "{\"title\":\"" + title + "\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions fulfil(Cookie who, String id, String note) throws Exception {
        return send(post(url() + "/" + id + "/fulfil"), who, "{\"note\":\"" + note + "\"}");
    }

    @Test
    void theOwnerLogsAndFulfilsAndEveryActiveMemberIsCreditedAndTheRoomIsTold() throws Exception {
        formAndJoin();
        String id = log(annS, "Ship v1");
        send(get(url()), bobS, null).andExpect(jsonPath("$[0].fulfilled").value(false)).andExpect(jsonPath("$[0].credited", hasSize(0)));
        fulfil(annS, id, "Live!").andExpect(status().isOk()).andExpect(jsonPath("$.fulfilled").value(true)).andExpect(jsonPath("$.note").value("Live!"))
                .andExpect(jsonPath("$.credited[*].username", containsInAnyOrder(ann, bob)));
        send(get("/api/users/" + bob + "/credentials"), bobS, null)
                .andExpect(jsonPath("$.entries[?(@.kind=='MILESTONE_CREDITED')].title", contains("Ship v1")));
        send(get("/api/users/" + ann + "/credentials"), annS, null)
                .andExpect(jsonPath("$.entries[?(@.kind=='MILESTONE_CREDITED')]", hasSize(1)));
        String thread = com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null)
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        send(get("/api/yarns/threads/" + thread + "/yarns"), bobS, null).andExpect(jsonPath("$[0].body", containsString("Ship v1")));
    }

    @Test
    void membersNeedThePermissionOutsidersAreNotFoundAndAcceptedApplicantsOnlyRead() throws Exception {
        formAndJoin();
        decide(catApp, "ACCEPT").andExpect(status().isOk());
        send(post(url()), bobS, "{\"title\":\"x\"}").andExpect(status().isForbidden());
        send(post(url()), catS, "{\"title\":\"x\"}").andExpect(status().isForbidden());   // accepted later, not in the room
        send(post(url()), danS, "{\"title\":\"x\"}").andExpect(status().isNotFound());
        send(get(url()), danS, null).andExpect(status().isNotFound());
        send(patch("/api/spaces/" + space + "/members/" + bob), annS, "{\"permissions\":[\"LOG_MILESTONES\"]}").andExpect(status().isOk());
        String id = log(bobS, "Bob's goal");
        fulfil(catS, id, "").andExpect(status().isForbidden());
        fulfil(bobS, id, "").andExpect(status().isOk());
    }

    @Test
    void titlesAndNotesAreValidatedAndAMilestoneIsFulfilledOnceAndOnlyUnfulfilledOnesGo() throws Exception {
        formAndJoin();
        send(post(url()), annS, "{\"title\":\"  \"}").andExpect(status().isBadRequest());
        send(post(url()), annS, "{\"title\":\"" + "x".repeat(121) + "\"}").andExpect(status().isBadRequest());
        String id = log(annS, "Goal");
        fulfil(annS, id, "n".repeat(501)).andExpect(status().isBadRequest());
        fulfil(annS, id, "done").andExpect(status().isOk());
        fulfil(annS, id, "again").andExpect(status().isBadRequest());
        send(delete(url() + "/" + id), annS, null).andExpect(status().isBadRequest());
        String other = log(annS, "Never mind");
        send(delete(url() + "/" + other), bobS, null).andExpect(status().isForbidden());
        send(delete(url() + "/" + other), annS, null).andExpect(status().isOk());
        send(get(url()), annS, null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void peopleWhoJoinLaterAreNotCreditedForEarlierMilestones() throws Exception {
        space = form("{}");
        String id = log(annS, "Early");
        fulfil(annS, id, "").andExpect(jsonPath("$.credited", hasSize(1)));
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
        send(get(url()), bobS, null).andExpect(jsonPath("$[0].credited[*].username", contains(ann)));
        send(get("/api/users/" + bob + "/credentials"), bobS, null).andExpect(jsonPath("$.entries[?(@.kind=='MILESTONE_CREDITED')]", hasSize(0)));
    }

    @Test
    void aCreditedMemberCanOptOutWhichRemovesTheCredentialToo() throws Exception {
        formAndJoin();
        String id = log(annS, "Ship v1");
        send(post(url() + "/" + id + "/opt-out"), bobS, null).andExpect(status().isBadRequest());   // not fulfilled, nothing to opt out of
        fulfil(annS, id, "").andExpect(status().isOk());
        send(post(url() + "/" + id + "/opt-out"), bobS, null).andExpect(status().isOk());
        send(get(url()), annS, null).andExpect(jsonPath("$[0].credited[*].username", contains(ann)));
        send(get("/api/users/" + bob + "/credentials"), bobS, null).andExpect(jsonPath("$.entries[?(@.kind=='MILESTONE_CREDITED')]", hasSize(0)));
        send(post(url() + "/" + id + "/opt-out"), bobS, null).andExpect(status().isBadRequest());   // already off it
        send(get("/api/users/" + ann + "/credentials"), annS, null).andExpect(jsonPath("$.entries[?(@.kind=='MILESTONE_CREDITED')]", hasSize(1)));
    }
}
