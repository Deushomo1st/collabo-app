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

/** Comments on posts: list, add, delete, and quiet block hiding. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comments;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class CommentApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cy;
    Cookie annS, bobS, cyS;
    String postId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cy = "cy" + tag;
        annS = signIn(ann); bobS = signIn(bob); cyS = signIn(cy);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}")
                .andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
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

    private String uid(String username) { return users.findByUsername(username).orElseThrow().getId().toString(); }

    private String comment(Cookie who, String text) throws Exception {
        String body = send(post("/api/posts/" + postId + "/comments"), who, "{\"body\":\"" + text + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().isUnauthorized());
    }

    @Test
    void addThenListOldestFirst() throws Exception {
        comment(bobS, "first");
        comment(cyS, "second");
        send(get("/api/posts/" + postId + "/comments"), annS, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].body").value("first"))
                .andExpect(jsonPath("$[0].author.username").value(bob))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[1].body").value("second"));
        send(get("/api/posts/" + postId + "/comments"), bobS, null)
                .andExpect(jsonPath("$[0].mine").value(true));
    }

    @Test
    void emptyAndOversizedAreRejected() throws Exception {
        send(post("/api/posts/" + postId + "/comments"), bobS, "{\"body\":\"   \"}").andExpect(status().isBadRequest());
        send(post("/api/posts/" + postId + "/comments"), bobS, "{\"body\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void commenterAndPostAuthorCanDeleteOthersCannot() throws Exception {
        String c1 = comment(bobS, "mine");
        send(delete("/api/posts/" + postId + "/comments/" + c1), cyS, null).andExpect(status().isNotFound());
        send(delete("/api/posts/" + postId + "/comments/" + c1), bobS, null).andExpect(status().isNoContent());
        String c2 = comment(cyS, "rude");
        send(delete("/api/posts/" + postId + "/comments/" + c2), annS, null).andExpect(status().isNoContent());
        send(get("/api/posts/" + postId + "/comments"), annS, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void blockedPeopleDontSeeEachOthersComments() throws Exception {
        comment(bobS, "from bob");
        comment(cyS, "from cy");
        send(put("/api/yarns/blocks/" + uid(bob)), cyS, null).andExpect(status().is2xxSuccessful());
        send(get("/api/posts/" + postId + "/comments"), cyS, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("from cy"));
        send(get("/api/posts/" + postId + "/comments"), bobS, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("from bob"));
    }

    @Test
    void blockedFromThePostCannotComment() throws Exception {
        send(put("/api/yarns/blocks/" + uid(bob)), annS, null).andExpect(status().is2xxSuccessful());
        send(post("/api/posts/" + postId + "/comments"), bobS, "{\"body\":\"hi\"}").andExpect(status().isNotFound());
    }

    @Test
    void deletingThePostTakesItsCommentsWithIt() throws Exception {
        comment(bobS, "bye");
        send(delete("/api/posts/" + postId), annS, null).andExpect(status().isNoContent());
        send(get("/api/posts/" + postId + "/comments"), bobS, null).andExpect(status().isNotFound());
    }
}
