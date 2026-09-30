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

/** Forming a space from a post: who may, what changes, who may open it. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spaces;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class SpaceApiTest {

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

    @Test
    void needsAnAcceptedApplicant() throws Exception {
        send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isBadRequest());
        decide(catApp, "SHORTLIST");
        send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorFormsIt() throws Exception {
        decide(bobApp, "ACCEPT");
        send(post("/api/posts/" + postId + "/space"), bobS, "{}").andExpect(status().isNotFound());
        send(post("/api/posts/" + postId + "/space"), danS, "{}").andExpect(status().isNotFound());
    }

    @Test
    void formsOncePerPostNamedAfterThePostByDefault() throws Exception {
        String id = form("{}");
        send(get("/api/spaces/" + id), annS, null).andExpect(jsonPath("$.name").value("Idea")).andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.owner.username").value(ann)).andExpect(jsonPath("$.role").value("OWNER"));
        send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void customNameIsUsedAndValidated() throws Exception {
        decide(bobApp, "ACCEPT");
        send(post("/api/posts/" + postId + "/space"), annS, "{\"name\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest());
        send(post("/api/posts/" + postId + "/space"), annS, "{\"name\":\" Synth crew \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Synth crew"));
    }

    @Test
    void theOwnerAndAcceptedApplicantsCanOpenItEveryoneElseGetsNotFound() throws Exception {
        String id = form("{}");
        send(get("/api/spaces/" + id), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("APPLICANT"));
        send(get("/api/posts/" + postId + "/space"), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        send(get("/api/spaces/" + id), catS, null).andExpect(status().isNotFound());   // not accepted
        send(get("/api/spaces/" + id), danS, null).andExpect(status().isNotFound());   // never applied
        send(get("/api/posts/" + postId + "/space"), danS, null).andExpect(status().isNotFound());
    }

    @Test
    void aFormedPostRefusesApplicationsAndLeavesTheOpenFilter() throws Exception {
        form("{}");
        send(get("/api/posts/" + postId), danS, null).andExpect(jsonPath("$.status").value("formed"));
        send(post("/api/posts/" + postId + "/applications"), danS, "{\"statement\":\"late\"}").andExpect(status().isBadRequest());
        send(get("/api/gaze?pending=true"), danS, null).andExpect(jsonPath("$.items[?(@.id=='" + postId + "')]", hasSize(0)));
        send(get("/api/gaze"), danS, null).andExpect(jsonPath("$.items[?(@.id=='" + postId + "')].status", contains("formed")));
    }

    @Test
    void acceptedIsLockedOnceTheSpaceExistsButOthersStillChange() throws Exception {
        form("{}");
        decide(bobApp, "DECLINE").andExpect(status().isBadRequest());
        decide(catApp, "SHORTLIST").andExpect(status().isOk());
        decide(catApp, "DECLINE").andExpect(status().isOk());
    }

    @Test
    void theOwnerGetsAFormedCredentialAndCannotDeleteTheFormedPost() throws Exception {
        form("{}");
        send(get("/api/users/" + ann + "/credentials"), annS, null)
                .andExpect(jsonPath("$.entries[?(@.kind=='SPACE_FORMED')]", hasSize(1)));
        send(delete("/api/posts/" + postId), annS, null).andExpect(status().isBadRequest());
    }
}
