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

/** Space settings: the response clock, pleas on or off, the name, and the room hearing about every change. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settings;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class SpaceSettingsTest {

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
        bobApp = apply(bobS);
        catApp = apply(catS);
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

    private String apply(Cookie who) throws Exception {
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"me\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions decide(String appId, String decision) throws Exception {
        return send(patch("/api/applications/" + appId), annS, "{\"decision\":\"" + decision + "\"}");
    }

    private String form(String json) throws Exception {
        decide(bobApp, "ACCEPT").andExpect(status().isOk());
        String body = send(post("/api/posts/" + postId + "/space"), annS, json).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String space;

    private void formAndJoin() throws Exception {
        space = form("{}");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
    }


    private String settings() { return "/api/spaces/" + space + "/settings"; }

    private String room() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    private ResultActions history() throws Exception { return send(get("/api/yarns/threads/" + room() + "/yarns"), annS, null); }

    @Test
    void theClockDefaultsAndIsVisibleToAcceptedApplicantsBeforeTheyJoin() throws Exception {
        space = form("{}");
        send(get("/api/spaces/" + space), bobS, null).andExpect(jsonPath("$.role").value("APPLICANT"))
                .andExpect(jsonPath("$.responseClockHours").value(72)).andExpect(jsonPath("$.pleasEnabled").value(true));
    }

    @Test
    void theFounderPicksTheClockAndPleasWhenFormingWithinTheBounds() throws Exception {
        decide(bobApp, "ACCEPT").andExpect(status().isOk());
        send(post("/api/posts/" + postId + "/space"), annS, "{\"responseClockHours\":47}").andExpect(status().isBadRequest());
        send(post("/api/posts/" + postId + "/space"), annS, "{\"responseClockHours\":8761}").andExpect(status().isBadRequest());
        send(post("/api/posts/" + postId + "/space"), annS, "{\"responseClockHours\":168,\"pleasEnabled\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.responseClockHours").value(168)).andExpect(jsonPath("$.pleasEnabled").value(false));
    }

    @Test
    void theOwnerChangesSettingsAndEveryRealChangeIsAnnouncedInTheRoom() throws Exception {
        formAndJoin();
        int before = com.jayway.jsonpath.JsonPath.<java.util.List<?>>read(history().andReturn().getResponse().getContentAsString(), "$").size();
        send(patch(settings()), annS, "{\"responseClockHours\":96,\"pleasEnabled\":false,\"name\":\"Synth crew\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.responseClockHours").value(96)).andExpect(jsonPath("$.pleasEnabled").value(false)).andExpect(jsonPath("$.name").value("Synth crew"));
        history().andExpect(jsonPath("$", hasSize(before + 3)))
                .andExpect(jsonPath("$[*].body", hasItems(containsString("from 72 to 96 hours"), containsString("switched pleas off"), containsString("renamed the space to Synth crew"))));
        send(get("/api/yarns/threads?tier=WORKSPACE"), bobS, null).andExpect(jsonPath("$[0].name").value("Synth crew"));
        send(patch(settings()), annS, "{\"responseClockHours\":96,\"pleasEnabled\":false,\"name\":\"Synth crew\"}").andExpect(status().isOk());
        history().andExpect(jsonPath("$", hasSize(before + 3)));   // nothing changed, nothing announced
    }

    @Test
    void theFloorAndTheNameAreEnforcedAndNothingIsHalfApplied() throws Exception {
        formAndJoin();
        send(patch(settings()), annS, "{\"responseClockHours\":47}").andExpect(status().isBadRequest());
        send(patch(settings()), annS, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        send(patch(settings()), annS, "{\"name\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest());
        send(patch(settings()), annS, "{\"name\":\"New name\",\"responseClockHours\":10}").andExpect(status().isBadRequest());
        send(get("/api/spaces/" + space), annS, null).andExpect(jsonPath("$.name").value("Idea")).andExpect(jsonPath("$.responseClockHours").value(72));
    }

    @Test
    void onlyThoseWithEditSettingsMayChangeThemAndTheyNeedAWarningToGetIt() throws Exception {
        formAndJoin();
        decide(catApp, "ACCEPT").andExpect(status().isOk());
        send(patch(settings()), bobS, "{\"pleasEnabled\":false}").andExpect(status().isForbidden());
        send(patch(settings()), catS, "{\"pleasEnabled\":false}").andExpect(status().isForbidden());   // accepted, not in the room
        send(patch(settings()), danS, "{\"pleasEnabled\":false}").andExpect(status().isNotFound());
        send(patch("/api/spaces/" + space + "/members/" + bob), annS, "{\"permissions\":[\"EDIT_SETTINGS\"],\"confirm\":true}").andExpect(status().isOk());
        send(patch(settings()), bobS, "{\"pleasEnabled\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.pleasEnabled").value(false));
    }
}
