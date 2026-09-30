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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Credential entries and who may see them, over HTTP against an in-memory H2. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:credentials;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class CredentialApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String owner, follower, followed, mutual, stranger;
    Map<String, Cookie> session;

    @BeforeEach
    void accounts() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        owner = "own" + tag; follower = "fol" + tag; followed = "fdd" + tag; mutual = "mut" + tag; stranger = "str" + tag;
        session = new java.util.HashMap<>();
        for (String n : new String[]{owner, follower, followed, mutual, stranger}) session.put(n, signIn(n));
        follow(follower, owner);     // follower follows owner
        follow(owner, followed);     // owner follows followed
        follow(mutual, owner); follow(owner, mutual);
        seed(owner, "space-1", "Formed the Lagos design space");
        seed(owner, "space-2", "Formed the Abuja build space");
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

    private void follow(String who, String target) throws Exception {
        mvc.perform(put("/api/users/" + target + "/follow").cookie(XSRF, session.get(who)).header("X-XSRF-TOKEN", "t")).andExpect(status().isOk());
    }

    private ResultActions seedRaw(String username, String sourceId, String title, String key) throws Exception {
        return mvc.perform(post("/api/admin/credentials").header("X-Admin-Key", key).contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"kind\":\"SPACE_FORMED\",\"title\":\"" + title
                        + "\",\"sourceType\":\"SPACE\",\"sourceId\":\"" + sourceId + "\"}"));
    }

    private void seed(String username, String sourceId, String title) throws Exception {
        seedRaw(username, sourceId, title, "test-admin-key").andExpect(status().isCreated());
    }

    private void setPrivacy(String value) throws Exception {
        mvc.perform(patch("/api/users/me").cookie(XSRF, session.get(owner)).header("X-XSRF-TOKEN", "t")
                .contentType("application/json").content("{\"credentialsPrivacy\":\"" + value + "\"}")).andExpect(status().isOk());
    }

    private void expectSees(String viewer, boolean sees) throws Exception {
        ResultActions r = mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(sees));
        r.andExpect(jsonPath("$.entries.length()").value(sees ? 2 : 0));
    }

    @Test
    void everyPrivacySettingShowsCredentialsToTheRightPeopleOnly() throws Exception {
        //                      owner  follower followed mutual stranger
        String[] who =          {owner, follower, followed, mutual, stranger};
        Object[][] matrix = {
                {"EVERYONE",   true, true,  true,  true,  true},
                {"FOLLOWERS",  true, true,  false, true,  false},
                {"FOLLOWING",  true, false, true,  true,  false},
                {"MUTUAL",     true, false, false, true,  false},
                {"APPLICANTS", true, false, false, false, false}};
        for (Object[] row : matrix) {
            setPrivacy((String) row[0]);
            for (int i = 0; i < who.length; i++) expectSees(who[i], (Boolean) row[i + 1]);
        }
    }

    @Test
    void aHiddenSectionSaysNothingAboutHowManyEntriesExist() throws Exception {
        setPrivacy("MUTUAL");
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(stranger)))
                .andExpect(jsonPath("$.visible").value(false)).andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.count").doesNotExist()).andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void entriesComeNewestFirstAndOwnersSeeThemAlways() throws Exception {
        mvc.perform(post("/api/admin/credentials").header("X-Admin-Key", "test-admin-key").contentType("application/json")
                .content("{\"username\":\"" + owner + "\",\"kind\":\"MILESTONE_CREDITED\",\"title\":\"Shipped v1\",\"detail\":\"Landing page\","
                        + "\"sourceType\":\"MILESTONE\",\"sourceId\":\"m-1\",\"occurredAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated());
        setPrivacy("APPLICANTS");
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(owner)))
                .andExpect(jsonPath("$.entries.length()").value(3)).andExpect(jsonPath("$.entries[0].title").value("Shipped v1"))
                .andExpect(jsonPath("$.entries[0].kind").value("MILESTONE_CREDITED")).andExpect(jsonPath("$.entries[0].featured").value(false));
    }

    @Test
    void recordingTheSameEventTwiceMakesOneEntry() throws Exception {
        seedRaw(owner, "space-1", "Formed the Lagos design space", "test-admin-key").andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
        expectSees(owner, true);   // still exactly two
    }

    @Test
    void seedingNeedsTheAdminKeyAndValidInput() throws Exception {
        seedRaw(owner, "x", "T", "wrong-key").andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/credentials").contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        seedRaw("nobody-" + owner, "x", "T", "test-admin-key").andExpect(status().isNotFound());
        seedRaw(owner, "x", "", "test-admin-key").andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/credentials").header("X-Admin-Key", "test-admin-key").contentType("application/json")
                .content("{\"username\":\"" + owner + "\",\"kind\":\"SELF_MADE\",\"title\":\"T\",\"sourceType\":\"S\",\"sourceId\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    private String firstEntryId() throws Exception {
        String json = mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(owner))).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.entries[0].id");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String who) {
        return b.cookie(XSRF, session.get(who)).header("X-XSRF-TOKEN", "t").contentType("application/json");
    }

    @Test
    void ownersPromoteEntriesToFeatsAndTakeThemBack() throws Exception {
        String id = firstEntryId();
        mvc.perform(as(patch("/api/credentials/" + id), owner).content("{\"featured\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(stranger)))
                .andExpect(jsonPath("$.entries[?(@.featured==true)]").value(org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(as(patch("/api/credentials/" + id), owner).content("{\"featured\":false}")).andExpect(jsonPath("$.featured").value(false));
        mvc.perform(as(patch("/api/credentials/" + id), owner).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void shippedLinksAttachRemoveAndFlipTheNothingShippedTag() throws Exception {
        String id = firstEntryId();
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(owner))).andExpect(jsonPath("$.entries[0].nothingShipped").value(true));

        String added = mvc.perform(as(post("/api/credentials/" + id + "/shipped"), owner).content("{\"title\":\"Live site\",\"url\":\"https://ship.dev\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nothingShipped").value(false))
                .andExpect(jsonPath("$.shipped[0].title").value("Live site")).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(stranger)))
                .andExpect(jsonPath("$.entries[0].shipped[0].url").value("https://ship.dev"));

        String linkId = com.jayway.jsonpath.JsonPath.read(added, "$.shipped[0].id");
        mvc.perform(as(delete("/api/credentials/" + id + "/shipped/" + linkId), owner)).andExpect(status().isOk()).andExpect(jsonPath("$.nothingShipped").value(true));
        mvc.perform(as(delete("/api/credentials/" + id + "/shipped/" + linkId), owner)).andExpect(status().isOk());   // already gone: quiet
    }

    @Test
    void badShippedLinksAreRejectedAndThereIsACap() throws Exception {
        String id = firstEntryId();
        mvc.perform(as(post("/api/credentials/" + id + "/shipped"), owner).content("{\"title\":\"x\",\"url\":\"javascript:alert(1)\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/credentials/" + id + "/shipped"), owner).content("{\"title\":\"\",\"url\":\"https://a.dev\"}")).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(as(post("/api/credentials/" + id + "/shipped"), owner).content("{\"title\":\"L" + i + "\",\"url\":\"https://a.dev/" + i + "\"}")).andExpect(status().isOk());
        }
        mvc.perform(as(post("/api/credentials/" + id + "/shipped"), owner).content("{\"title\":\"6th\",\"url\":\"https://a.dev/6\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCanChangeAnEntryAndOthersJustSeeNotFound() throws Exception {
        String id = firstEntryId();
        mvc.perform(as(patch("/api/credentials/" + id), stranger).content("{\"featured\":true}")).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/credentials/" + id + "/shipped"), follower).content("{\"title\":\"x\",\"url\":\"https://a.dev\"}")).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/credentials/" + id + "/shipped/" + UUID.randomUUID()), stranger)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/credentials/" + UUID.randomUUID()).cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"featured\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + owner + "/credentials").cookie(session.get(owner))).andExpect(jsonPath("$.entries[0].featured").value(false));
    }

    @Test
    void signedOutVisitorsAndUnknownUsersAreRefused() throws Exception {
        mvc.perform(get("/api/users/" + owner + "/credentials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/nobody-" + owner + "/credentials").cookie(session.get(owner))).andExpect(status().isNotFound());
    }
}
