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

/** Spaces and the Yarns they open: the Workspace thread, the collaborators' WeSpace, and who sits in each. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spacethreads;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class SpaceThreadTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

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

    private String form() throws Exception {
        send(patch("/api/applications/" + catApp), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String body = send(post("/api/posts/" + postId + "/space"), annS, "{\"name\":\"Synth crew\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions rooms(Cookie who, String tier) throws Exception { return send(get("/api/yarns/threads?tier=" + tier), who, null); }

    private String roomId(Cookie who, String tier) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(rooms(who, tier).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    @Test
    void formingOpensAWorkspaceForTheOwnerAndAcceptedApplicantsStayOutUntilTheyJoin() throws Exception {
        String space = form();
        rooms(annS, "WORKSPACE").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Synth crew"));
        rooms(catS, "WORKSPACE").andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/spaces/" + space), annS, null).andExpect(jsonPath("$.threadId").value(roomId(annS, "WORKSPACE")));
        send(get("/api/spaces/" + space), catS, null).andExpect(jsonPath("$.threadId").value(nullValue()));
    }

    @Test
    void joiningSeatsYouAndLeavingOrRemovalTakesYouOut() throws Exception {
        String space = form();
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());
        String thread = roomId(annS, "WORKSPACE");
        rooms(catS, "WORKSPACE").andExpect(jsonPath("$", hasSize(1)));
        send(get("/api/spaces/" + space), catS, null).andExpect(jsonPath("$.threadId").value(thread));
        send(get("/api/yarns/threads/" + thread + "/yarns"), catS, null)
                .andExpect(jsonPath("$[0].body").value(cat + " joined.")).andExpect(jsonPath("$[0].kind").value("SYSTEM"));
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());   // joining twice adds nothing
        send(get("/api/yarns/threads/" + thread + "/yarns"), annS, null).andExpect(jsonPath("$", hasSize(2)));

        send(post("/api/spaces/" + space + "/leave"), catS, null).andExpect(status().isOk());
        rooms(catS, "WORKSPACE").andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/yarns/threads/" + thread + "/yarns"), catS, null).andExpect(status().isNotFound());
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());
        rooms(catS, "WORKSPACE").andExpect(jsonPath("$", hasSize(1)));

        send(delete("/api/spaces/" + space + "/members/" + cat), annS, null).andExpect(status().isOk());
        rooms(catS, "WORKSPACE").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void collaboratorsGetOneWeSpaceForThePostAndLeaveItWhenTheyStepDown() throws Exception {
        rooms(annS, "WESPACE").andExpect(jsonPath("$", hasSize(0)));
        makeCollaborator(bobS, bob);
        rooms(bobS, "WESPACE").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Idea"))
                .andExpect(jsonPath("$[0].members", hasSize(2)));
        makeCollaborator(danS, dan);
        rooms(annS, "WESPACE").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].members", hasSize(3)));
        send(delete(url + "/" + bob), bobS, null).andExpect(status().isOk());
        rooms(bobS, "WESPACE").andExpect(jsonPath("$", hasSize(0)));
        rooms(annS, "WESPACE").andExpect(jsonPath("$[0].members", hasSize(2)));
    }

    @Test
    void collaboratorsSitInTheWorkspaceFromTheStartOrOnAcceptingAndLeaveWhenRemoved() throws Exception {
        makeCollaborator(bobS, bob);
        form();
        rooms(bobS, "WORKSPACE").andExpect(jsonPath("$", hasSize(1)));
        makeCollaborator(danS, dan);
        rooms(danS, "WORKSPACE").andExpect(jsonPath("$", hasSize(1)));
        send(delete(url + "/" + dan), annS, null).andExpect(status().isOk());
        rooms(danS, "WORKSPACE").andExpect(jsonPath("$", hasSize(0)));
    }
}
