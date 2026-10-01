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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Likes (quiet, one per person), regular posts (applications switched off) and sharing an existing post by yarn. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:likeshare;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class PostLikeShareTest {

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
        postId = idOf(send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}"));
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

    private static String idOf(ResultActions r) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void likeIsIdempotentCountedAndPersonal() throws Exception {
        send(put("/api/posts/" + postId + "/like"), bobS, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes").value(1)).andExpect(jsonPath("$.liked").value(true));
        send(put("/api/posts/" + postId + "/like"), bobS, null).andExpect(jsonPath("$.likes").value(1));
        send(put("/api/posts/" + postId + "/like"), annS, null).andExpect(jsonPath("$.likes").value(2));   // your own post too
        send(get("/api/posts/" + postId), catS, null).andExpect(jsonPath("$.likes").value(2)).andExpect(jsonPath("$.liked").value(false));
        send(delete("/api/posts/" + postId + "/like"), bobS, null)
                .andExpect(jsonPath("$.likes").value(1)).andExpect(jsonPath("$.liked").value(false));
        send(put("/api/posts/" + postId + "/like"), catS, null).andExpect(jsonPath("$.likes").value(2));
        send(get("/api/gaze"), catS, null)   // feed items carry the same fields
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].likes").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].liked").value(org.hamcrest.Matchers.contains(true)));
    }

    private String uid(String username) { return users.findByUsername(username).orElseThrow().getId().toString(); }

    @Test
    void commentLikesCountAndTheFeedPreviewsComments() throws Exception {
        String c = idOf(send(post("/api/posts/" + postId + "/comments"), bobS, "{\"body\":\"Count me in\"}"));
        String like = "/api/posts/" + postId + "/comments/" + c + "/like";
        send(put(like), catS, null).andExpect(jsonPath("$.likes").value(1)).andExpect(jsonPath("$.liked").value(true));
        send(put(like), catS, null).andExpect(jsonPath("$.likes").value(1));   // idempotent
        send(get("/api/posts/" + postId + "/comments"), annS, null).andExpect(jsonPath("$[0].likes").value(1)).andExpect(jsonPath("$[0].liked").value(false));
        send(get("/api/posts/" + postId + "/comments"), catS, null).andExpect(jsonPath("$[0].liked").value(true));
        send(get("/api/gaze"), catS, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].commentCount").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].sample[0].username").value(org.hamcrest.Matchers.contains(bob)));
        send(delete(like), catS, null).andExpect(jsonPath("$.likes").value(0)).andExpect(jsonPath("$.liked").value(false));
        // a block either way hides the comment from the preview and from liking it; the count stays
        send(put("/api/yarns/blocks/" + uid(bob)), catS, null).andExpect(status().is2xxSuccessful());
        send(put(like), catS, null).andExpect(status().isNotFound());
        send(get("/api/gaze"), catS, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].sample[0]").isEmpty());
    }

    @Test
    void repliesThreadUnderTheirCommentAndGoWithIt() throws Exception {
        String base = "/api/posts/" + postId + "/comments";
        String top = idOf(send(post(base), bobS, "{\"body\":\"Count me in\"}"));
        String r1 = idOf(send(post(base), catS, "{\"body\":\"Same here\",\"parentId\":\"" + top + "\"}"));
        // replying to a reply nests one level further down
        send(post(base), annS, "{\"body\":\"Welcome both\",\"parentId\":\"" + r1 + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").value(r1));
        send(get(base), annS, null)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.id == '" + top + "')].replies").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.id == '" + r1 + "')].replies").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.id == '" + r1 + "')].parentId").value(org.hamcrest.Matchers.contains(top)));
        send(put(base + "/" + r1 + "/like"), annS, null).andExpect(jsonPath("$.likes").value(1)).andExpect(jsonPath("$.parentId").value(top));
        // the feed counts every comment but previews only top-level ones
        send(get("/api/gaze"), catS, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].commentCount").value(org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$.items[?(@.id == '" + postId + "')].sample.length()").value(org.hamcrest.Matchers.contains(1)));
        // a comment from another post cannot be replied to
        String other = idOf(send(post("/api/posts"), annS, "{\"title\":\"Other\",\"body\":\"Another post.\"}"));
        send(post("/api/posts/" + other + "/comments"), catS, "{\"body\":\"x\",\"parentId\":\"" + top + "\"}").andExpect(status().isNotFound());
        // deleting the first comment takes everything under it, at any depth
        send(delete(base + "/" + top), bobS, null).andExpect(status().is2xxSuccessful());
        send(get(base), annS, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aRegularPostTakesNoApplications() throws Exception {
        String regular = idOf(send(post("/api/posts"), annS, "{\"title\":\"Thought\",\"body\":\"Just a thought.\",\"applicationsOn\":false}"));
        send(get("/api/posts/" + regular), bobS, null).andExpect(jsonPath("$.applicationsOn").value(false));
        send(post("/api/posts/" + regular + "/applications"), bobS, "{\"statement\":\"Pick me\"}").andExpect(status().isBadRequest());
        send(get("/api/posts/" + postId), bobS, null).andExpect(jsonPath("$.applicationsOn").value(true));   // the default is unchanged
    }

    @Test
    void shareNeedsPeopleAndHonoursTheAudience() throws Exception {
        send(post("/api/posts/" + postId + "/share"), bobS, "{\"usernames\":[]}").andExpect(status().isBadRequest());
        send(post("/api/posts/" + postId + "/share"), bobS, "{\"usernames\":[\"" + cat + "\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.shared").isNumber());
        String limited = idOf(send(post("/api/posts"), annS, "{\"title\":\"Private\",\"body\":\"For followers.\",\"audience\":\"FOLLOWERS\"}"));
        send(put("/api/users/" + ann + "/follow"), bobS, null).andExpect(status().isOk());   // bob may see it, but it is not his to pass on
        send(post("/api/posts/" + limited + "/share"), bobS, "{\"usernames\":[\"" + cat + "\"]}").andExpect(status().isBadRequest());
    }
}
