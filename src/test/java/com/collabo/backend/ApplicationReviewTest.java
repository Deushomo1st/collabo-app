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

/** The founder's review stack: list, sort, filter, and the three decisions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:review;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ApplicationReviewTest {

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
        bobApp = apply(bobS, "from bob");
        Thread.sleep(5);   // distinct createdAt so the order is certain
        catApp = apply(catS, "from cat");
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

    private String apply(Cookie who, String statement) throws Exception {
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"" + statement + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions decide(Cookie who, String appId, String decision) throws Exception {
        return send(patch("/api/applications/" + appId), who, "{\"decision\":\"" + decision + "\"}");
    }

    @Test
    void onlyTheAuthorSeesTheStack() throws Exception {
        send(get("/api/posts/" + postId + "/applications"), bobS, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + postId + "/applications"), danS, null).andExpect(status().isNotFound());
        send(get("/api/posts/" + postId + "/applications"), annS, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void sortsRecentFirstByDefaultOrOldestFirst() throws Exception {
        send(get("/api/posts/" + postId + "/applications"), annS, null)
                .andExpect(jsonPath("$[0].applicant.username").value(cat)).andExpect(jsonPath("$[0].statement").value("from cat"))
                .andExpect(jsonPath("$[1].applicant.username").value(bob));
        send(get("/api/posts/" + postId + "/applications?sort=oldest"), annS, null)
                .andExpect(jsonPath("$[0].applicant.username").value(bob));
        send(get("/api/posts/" + postId + "/applications?sort=sideways"), annS, null).andExpect(status().isBadRequest());
    }

    @Test
    void decisionsSetTheStateAndFiltersFollow() throws Exception {
        decide(annS, bobApp, "SHORTLIST").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SHORTLISTED"));
        send(get("/api/posts/" + postId + "/applications?filter=unreviewed"), annS, null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].applicant.username").value(cat));
        send(get("/api/posts/" + postId + "/applications?filter=shortlisted"), annS, null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].applicant.username").value(bob));
        decide(annS, catApp, "DECLINE").andExpect(jsonPath("$.state").value("DECLINED"));
        decide(annS, bobApp, "ACCEPT").andExpect(jsonPath("$.state").value("ACCEPTED"));
        send(get("/api/applications/mine"), bobS, null).andExpect(jsonPath("$[0].state").value("ACCEPTED"));
        send(get("/api/posts/" + postId + "/applications?filter=nope"), annS, null).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorDecidesAndOnlyWithARealDecision() throws Exception {
        decide(bobS, catApp, "ACCEPT").andExpect(status().isNotFound());
        decide(danS, bobApp, "ACCEPT").andExpect(status().isNotFound());
        decide(annS, bobApp, "MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void withdrawnLeaveTheStackAndCannotBeDecided() throws Exception {
        send(post("/api/applications/" + bobApp + "/withdraw"), bobS, null).andExpect(status().isOk());
        send(get("/api/posts/" + postId + "/applications"), annS, null).andExpect(jsonPath("$", hasSize(1)));
        decide(annS, bobApp, "ACCEPT").andExpect(status().isBadRequest());
    }

    @Test
    void decidedApplicationsCannotBeWithdrawnByTheApplicant() throws Exception {
        decide(annS, bobApp, "DECLINE").andExpect(status().isOk());
        send(post("/api/applications/" + bobApp + "/withdraw"), bobS, null).andExpect(status().isBadRequest());
    }
}
