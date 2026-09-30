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

/** Accepting an applicant leaves a system yarn in a MySpace thread between the decider and the applicant. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:acceptyarn;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class AcceptanceYarnTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired com.collabo.backend.repository.ApplicationRepository applications;

    String ann, bob, cat, dan;
    Cookie annS, bobS, catS, danS;
    String postId, catApp, url;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}")
                .andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        url = "/api/posts/" + postId + "/collaborators";
        String app = send(post("/api/posts/" + postId + "/applications"), catS, "{\"statement\":\"me\"}")
                .andReturn().getResponse().getContentAsString();
        catApp = com.jayway.jsonpath.JsonPath.read(app, "$.id");
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

    private ResultActions invite(String username) throws Exception { return send(post(url), annS, "{\"username\":\"" + username + "\"}"); }

    private void mutual(Cookie s, String name) throws Exception { follow(annS, name); follow(s, ann); }

    private void makeCollaborator(Cookie s, String name) throws Exception {
        mutual(s, name);
        invite(name).andExpect(status().isOk());
        send(post(url + "/accept"), s, null).andExpect(status().isOk());
    }

    private ResultActions accept(Cookie by) throws Exception {
        return send(patch("/api/applications/" + catApp), by, "{\"decision\":\"ACCEPT\"}");
    }

    private ResultActions myspaces(Cookie who) throws Exception { return send(get("/api/yarns/threads?tier=MYSPACE"), who, null); }

    @Test
    void acceptingDropsASystemYarnIntoAMySpaceThreadBothSeeAndItIsNotARequest() throws Exception {
        myspaces(catS).andExpect(jsonPath("$", hasSize(0)));
        accept(annS).andExpect(status().isOk());
        myspaces(catS).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value(ann))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED")).andExpect(jsonPath("$[0].incomingRequest").value(false))
                .andExpect(jsonPath("$[0].unread").value(1)).andExpect(jsonPath("$[0].lastBody", containsString("Idea")));
        myspaces(annS).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value(cat));
        String thread = com.jayway.jsonpath.JsonPath.read(myspaces(catS).andReturn().getResponse().getContentAsString(), "$[0].id");
        send(get("/api/yarns/threads/" + thread + "/yarns"), catS, null)
                .andExpect(jsonPath("$[0].kind").value("SYSTEM")).andExpect(jsonPath("$[0].body", containsString(ann)));
        send(post("/api/yarns/threads/" + thread + "/yarns"), catS, "{\"body\":\"Thank you!\"}").andExpect(status().isCreated());   // they can talk straight away
    }

    @Test
    void oneYarnPerAcceptanceAndAnExistingThreadIsReused() throws Exception {
        send(post("/api/yarns/threads/myspace"), catS, "{\"username\":\"" + ann + "\",\"body\":\"hi\"}").andExpect(status().isCreated());   // a pending request first
        accept(annS).andExpect(status().isOk());
        accept(annS).andExpect(status().isOk());   // no change, no second yarn
        myspaces(catS).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        String thread = com.jayway.jsonpath.JsonPath.read(myspaces(catS).andReturn().getResponse().getContentAsString(), "$[0].id");
        send(get("/api/yarns/threads/" + thread + "/yarns"), catS, null).andExpect(jsonPath("$", hasSize(2)));   // hi + acceptance
    }

    @Test
    void aCollaboratorWhoAcceptsIsTheOneWhoWrites() throws Exception {
        makeCollaborator(bobS, bob);
        accept(bobS).andExpect(status().isOk());
        myspaces(catS).andExpect(jsonPath("$[0].name").value(bob));
        myspaces(annS).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aBlockKeepsTheAcceptanceYarnQuietButTheAcceptanceStands() throws Exception {
        send(put("/api/yarns/blocks/" + users.findByUsername(ann).orElseThrow().getId()), catS, null).andExpect(status().isNoContent());
        accept(annS).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(com.collabo.backend.entity.ApplicationState.ACCEPTED,
                applications.findById(UUID.fromString(catApp)).orElseThrow().getState());
        myspaces(annS).andExpect(jsonPath("$", hasSize(0)));
    }
}
