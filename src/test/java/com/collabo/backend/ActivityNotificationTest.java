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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The notifications people expect: a new follower, a post from someone they follow, and new messages (one line until they look). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:activitynotes;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ActivityNotificationTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat;
    Cookie annS, bobS, catS;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat);
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

    private ResultActions notes(Cookie who) throws Exception { return send(get("/api/notifications"), who, null); }

    @Test
    void aNewFollowerIsToldOnceNoMatterHowOftenTheyTapFollow() throws Exception {
        follow(bobS, ann);
        follow(bobS, ann);
        notes(annS).andExpect(jsonPath("$[?(@.title=='New follower')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.title=='New follower')].bucket").value("PERSONAL"))
                .andExpect(jsonPath("$[?(@.title=='New follower')].body").value(bob + " started following you."));
        notes(bobS).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void thoseWhoFollowYouHearOfAPostIfTheirAudienceCoversThemAndNeverOfAnAnonymousOne() throws Exception {
        follow(bobS, ann);   // bob follows ann; cat does not
        send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='" + ann + " posted')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.title=='" + ann + " posted')].bucket").value("ACTIVITY"))
                .andExpect(jsonPath("$[?(@.title=='" + ann + " posted')].body").value("Open call"));
        notes(catS).andExpect(jsonPath("$", hasSize(0)));
        send(post("/api/posts"), annS, "{\"title\":\"Secret\",\"body\":\"Shh\",\"anonymous\":true}").andExpect(status().isOk());
        send(post("/api/posts"), annS, "{\"title\":\"Not for bob\",\"body\":\"x\",\"audience\":\"EXCEPT\",\"audienceWith\":[\"" + bob + "\"]}").andExpect(status().isOk());
        notes(bobS).andExpect(jsonPath("$", hasSize(1)));   // still just the first one
    }

    @Test
    void newMessagesAreOneLineUntilYouLookAndMutingSilencesThem() throws Exception {
        String hello = "{\"username\":\"" + bob + "\",\"body\":\"hi there\"}";
        String res = send(post("/api/yarns/threads/myspace"), annS, hello).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String thread = com.jayway.jsonpath.JsonPath.read(res, "$.id");
        notes(bobS).andExpect(jsonPath("$[?(@.title=='You\\'ve got new messages')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.title=='You\\'ve got new messages')].body").value(ann + ": hi there"));
        notes(annS).andExpect(jsonPath("$", hasSize(0)));   // not told of your own message
        send(post("/api/yarns/threads/" + thread + "/respond"), bobS, "{\"accept\":true}").andExpect(status().isOk());   // a first yarn is a request
        send(post("/api/yarns/threads/" + thread + "/yarns"), annS, "{\"body\":\"hello?\"}").andExpect(status().isCreated());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='You\\'ve got new messages')]", hasSize(1)));   // still one line
        send(post("/api/notifications/read-all"), bobS, null).andExpect(status().is2xxSuccessful());
        send(post("/api/yarns/threads/" + thread + "/yarns"), annS, "{\"body\":\"ping\"}").andExpect(status().isCreated());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='You\\'ve got new messages')]", hasSize(2)));   // read, so a fresh one
        send(patch("/api/yarns/threads/" + thread + "/prefs"), bobS, "{\"muted\":true}").andExpect(status().isOk());
        send(post("/api/notifications/read-all"), bobS, null).andExpect(status().is2xxSuccessful());
        send(post("/api/yarns/threads/" + thread + "/yarns"), annS, "{\"body\":\"psst\"}").andExpect(status().isCreated());
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(0));   // muted
    }
}
