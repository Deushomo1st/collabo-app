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

/** Payment records: who claims, who confirms or cancels, and the hold on the room while a claim is open. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payments;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class PaymentRecordTest {

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


    private String url() { return "/api/spaces/" + space + "/payments"; }

    private ResultActions claim(Cookie who, String to, String amount) throws Exception {
        return send(post(url()), who, "{\"recipient\":\"" + to + "\",\"amount\":" + amount + ",\"currency\":\"NGN\",\"note\":\"design work\"}");
    }

    private String claimId(Cookie who, String to) throws Exception {
        String body = claim(who, to, "50.00").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private ResultActions say(Cookie who, String thread) throws Exception {
        return send(post("/api/yarns/threads/" + thread + "/yarns"), who, "{\"body\":\"hello\"}");
    }

    private String room() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    @Test
    void thePayerClaimsAndOnlyPeopleInTheRoomCanSeeIt() throws Exception {
        formAndJoin();
        claim(annS, bob, "50.00").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLAIMED")).andExpect(jsonPath("$.amount").value(50.0))
                .andExpect(jsonPath("$.payer.username").value(ann)).andExpect(jsonPath("$.recipient.username").value(bob))
                .andExpect(jsonPath("$.currency").value("NGN")).andExpect(jsonPath("$.note").value("design work"));
        send(get(url()), bobS, null).andExpect(jsonPath("$", hasSize(1)));
        decide(catApp, "ACCEPT").andExpect(status().isOk());
        send(get(url()), catS, null).andExpect(status().isNotFound());   // accepted but not in the room
        send(get(url()), danS, null).andExpect(status().isNotFound());
        send(get("/api/yarns/threads/" + room() + "/yarns"), bobS, null).andExpect(jsonPath("$[0].body", containsString("payment claim")));
    }

    @Test
    void theClaimNeedsThePermissionARealRecipientAndSensibleFigures() throws Exception {
        formAndJoin();
        claim(bobS, ann, "10").andExpect(status().isForbidden());
        claim(annS, ann, "10").andExpect(status().isBadRequest());   // not to yourself
        claim(annS, cat, "10").andExpect(status().isBadRequest());   // not in the room
        claim(annS, "nobody" + ann, "10").andExpect(status().isBadRequest());
        claim(annS, bob, "0").andExpect(status().isBadRequest());
        claim(annS, bob, "-5").andExpect(status().isBadRequest());
        claim(annS, bob, "1.005").andExpect(status().isBadRequest());
        send(post(url()), annS, "{\"recipient\":\"" + bob + "\",\"amount\":5,\"currency\":\"x\"}").andExpect(status().isBadRequest());
        send(patch("/api/spaces/" + space + "/members/" + bob), annS, "{\"permissions\":[\"LOG_PAYMENTS\"]}").andExpect(status().isOk());
        claim(bobS, ann, "10").andExpect(status().isOk());
    }

    @Test
    void onlyTheRecipientConfirmsAndOnlyOnce() throws Exception {
        formAndJoin();
        String id = claimId(annS, bob);
        send(post(url() + "/" + id + "/confirm"), annS, null).andExpect(status().isForbidden());
        send(post(url() + "/" + id + "/confirm"), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));
        send(post(url() + "/" + id + "/confirm"), bobS, null).andExpect(status().isBadRequest());
        send(post(url() + "/" + id + "/cancel"), annS, null).andExpect(status().isBadRequest());
    }

    @Test
    void onlyThePayerCancelsAndACancelledClaimCannotBeConfirmed() throws Exception {
        formAndJoin();
        String id = claimId(annS, bob);
        send(post(url() + "/" + id + "/cancel"), bobS, null).andExpect(status().isForbidden());
        send(post(url() + "/" + id + "/cancel"), annS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
        send(post(url() + "/" + id + "/confirm"), bobS, null).andExpect(status().isBadRequest());
        send(post(url() + "/" + id + "/cancel"), danS, null).andExpect(status().isNotFound());
    }

    @Test
    void anOpenClaimHoldsTheRoomUntilItIsConfirmedOrCancelled() throws Exception {
        formAndJoin();
        String thread = room();
        say(bobS, thread).andExpect(status().isCreated());
        String first = claimId(annS, bob);
        say(bobS, thread).andExpect(status().isConflict());
        say(annS, thread).andExpect(status().isConflict());
        send(post(url() + "/" + first + "/confirm"), bobS, null).andExpect(status().isOk());
        say(bobS, thread).andExpect(status().isCreated());
        String second = claimId(annS, bob);
        say(bobS, thread).andExpect(status().isConflict());
        send(post(url() + "/" + second + "/cancel"), annS, null).andExpect(status().isOk());   // a silent recipient cannot hold the room
        say(bobS, thread).andExpect(status().isCreated());
    }
}
