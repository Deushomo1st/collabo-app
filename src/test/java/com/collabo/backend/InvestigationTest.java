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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Investigations: a member reports any Yarnspace they sit in, the admin assigns a moderator, and the members are told. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:investigations;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class InvestigationTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, dan;
    Cookie annS, bobS, danS;
    String mySpace, workspace, mod;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); danS = signIn(dan);
        mySpace = JsonPath.read(send(post("/api/yarns/threads/myspace"), annS, "{\"username\":\"" + bob + "\",\"body\":\"hey\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String post = JsonPath.read(send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString(), "$.id");
        String app = send(post("/api/posts/" + post + "/applications"), bobS, "{\"statement\":\"me\"}").andReturn().getResponse().getContentAsString();
        send(patch("/api/applications/" + JsonPath.read(app, "$.id")), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String space = JsonPath.read(send(post("/api/posts/" + post + "/space"), annS, "{}").andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
        workspace = JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
        mod = JsonPath.read(admin(post("/api/admin/moderators"), "{\"name\":\"Mo\",\"email\":\"mo" + tag + "@t.dev\",\"password\":\"long-enough-pw\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
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

    private ResultActions admin(MockHttpServletRequestBuilder req, String json) throws Exception {
        req.header("X-Admin-Key", "test-admin-key");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private String report(String thread, Cookie by, String reason) throws Exception {
        return JsonPath.read(send(post("/api/yarns/threads/" + thread + "/report"), by, "{\"reason\":\"" + reason + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void aMemberReportsAnyTierAndItLandsInTheAdminQueue() throws Exception {
        String dm = report(mySpace, bobS, "they keep sending threats");
        String ws = report(workspace, annS, "off-platform payment pressure");
        String active = "$[?(@.id=='%s')]";
        admin(get("/api/admin/investigations"), null).andExpect(status().isOk())
                .andExpect(jsonPath(active.formatted(dm) + ".tier", contains("MYSPACE")))
                .andExpect(jsonPath(active.formatted(dm) + ".reporter", contains(bob)))
                .andExpect(jsonPath(active.formatted(dm) + ".status", contains("OPEN")))
                .andExpect(jsonPath(active.formatted(dm) + ".title", anyOf(contains(ann + " & " + bob), contains(bob + " & " + ann))))
                .andExpect(jsonPath(active.formatted(ws) + ".tier", contains("WORKSPACE")))
                .andExpect(jsonPath(active.formatted(ws) + ".kind", contains("REPORT")));
    }

    @Test
    void onlyAMemberCanReportAndWithAReasonAndOnlyOnce() throws Exception {
        send(post("/api/yarns/threads/" + mySpace + "/report"), danS, "{\"reason\":\"x\"}").andExpect(status().isNotFound());   // not in it
        send(post("/api/yarns/threads/" + UUID.randomUUID() + "/report"), danS, "{\"reason\":\"x\"}").andExpect(status().isNotFound());
        send(post("/api/yarns/threads/" + mySpace + "/report"), bobS, "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        report(mySpace, bobS, "first");
        send(post("/api/yarns/threads/" + mySpace + "/report"), bobS, "{\"reason\":\"again\"}").andExpect(status().isBadRequest());
        report(mySpace, annS, "the other side can report too");
    }

    @Test
    void assigningTellsEveryMemberAModeratorWasIntroduced() throws Exception {
        String inv = report(workspace, annS, "something is off");
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{\"moderatorId\":\"" + mod + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED")).andExpect(jsonPath("$.moderator").value("Mo"));
        for (Cookie member : new Cookie[]{annS, bobS})
            send(get("/api/notifications"), member, null).andExpect(jsonPath("$[0].title").value("A moderator was introduced"))
                    .andExpect(jsonPath("$[0].body", containsString("Mo")));
        send(get("/api/notifications"), danS, null).andExpect(jsonPath("$[?(@.title=='A moderator was introduced')]", hasSize(0)));
        admin(get("/api/admin/investigations/" + inv), null).andExpect(jsonPath("$.members", containsInAnyOrder(ann, bob)))
                .andExpect(jsonPath("$.appeal").doesNotExist());
    }

    @Test
    void onlyAnActiveRealModeratorCanBeAssigned() throws Exception {
        String inv = report(mySpace, bobS, "x");
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{\"moderatorId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isNotFound());
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{}").andExpect(status().isNotFound());
        admin(patch("/api/admin/moderators/" + mod + "/active"), "{\"active\":false}").andExpect(status().isOk());
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{\"moderatorId\":\"" + mod + "\"}").andExpect(status().isBadRequest());
        admin(post("/api/admin/investigations/" + inv + "/decide"), "{\"outcome\":\"DROPS\"}").andExpect(status().isBadRequest());   // a report has no badge to decide
    }

    @Test
    void theQueueIsForTheAdminKeyAlone() throws Exception {
        mvc.perform(get("/api/admin/investigations")).andExpect(status().isForbidden());
        send(get("/api/admin/investigations"), annS, null).andExpect(status().isForbidden());
    }
}
