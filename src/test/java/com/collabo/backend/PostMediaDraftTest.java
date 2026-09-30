package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The post page's backend: pictures and videos, hashtags, the comment and shout-out switches, drafts, and sharing a post by yarn. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:postmedia;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class PostMediaDraftTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");
    /** A PNG signature followed by filler: enough for the type check, which reads the first 12 bytes. */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob;
    Cookie annS, bobS;

    @BeforeEach
    void accounts() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag;
        annS = signIn(ann); bobS = signIn(bob);
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

    private ResultActions upload(Cookie session, String type, byte[] bytes) throws Exception {
        return mvc.perform(post("/api/media").cookie(XSRF, session).header("X-XSRF-TOKEN", "t").contentType(type).content(bytes));
    }

    private String uploaded(Cookie session) throws Exception {
        String res = upload(session, "image/png", PNG).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    private String read(ResultActions r, String path) throws Exception { return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path); }

    @Test
    void anUploadIsOnlyYoursUntilItIsPosted() throws Exception {
        String id = uploaded(annS);
        mvc.perform(get("/api/media/" + id).cookie(annS)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mvc.perform(get("/api/media/" + id).cookie(bobS)).andExpect(status().isNotFound());

        send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"mediaIds\":[\"" + id + "\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].id").value(id)).andExpect(jsonPath("$.media[0].kind").value("IMAGE"));
        mvc.perform(get("/api/media/" + id).cookie(bobS)).andExpect(status().isOk());   // on a post bob can see
    }

    @Test
    void uploadsAreCheckedByTheirContent() throws Exception {
        upload(annS, "image/png", "definitely not a png".getBytes()).andExpect(status().isBadRequest());
        upload(annS, "application/pdf", PNG).andExpect(status().isBadRequest());
        upload(annS, "image/png", new byte[6 * 1024 * 1024]).andExpect(status().isBadRequest());
        mvc.perform(post("/api/media").cookie(annS)).andExpect(status().isForbidden());   // no CSRF token
    }

    @Test
    void someoneElsesUploadCannotBeAttached() throws Exception {
        String id = uploaded(annS);
        send(post("/api/posts"), bobS, "{\"title\":\"T\",\"body\":\"B\",\"mediaIds\":[\"" + id + "\"]}").andExpect(status().isBadRequest());
    }

    @Test
    void hashtagsAreCleanedAndLimited() throws Exception {
        send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"hashtags\":[\"#Design\",\"design\",\"Web_3\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.hashtags", contains("design", "web_3")));
        send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"hashtags\":[\"has space\"]}").andExpect(status().isBadRequest());
        send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"hashtags\":[\"a1\",\"a2\",\"a3\",\"a4\",\"a5\",\"a6\",\"a7\",\"a8\",\"a9\",\"b1\",\"b2\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAuthorCanSwitchCommentsAndShoutOutsOff() throws Exception {
        String id = read(send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"commentsOn\":false,\"shoutsOn\":false}")
                .andExpect(jsonPath("$.commentsOn").value(false)).andExpect(jsonPath("$.shoutsOn").value(false)), "$.id");
        send(post("/api/posts/" + id + "/comments"), bobS, "{\"body\":\"hi\"}").andExpect(status().isBadRequest());
        send(put("/api/posts/" + id + "/shout"), bobS, null).andExpect(status().isBadRequest());
    }

    @Test
    void draftsAreSavedUpdatedListedAndPrivate() throws Exception {
        send(put("/api/drafts"), annS, "{}").andExpect(status().isBadRequest());   // nothing to save
        String id = read(send(put("/api/drafts"), annS, "{\"title\":\"Half an idea\",\"hashtags\":[\"#wip\"],\"shareWith\":[\"" + bob + "\"],\"commentsOn\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.commentsOn").value(false)), "$.id");
        send(put("/api/drafts"), annS, "{\"id\":\"" + id + "\",\"title\":\"Better\",\"body\":\"More\"}")
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.title").value("Better")).andExpect(jsonPath("$.hashtags", empty()));
        send(get("/api/drafts"), annS, null).andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$", hasSize(1)));
        send(get("/api/drafts"), bobS, null).andExpect(jsonPath("$", hasSize(0)));
        send(delete("/api/drafts/" + id), bobS, null).andExpect(status().isNotFound());
        send(delete("/api/drafts/" + id), annS, null).andExpect(status().isNoContent());
    }

    @Test
    void aDraftKeepsItsFilesAndPostingItConsumesIt() throws Exception {
        String file = uploaded(annS);
        String id = read(send(put("/api/drafts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"mediaIds\":[\"" + file + "\"]}")
                .andExpect(jsonPath("$.media[0].id").value(file)), "$.id");
        send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"mediaIds\":[\"" + file + "\"],\"draftId\":\"" + id + "\"}").andExpect(status().isOk());
        send(get("/api/drafts/" + id), annS, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/media/" + file).cookie(bobS)).andExpect(status().isOk());   // the file went with the post
    }

    @Test
    void deletingADraftThrowsAwayItsUnpostedFiles() throws Exception {
        String file = uploaded(annS);
        String id = read(send(put("/api/drafts"), annS, "{\"title\":\"T\",\"mediaIds\":[\"" + file + "\"]}"), "$.id");
        send(delete("/api/drafts/" + id), annS, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/media/" + file).cookie(annS)).andExpect(status().isNotFound());
    }

    @Test
    void postingCanYarnItToPeopleYouPicked() throws Exception {
        send(post("/api/posts"), annS, "{\"title\":\"Shared one\",\"body\":\"B\",\"shareWith\":[\"" + bob + "\",\"nobody" + UUID.randomUUID().toString().substring(0, 6) + "\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.shared").value(1));
        send(get("/api/yarns/threads"), bobS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastBody", containsString("shared a post with you")));
    }

    @Test
    void deletingAPostRemovesItsFiles() throws Exception {
        String file = uploaded(annS);
        String id = read(send(post("/api/posts"), annS, "{\"title\":\"T\",\"body\":\"B\",\"mediaIds\":[\"" + file + "\"]}"), "$.id");
        send(delete("/api/posts/" + id), annS, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/media/" + file).cookie(annS)).andExpect(status().isNotFound());
    }
}
