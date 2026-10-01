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

/** The WeSpace about view and the collaborators' governance: nudge, freeze, unfreeze, disband and coming back. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wespace;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class WeSpaceTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat, dan;
    Cookie annS, bobS, catS, danS;
    String postId, url;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        url = "/api/posts/" + postId + "/collaborators";
        follow(annS, bob); follow(bobS, ann);
        follow(annS, cat); follow(catS, ann);
        for (String who : new String[]{bob, cat}) send(post(url), annS, "{\"username\":\"" + who + "\"}").andExpect(status().isOk());
        send(post(url + "/accept"), bobS, null).andExpect(status().isOk());
        send(post(url + "/accept"), catS, null).andExpect(status().isOk());
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

    private ResultActions about(Cookie who) throws Exception { return send(get("/api/posts/" + postId + "/wespace"), who, null); }

    @Test
    void theAboutViewListsTheFounderFirstAndIsClosedToOutsiders() throws Exception {
        about(annS).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("FOUNDER")).andExpect(jsonPath("$.title").value("Idea"))
                .andExpect(jsonPath("$.seats", hasSize(3))).andExpect(jsonPath("$.seats[0].founder").value(true)).andExpect(jsonPath("$.threadId").isNotEmpty());
        about(bobS).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("COLLABORATOR"));
        about(danS).andExpect(status().isNotFound());
    }

    @Test
    void aFrozenCollaboratorCanReadButNotWriteAndTheFounderCanUnfreeze() throws Exception {
        send(post(url + "/" + bob + "/freeze"), bobS, null).andExpect(status().isForbidden());   // founder only
        send(post(url + "/" + bob + "/freeze"), annS, null).andExpect(status().isOk());
        send(post(url + "/" + bob + "/freeze"), annS, null).andExpect(status().isBadRequest());   // already frozen
        String room = com.jayway.jsonpath.JsonPath.read(about(bobS).andExpect(jsonPath("$.role").value("FROZEN")).andReturn().getResponse().getContentAsString(), "$.threadId");
        send(post("/api/yarns/threads/" + room + "/yarns"), bobS, "{\"body\":\"hello\"}").andExpect(status().isForbidden());
        send(post("/api/yarns/threads/" + room + "/yarns"), catS, "{\"body\":\"hello\"}").andExpect(status().isCreated());
        send(post(url + "/" + bob + "/unfreeze"), annS, null).andExpect(status().isOk());
        send(post("/api/yarns/threads/" + room + "/yarns"), bobS, "{\"body\":\"back\"}").andExpect(status().isCreated());
    }

    @Test
    void disbandingNeedsAReasonKeepsTheSpotAndTheyCanBeAskedBack() throws Exception {
        send(post(url + "/" + bob + "/disband"), annS, "{\"reason\":\"  \"}").andExpect(status().isBadRequest());
        send(post(url + "/" + bob + "/disband"), annS, "{\"reason\":\"Gone quiet for a month\"}").andExpect(status().isOk());
        about(annS).andExpect(jsonPath("$.seats[?(@.person.username=='" + bob + "')].state").value("DISBANDED"))
                .andExpect(jsonPath("$.seats[?(@.person.username=='" + bob + "')].reason").value("Gone quiet for a month"));
        about(bobS).andExpect(status().isNotFound());   // out of the room
        send(post(url), annS, "{\"username\":\"" + bob + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INVITED"));
        send(post(url + "/accept"), bobS, null).andExpect(status().isOk());
        about(bobS).andExpect(status().isOk());
    }

    @Test
    void anyActiveCollaboratorCanNudgeAndOutsidersCannot() throws Exception {
        send(post(url + "/" + bob + "/nudge"), catS, null).andExpect(status().isOk());
        send(post(url + "/" + bob + "/nudge"), danS, null).andExpect(status().isNotFound());
        send(post(url + "/" + bob + "/nudge"), bobS, null).andExpect(status().isNotFound());   // not yourself
    }
}
