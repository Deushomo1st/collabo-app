package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.RemovalService;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notifications: what lands, the three filters, the pinned action-required class and how it clears itself. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class NotificationTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RemovalService removals;

    String ann, bob, dan;
    Cookie annS, bobS, danS;
    String postId, bobApp, space;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        bobApp = apply(bobS);
        apply(danS);
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
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"me\"}").andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private void accept() throws Exception {
        send(patch("/api/applications/" + bobApp), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
    }

    private void acceptAndForm() throws Exception {
        accept();
        send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().is2xxSuccessful());   // the acceptance is announced when the space forms
    }

    private void formAndJoin() throws Exception {
        accept();
        String body = send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        space = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
    }

    private String claim() throws Exception {
        String body = send(post("/api/spaces/" + space + "/payments"), annS, "{\"recipient\":\"" + bob + "\",\"amount\":50,\"currency\":\"NGN\",\"note\":\"x\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String flag() throws Exception {
        String body = send(post("/api/spaces/" + space + "/removals"), annS, "{\"username\":\"" + bob + "\",\"reason\":\"gone quiet\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions mine(Cookie who, String query) throws Exception { return send(get("/api/notifications" + query), who, null); }

    @Test
    void anAcceptanceLandsInSpacesAndReadingClearsTheCount() throws Exception {
        acceptAndForm();
        mine(bobS, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].bucket").value("SPACES"))
                .andExpect(jsonPath("$[0].actionRequired").value(false)).andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].link", containsString("/space.html?id=")));
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(1));
        String id = com.jayway.jsonpath.JsonPath.read(mine(bobS, "").andReturn().getResponse().getContentAsString(), "$[0].id");
        send(post("/api/notifications/" + id + "/read"), danS, null).andExpect(status().isNotFound());   // not theirs
        send(post("/api/notifications/" + id + "/read"), bobS, null).andExpect(status().isOk());
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(0));
        mine(annS, "").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aPaymentClaimIsPinnedForTheRecipientAndClearsWhenSettled() throws Exception {
        formAndJoin();
        String id = claim();
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(true)).andExpect(jsonPath("$[0].title", containsString("payment")));
        send(post("/api/spaces/" + space + "/payments/" + id + "/confirm"), bobS, null).andExpect(status().isOk());
        mine(bobS, "").andExpect(jsonPath("$[*].actionRequired", everyItem(is(false))));
        String second = claim();
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(true));
        send(post("/api/spaces/" + space + "/payments/" + second + "/cancel"), annS, null).andExpect(status().isOk());   // the payer withdraws
        mine(bobS, "").andExpect(jsonPath("$[*].actionRequired", everyItem(is(false))));
    }

    @Test
    void actionRequiredIsPinnedWhateverTheFilterAndTheFiltersSplitTheRest() throws Exception {
        formAndJoin();                 // bob: acceptance (spaces)
        claim();                       // bob: payment claim (spaces, action required, newest)
        send(get("/api/notifications?filter=activity"), bobS, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].actionRequired").value(true));
        send(get("/api/notifications?filter=personal"), bobS, null).andExpect(jsonPath("$", hasSize(1)));
        send(get("/api/notifications?filter=spaces"), bobS, null).andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].actionRequired").value(true));
        send(get("/api/notifications?filter=nonsense"), bobS, null).andExpect(status().isBadRequest());
    }

    @Test
    void aMilestoneCreditsEveryoneInTheRoom() throws Exception {
        formAndJoin();
        String body = send(post("/api/spaces/" + space + "/milestones"), annS, "{\"title\":\"First cut\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String mid = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(post("/api/spaces/" + space + "/milestones/" + mid + "/fulfil"), annS, "{\"note\":\"done\"}").andExpect(status().isOk());
        mine(bobS, "").andExpect(jsonPath("$[0].title", containsString("First cut"))).andExpect(jsonPath("$[0].bucket").value("SPACES"));
        mine(annS, "").andExpect(jsonPath("$[0].title", containsString("First cut")));
    }

    @Test
    void aFlagPinsForTheTargetUntilTheyRespondAndRemovalIsAnnounced() throws Exception {
        formAndJoin();
        String id = flag();
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(true)).andExpect(jsonPath("$[0].title", containsString("quiet")));
        send(post("/api/spaces/" + space + "/removals/" + id + "/respond"), bobS, null).andExpect(status().isOk());
        mine(bobS, "").andExpect(jsonPath("$[*].actionRequired", everyItem(is(false))));

        flag();
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(true));
        removals.settleDue(Instant.now().plusSeconds(3600L * 1000));
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(false)).andExpect(jsonPath("$[0].title", containsString("removed")));
        mine(bobS, "").andExpect(jsonPath("$[*].actionRequired", everyItem(is(false))));
    }

    @Test
    void formingTheSpaceTellsApplicantsWhoWereNotPickedThatTheWindowClosed() throws Exception {
        formAndJoin();
        mine(danS, "").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].title", containsString("closed")));
    }

    @Test
    void readAllClearsEverything() throws Exception {
        formAndJoin();
        claim();
        send(post("/api/notifications/read-all"), bobS, null).andExpect(status().isOk());
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(0));
        mine(bobS, "").andExpect(jsonPath("$[0].actionRequired").value(true));   // read is not resolved
    }
}
