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

/** Collaborators: asking a mutual follow in, accepting, declining, stepping down, reviewing together, joining the space. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:collaborators;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class CollaboratorApiTest {

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

    @Test
    void onlyAMutualFollowCanBeAsked() throws Exception {
        invite(bob).andExpect(status().isBadRequest());
        follow(annS, bob);
        invite(bob).andExpect(status().isBadRequest());   // one way is not enough
        follow(bobS, ann);
        invite(ann).andExpect(status().isBadRequest());   // not yourself
        invite(bob).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INVITED")).andExpect(jsonPath("$.person.username").value(bob));
        invite(bob).andExpect(status().isBadRequest());   // already asked
        send(post(url), bobS, "{\"username\":\"" + cat + "\"}").andExpect(status().isNotFound());   // only the author asks
    }

    @Test
    void theInviteeAcceptsOrDeclinesAndADeclinedPersonCanBeAskedAgain() throws Exception {
        mutual(bobS, bob);
        invite(bob);
        send(post(url + "/accept"), danS, null).andExpect(status().isNotFound());   // not asked
        send(get("/api/collaborations"), bobS, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].postId").value(postId))
                .andExpect(jsonPath("$[0].founder.username").value(ann));
        send(post(url + "/decline"), bobS, null).andExpect(status().isOk());
        send(get("/api/collaborations"), bobS, null).andExpect(jsonPath("$", hasSize(0)));
        send(get(url), annS, null).andExpect(jsonPath("$", hasSize(0)));
        invite(bob).andExpect(status().isOk());
        send(post(url + "/accept"), bobS, null).andExpect(status().isOk());
        send(get("/api/collaborations"), bobS, null).andExpect(jsonPath("$[0].state").value("ACTIVE")).andExpect(jsonPath("$[0].postStatus").value("pending"));
        send(get(url), annS, null).andExpect(jsonPath("$[0].state").value("ACTIVE"));
        send(get(url), bobS, null).andExpect(status().isOk());
        send(get(url), danS, null).andExpect(status().isNotFound());
    }

    @Test
    void collaboratorsReviewTheStackTogetherAndCannotApply() throws Exception {
        makeCollaborator(bobS, bob);
        send(get("/api/posts/" + postId + "/applications"), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        send(patch("/api/applications/" + catApp), bobS, "{\"decision\":\"SHORTLIST\"}").andExpect(status().isOk());
        send(get("/api/posts/" + postId + "/applications"), danS, null).andExpect(status().isNotFound());
        send(post("/api/posts/" + postId + "/applications"), bobS, "{\"statement\":\"me\"}").andExpect(status().isBadRequest());
    }

    @Test
    void anInvitedPersonHasNoReviewPowersYet() throws Exception {
        mutual(bobS, bob);
        invite(bob);
        send(get("/api/posts/" + postId + "/applications"), bobS, null).andExpect(status().isNotFound());
    }

    @Test
    void theAuthorRemovesAndACollaboratorCanStepDown() throws Exception {
        makeCollaborator(bobS, bob);
        send(delete(url + "/" + bob), danS, null).andExpect(status().isNotFound());
        send(delete(url + "/" + bob), annS, null).andExpect(status().isOk());
        send(get("/api/posts/" + postId + "/applications"), bobS, null).andExpect(status().isNotFound());
        makeCollaborator(bobS, bob);   // asked again after removal
        send(delete(url + "/" + bob), bobS, null).andExpect(status().isOk());   // stepping down
        send(get(url), annS, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void collaboratorsAreMembersOfTheSpaceFromTheStart() throws Exception {
        makeCollaborator(bobS, bob);
        send(patch("/api/applications/" + catApp), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String body = send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String space = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(get("/api/spaces/" + space), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER")).andExpect(jsonPath("$.canManage").value(true));
        send(get("/api/spaces/" + space), annS, null).andExpect(jsonPath("$.canManage").value(true));
        send(get("/api/spaces/" + space), catS, null).andExpect(jsonPath("$.canManage").value(false));
        send(get("/api/spaces/" + space + "/members"), annS, null)
                .andExpect(jsonPath("$[?(@.person.username=='" + bob + "')].title", contains("Collaborator")));
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isBadRequest());
        send(post("/api/spaces/" + space + "/leave"), bobS, null).andExpect(status().isBadRequest());   // step down instead
        // asked after the space exists: in at once, and out again when removed
        makeCollaborator(danS, dan);
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(3)));
        send(delete(url + "/" + dan), annS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space), danS, null).andExpect(status().isNotFound());
        send(get("/api/spaces/" + space + "/members"), annS, null).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void collaboratorsSetTitlesButNotTheOwnersRole() throws Exception {
        makeCollaborator(bobS, bob);
        send(patch("/api/applications/" + catApp), annS, "{\"decision\":\"ACCEPT\"}");
        String body = send(post("/api/posts/" + postId + "/space"), annS, "{}").andReturn().getResponse().getContentAsString();
        String space = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(post("/api/spaces/" + space + "/join"), catS, null).andExpect(status().isOk());
        send(patch("/api/spaces/" + space + "/members/" + cat), bobS, "{\"title\":\"Designer\"}").andExpect(status().isOk());
        send(patch("/api/spaces/" + space + "/members/" + ann), bobS, "{\"title\":\"King\"}").andExpect(status().isBadRequest());
        send(patch("/api/spaces/" + space + "/members/" + cat), catS, "{\"title\":\"Boss\"}").andExpect(status().isNotFound());
    }
}
