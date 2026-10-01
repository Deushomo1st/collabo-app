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

/** Agree and disagree on applications, and acceptance by consensus of the founder and the active collaborators. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:appconsensus;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ApplicationConsensusTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat, dan, eve;
    Cookie annS, bobS, catS, danS, eveS;
    String postId, url, appId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag; eve = "eve" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan); eveS = signIn(eve);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        url = "/api/posts/" + postId + "/collaborators";
        follow(annS, bob); follow(bobS, ann);
        follow(annS, cat); follow(catS, ann);
        for (String who : new String[]{bob, cat}) send(post(url), annS, "{\"username\":\"" + who + "\"}").andExpect(status().isOk());
        send(post(url + "/accept"), bobS, null).andExpect(status().isOk());
        send(post(url + "/accept"), catS, null).andExpect(status().isOk());
        String res = send(post("/api/posts/" + postId + "/applications"), danS, "{\"statement\":\"I design.\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        appId = com.jayway.jsonpath.JsonPath.read(res, "$.id");
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

    private void follow(Cookie who, String target) throws Exception { send(put("/api/users/" + target + "/follow"), who, null).andExpect(status().isOk()); }

    private ResultActions react(Cookie who, String reaction) throws Exception {
        return send(put("/api/applications/" + appId + "/reaction"), who, "{\"reaction\":\"" + reaction + "\"}");
    }

    private ResultActions decide(Cookie who, String decision) throws Exception {
        return send(patch("/api/applications/" + appId), who, "{\"decision\":\"" + decision + "\"}");
    }

    private ResultActions stack(Cookie who) throws Exception { return send(get("/api/posts/" + postId + "/applications"), who, null); }

    @Test
    void anyReviewerCanReactAndChangeOrTakeItBackButOutsidersCannot() throws Exception {
        react(bobS, "AGREE").andExpect(status().isOk()).andExpect(jsonPath("$.agree[0].username").value(bob)).andExpect(jsonPath("$.myReaction").value("AGREE"));
        react(bobS, "DISAGREE").andExpect(jsonPath("$.agree", hasSize(0))).andExpect(jsonPath("$.disagree[0].username").value(bob));
        stack(annS).andExpect(jsonPath("$[0].disagree[0].username").value(bob)).andExpect(jsonPath("$[0].myReaction").doesNotExist());
        react(bobS, "NONE").andExpect(jsonPath("$.disagree", hasSize(0))).andExpect(jsonPath("$.myReaction").doesNotExist());
        react(bobS, "MAYBE").andExpect(status().isBadRequest());
        react(danS, "AGREE").andExpect(status().isNotFound());   // the applicant is no reviewer
        react(eveS, "AGREE").andExpect(status().isNotFound());
    }

    @Test
    void acceptingWithNoOneAgainstGoesThroughAndCountsAsYourAgree() throws Exception {
        decide(annS, "ACCEPT").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACCEPTED")).andExpect(jsonPath("$.agree[0].username").value(ann));
    }

    @Test
    void aDisagreementBlocksAcceptanceUntilAMajorityAgrees() throws Exception {
        react(bobS, "DISAGREE").andExpect(status().isOk());
        decide(annS, "ACCEPT").andExpect(status().isBadRequest());   // ann agrees, bob does not: 1 of 3
        stack(annS).andExpect(jsonPath("$[0].state").value("SUBMITTED"));
        react(catS, "AGREE").andExpect(status().isOk());
        decide(annS, "ACCEPT").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACCEPTED"));   // ann and cat: a majority of 3
    }

    @Test
    void anAcceptanceThatLosesItsMajorityGoesBackToTheShortlist() throws Exception {
        react(catS, "AGREE").andExpect(status().isOk());
        decide(annS, "ACCEPT").andExpect(status().isOk());
        react(bobS, "DISAGREE").andExpect(jsonPath("$.state").value("ACCEPTED"));   // 2 of 3 still agree
        react(catS, "DISAGREE").andExpect(jsonPath("$.state").value("SHORTLISTED"));   // 1 of 3
        decide(annS, "SHORTLIST").andExpect(status().isOk());   // shortlisting and declining stay a single reviewer's call
        decide(annS, "DECLINE").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DECLINED"));
        react(bobS, "AGREE").andExpect(status().isBadRequest());   // closed
    }

    @Test
    void aFrozenCollaboratorNoLongerCounts() throws Exception {
        react(bobS, "DISAGREE").andExpect(status().isOk());
        send(post(url + "/" + bob + "/freeze"), annS, null).andExpect(status().isOk());
        react(bobS, "AGREE").andExpect(status().isNotFound());   // out of the review
        decide(annS, "ACCEPT").andExpect(status().isOk()).andExpect(jsonPath("$.disagree", hasSize(0)));   // bob's old disagree is ignored
    }
}
