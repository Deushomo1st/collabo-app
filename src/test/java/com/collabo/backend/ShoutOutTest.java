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

/** Shout-outs (reposts): free, silent, one per person per post; they surface in Shared Gaze and on profiles. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shouts;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ShoutOutTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat;
    Cookie annS, bobS, catS;
    String postId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat);
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

    @Test
    void shoutIsIdempotentAndCounted() throws Exception {
        send(put("/api/posts/" + postId + "/shout"), bobS, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.shouts").value(1)).andExpect(jsonPath("$.shouted").value(true));
        send(put("/api/posts/" + postId + "/shout"), bobS, null).andExpect(jsonPath("$.shouts").value(1));
        send(put("/api/posts/" + postId + "/shout"), catS, null).andExpect(jsonPath("$.shouts").value(2));
        send(get("/api/posts/" + postId), annS, null).andExpect(jsonPath("$.shouts").value(2)).andExpect(jsonPath("$.shouted").value(false));
        send(delete("/api/posts/" + postId + "/shout"), bobS, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.shouts").value(1)).andExpect(jsonPath("$.shouted").value(false));
    }

    @Test
    void cannotShoutYourOwnOrABlockedPost() throws Exception {
        send(put("/api/posts/" + postId + "/shout"), annS, null).andExpect(status().isBadRequest());
        send(put("/api/yarns/blocks/" + uid(bob)), annS, null).andExpect(status().is2xxSuccessful());
        send(put("/api/posts/" + postId + "/shout"), bobS, null).andExpect(status().isNotFound());
    }

    @Test
    void feedItemsCarryShoutState() throws Exception {
        send(put("/api/posts/" + postId + "/shout"), bobS, null).andExpect(status().isOk());
        send(get("/api/gaze"), bobS, null)
                .andExpect(jsonPath("$.items[0].shouts").value(1)).andExpect(jsonPath("$.items[0].shouted").value(true));
        send(get("/api/gaze"), catS, null)
                .andExpect(jsonPath("$.items[0].shouts").value(1)).andExpect(jsonPath("$.items[0].shouted").value(false));
    }

    @Test
    void sharedGazeSurfacesNetworkShoutOuts() throws Exception {
        send(put("/api/users/" + bob + "/follow"), catS, null).andExpect(status().isOk());   // cat follows bob; ann is a stranger to cat
        send(get("/api/gaze?feed=shared"), catS, null).andExpect(jsonPath("$.items", hasSize(0)));
        send(put("/api/posts/" + postId + "/shout"), bobS, null).andExpect(status().isOk());
        send(get("/api/gaze?feed=shared"), catS, null)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].shoutedBy.username").value(bob));
        send(get("/api/gaze"), catS, null).andExpect(jsonPath("$.items[0].shoutedBy").doesNotExist());
    }

    @Test
    void profileTabsListPostsAndReposts() throws Exception {
        send(post("/api/posts"), bobS, "{\"title\":\"Bobs\",\"body\":\"b\"}").andExpect(status().isOk());
        send(put("/api/posts/" + postId + "/shout"), bobS, null).andExpect(status().isOk());
        send(get("/api/users/" + bob + "/posts"), catS, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].title").value("Bobs"));
        send(get("/api/users/" + bob + "/posts?tab=reposts"), catS, null)
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value(postId));
    }

    @Test
    void blockedProfileIsNotFound() throws Exception {
        send(put("/api/yarns/blocks/" + uid(bob)), catS, null).andExpect(status().is2xxSuccessful());
        send(get("/api/users/" + bob + "/posts"), catS, null).andExpect(status().isNotFound());
    }
}
