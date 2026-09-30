package com.collabo.backend;

import com.collabo.backend.entity.CredentialKind;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.CredentialService;
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

/** Applying opens credentials both ways: the founder sees the applicant's, the applicant reaches the founder's through the post. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:appcreds;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ApplicationCredentialsTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired CredentialService credentials;

    String ann, bob, cat;
    Cookie annS, bobS, catS;
    String postId;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat);
        credentials.recordFor(ann, CredentialKind.SPACE_FORMED, "Ann's old space", "", "test", "a-" + tag, null);
        credentials.recordFor(bob, CredentialKind.SPACE_FORMED, "Bob's old space", "", "test", "b-" + tag, null);
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

    private void privacy(Cookie who, String value) throws Exception {
        send(patch("/api/users/me"), who, "{\"credentialsPrivacy\":\"" + value + "\"}").andExpect(status().isOk());
    }

    private String apply(Cookie who) throws Exception {
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"me\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    @Test
    void applicantsOnlyMeansPeopleWhoAppliedToMyPosts() throws Exception {
        privacy(annS, "APPLICANTS");
        send(get("/api/users/" + ann + "/credentials"), bobS, null).andExpect(jsonPath("$.visible").value(false));
        String app = apply(bobS);
        send(get("/api/users/" + ann + "/credentials"), bobS, null)
                .andExpect(jsonPath("$.visible").value(true)).andExpect(jsonPath("$.entries", hasSize(1)));
        send(get("/api/users/" + ann + "/credentials"), catS, null).andExpect(jsonPath("$.visible").value(false));
        send(post("/api/applications/" + app + "/withdraw"), bobS, null).andExpect(status().isOk());
        send(get("/api/users/" + ann + "/credentials"), bobS, null).andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void applyingShowsTheFounderYourCredentialsWhateverYourSetting() throws Exception {
        privacy(bobS, "APPLICANTS");
        send(get("/api/users/" + bob + "/credentials"), annS, null).andExpect(jsonPath("$.visible").value(false));
        String app = apply(bobS);
        send(get("/api/users/" + bob + "/credentials"), annS, null)
                .andExpect(jsonPath("$.visible").value(true)).andExpect(jsonPath("$.entries[0].title").value("Bob's old space"));
        send(get("/api/users/" + bob + "/credentials"), catS, null).andExpect(jsonPath("$.visible").value(false));
        send(post("/api/applications/" + app + "/withdraw"), bobS, null).andExpect(status().isOk());
        send(get("/api/users/" + bob + "/credentials"), annS, null).andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void applicantReachesTheFoundersCredentialsOnlyThroughThePost() throws Exception {
        privacy(annS, "MUTUAL");
        send(get("/api/posts/" + postId + "/founder-credentials"), bobS, null).andExpect(status().isNotFound());   // not applied yet
        apply(bobS);
        send(get("/api/posts/" + postId + "/founder-credentials"), bobS, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.visible").value(true)).andExpect(jsonPath("$.entries[0].title").value("Ann's old space"));
        send(get("/api/users/" + ann + "/credentials"), bobS, null).andExpect(jsonPath("$.visible").value(false));   // no general unlock
        send(get("/api/posts/" + postId + "/founder-credentials"), catS, null).andExpect(status().isNotFound());
    }
}
