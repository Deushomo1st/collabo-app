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

/** Joining, leaving, the members list, role titles and permissions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:members;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class SpaceMembershipTest {

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

    @Test
    void onlyAnAcceptedApplicantJoinsAndTheRoleBecomesMember() throws Exception {
        space = form("{}");
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isNotFound());   // not accepted
        send(post("/api/spaces/" + space + "/join"), danS, null).andExpect(status().isNotFound());
        send(post("/api/spaces/" + space + "/join"), annS, null).andExpect(status().isBadRequest());   // owner is already in
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space), bobS, null).andExpect(jsonPath("$.role").value("MEMBER"));
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());   // joining twice changes nothing
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void membersListShowsTheOwnerFirstAndIsClosedToOutsiders() throws Exception {
        formAndJoin();
        send(get("/api/spaces/" + space + "/members"), bobS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].owner").value(true)).andExpect(jsonPath("$[0].person.username").value(ann))
                .andExpect(jsonPath("$[1].person.username").value(bob)).andExpect(jsonPath("$[1].permissions", hasSize(0)));
        send(get("/api/spaces/" + space + "/members"), danS, null).andExpect(status().isNotFound());
    }

    @Test
    void leavingRemovesYouFromTheListAndYouCanComeBack() throws Exception {
        formAndJoin();
        send(post("/api/spaces/" + space + "/leave"), annS, null).andExpect(status().isBadRequest());
        send(post("/api/spaces/" + space + "/leave"), bobS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(1)));
        send(get("/api/spaces/" + space), bobS, null).andExpect(jsonPath("$.role").value("APPLICANT"));
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void joiningEarnsACredentialOnce() throws Exception {
        formAndJoin();
        send(post("/api/spaces/" + space + "/leave"), bobS, null);
        send(post("/api/spaces/" + space + "/join"), bobS, null);
        send(get("/api/users/" + bob + "/credentials"), bobS, null)
                .andExpect(jsonPath("$.entries[?(@.kind=='SPACE_FORMED')]", hasSize(1)));
    }

    @Test
    void theOwnerSetsTitlesAndPermissionsNobodyElseCan() throws Exception {
        formAndJoin();
        String url = "/api/spaces/" + space + "/members/" + bob;
        send(patch(url), bobS, "{\"title\":\"Boss\"}").andExpect(status().isNotFound());   // members do not edit members
        send(patch(url), annS, "{\"title\":\"  Sound designer \",\"permissions\":[\"LOG_MILESTONES\",\"LOG_PAYMENTS\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Sound designer"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("LOG_MILESTONES", "LOG_PAYMENTS")));
        send(patch(url), annS, "{\"title\":\"" + "x".repeat(41) + "\"}").andExpect(status().isBadRequest());
        send(patch(url), annS, "{\"permissions\":[\"FLY\"]}").andExpect(status().isBadRequest());
        send(patch(url), annS, "{\"title\":\"Lead\"}").andExpect(status().isOk())   // untouched fields stay
                .andExpect(jsonPath("$.permissions", hasSize(2)));
    }

    @Test
    void weightyPermissionsNeedAnExplicitConfirmation() throws Exception {
        formAndJoin();
        String url = "/api/spaces/" + space + "/members/" + bob;
        send(patch(url), annS, "{\"permissions\":[\"ACCEPT_MEMBERS\"]}").andExpect(status().isBadRequest());
        send(patch(url), annS, "{\"permissions\":[\"ACCEPT_MEMBERS\"],\"confirm\":true}").andExpect(status().isOk());
    }

    @Test
    void aMemberWithAcceptMembersCanRemoveOthersOtherwiseOnlyTheOwner() throws Exception {
        formAndJoin();
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isNotFound());
        decide(catApp, "ACCEPT").andExpect(status().isOk());
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());
        send(delete("/api/spaces/" + space + "/members/" + cat), bobS, null).andExpect(status().isNotFound());
        send(patch("/api/spaces/" + space + "/members/" + bob), annS, "{\"permissions\":[\"ACCEPT_MEMBERS\"],\"confirm\":true}");
        send(delete("/api/spaces/" + space + "/members/" + ann), bobS, null).andExpect(status().isBadRequest());   // the owner stays
        send(delete("/api/spaces/" + space + "/members/" + cat), bobS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(2)));
        send(get("/api/spaces/" + space), catS, null).andExpect(status().isNotFound());   // removal ends reading
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isNotFound());   // and joining
    }
}
