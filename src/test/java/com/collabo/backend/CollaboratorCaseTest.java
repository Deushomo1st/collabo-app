package com.collabo.backend;

import com.collabo.backend.entity.CollaboratorCase;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.CollaboratorCaseRepository;
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

/** Flagging a quiet collaborator: the founder's clock, the response, pleas, the vote and the founder's cancel. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ccase;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class CollaboratorCaseTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CollaboratorCaseRepository cases;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat, dan;
    Cookie annS, bobS, catS, danS;
    String postId, url, casesUrl;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat); danS = signIn(dan);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        url = "/api/posts/" + postId + "/collaborators";
        casesUrl = "/api/posts/" + postId + "/cases";
        follow(annS, bob); follow(bobS, ann);
        follow(annS, cat); follow(catS, ann);
        for (String who : new String[]{bob, cat}) send(post(url), annS, "{\"username\":\"" + who + "\"}").andExpect(status().isOk());
        send(post(url + "/accept"), bobS, null).andExpect(status().isOk());
        send(post(url + "/accept"), catS, null).andExpect(status().isOk());
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

    /** Flags `target` as `by` and returns the case id. */
    private String flag(Cookie by, String target) throws Exception {
        String res = send(post(casesUrl), by, "{\"username\":\"" + target + "\",\"reason\":\"Silent for weeks\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING")).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.id");
    }

    /** Runs the clock out without waiting 48 hours. */
    private void expire(String id) {
        CollaboratorCase k = cases.findById(UUID.fromString(id)).orElseThrow();
        k.extend(-1000);
        cases.save(k);
    }

    private ResultActions listAs(Cookie who) throws Exception { return send(get(casesUrl), who, null); }

    private ResultActions about(Cookie who) throws Exception { return send(get("/api/posts/" + postId + "/wespace"), who, null); }

    @Test
    void theFounderSetsTheClockButNeverUnder48Hours() throws Exception {
        about(annS).andExpect(jsonPath("$.responseClockHours").value(72));
        send(put("/api/posts/" + postId + "/wespace/clock"), annS, "{\"hours\":47}").andExpect(status().isBadRequest());
        send(put("/api/posts/" + postId + "/wespace/clock"), bobS, "{\"hours\":96}").andExpect(status().isForbidden());
        send(put("/api/posts/" + postId + "/wespace/clock"), annS, "{\"hours\":96}").andExpect(status().isOk());
        about(bobS).andExpect(jsonPath("$.responseClockHours").value(96));
    }

    @Test
    void flaggingIsForCollaboratorsAboutActiveCollaboratorsOneAtATimeAndTheTargetCanAnswer() throws Exception {
        send(post(casesUrl), danS, "{\"username\":\"" + cat + "\",\"reason\":\"x\"}").andExpect(status().isNotFound());   // outsiders
        send(post(casesUrl), bobS, "{\"username\":\"" + bob + "\",\"reason\":\"x\"}").andExpect(status().isBadRequest());   // not yourself
        send(post(casesUrl), bobS, "{\"username\":\"" + cat + "\",\"reason\":\"  \"}").andExpect(status().isBadRequest());   // reason needed
        String id = flag(bobS, cat);
        send(post(casesUrl), annS, "{\"username\":\"" + cat + "\",\"reason\":\"x\"}").andExpect(status().isBadRequest());   // already flagged
        send(post(casesUrl + "/" + id + "/respond"), bobS, null).andExpect(status().isForbidden());
        send(post(casesUrl + "/" + id + "/respond"), catS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RESPONDED"));
        flag(bobS, cat);   // answered, so a fresh flag is allowed
    }

    @Test
    void aPleaBuysTwelveHoursOneAtATimeAndNotForYourselfOrByTheAccuser() throws Exception {
        String id = flag(annS, cat);
        send(post(casesUrl + "/" + id + "/plea"), catS, null).andExpect(status().isBadRequest());   // not for yourself
        send(post(casesUrl + "/" + id + "/plea"), annS, null).andExpect(status().isBadRequest());   // accuser
        send(post(casesUrl + "/" + id + "/plea"), bobS, null).andExpect(status().isOk()).andExpect(jsonPath("$.plea.by.username").value(bob));
        send(post(casesUrl + "/" + id + "/plea"), bobS, null).andExpect(status().isBadRequest());   // one standing, and one a week
    }

    @Test
    void whenTheClockRunsOutTheFounderAndTheOthersVoteAndAMajorityFreezes() throws Exception {
        String id = flag(annS, cat);
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"FREEZE\"}").andExpect(status().isBadRequest());   // clock still running
        expire(id);
        listAs(bobS).andExpect(jsonPath("$[0].state").value("VOTING")).andExpect(jsonPath("$[0].voters").value(2)).andExpect(jsonPath("$[0].canVote").value(true));
        listAs(catS).andExpect(jsonPath("$[0].canVote").value(false));
        send(post(casesUrl + "/" + id + "/vote"), catS, "{\"choice\":\"FREEZE\"}").andExpect(status().isForbidden());   // the accused does not vote
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"banana\"}").andExpect(status().isBadRequest());
        send(post(casesUrl + "/" + id + "/vote"), annS, "{\"choice\":\"FREEZE\"}").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("VOTING"));
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"FREEZE\"}").andExpect(status().isOk());
        about(annS).andExpect(jsonPath("$.seats[?(@.person.username=='" + cat + "')].state").value("FROZEN"));
        listAs(annS).andExpect(jsonPath("$[0].state").value("DECIDED"));
    }

    @Test
    void aSplitVoteWaitsAndChangingYourVoteCanCarryIt() throws Exception {
        String id = flag(bobS, cat);
        expire(id);
        send(post(casesUrl + "/" + id + "/vote"), annS, "{\"choice\":\"FREEZE\"}").andExpect(status().isOk());
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"DISBAND\"}").andExpect(status().isOk());
        listAs(annS).andExpect(jsonPath("$[0].state").value("VOTING")).andExpect(jsonPath("$[0].freezeVotes").value(1)).andExpect(jsonPath("$[0].disbandVotes").value(1));
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"FREEZE\"}").andExpect(status().isOk());   // bob changes his mind
        about(annS).andExpect(jsonPath("$.seats[?(@.person.username=='" + cat + "')].state").value("FROZEN"));
    }

    @Test
    void aMajorityToDisbandRemovesThemWithTheFlagReasonAndKeepsTheSpot() throws Exception {
        String id = flag(bobS, cat);
        expire(id);
        send(post(casesUrl + "/" + id + "/vote"), annS, "{\"choice\":\"DISBAND\"}").andExpect(status().isOk());
        send(post(casesUrl + "/" + id + "/vote"), bobS, "{\"choice\":\"DISBAND\"}").andExpect(status().isOk());
        about(annS).andExpect(jsonPath("$.seats[?(@.person.username=='" + cat + "')].state").value("DISBANDED"))
                .andExpect(jsonPath("$.seats[?(@.person.username=='" + cat + "')].reason").value("Silent for weeks"));
        send(post(url), annS, "{\"username\":\"" + cat + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INVITED"));   // the spot is still theirs
    }

    @Test
    void onlyTheFounderCancelsAndItWorksDuringTheVote() throws Exception {
        String id = flag(bobS, cat);
        send(post(casesUrl + "/" + id + "/cancel"), bobS, null).andExpect(status().isForbidden());
        expire(id);
        listAs(annS).andExpect(jsonPath("$[0].state").value("VOTING"));
        send(post(casesUrl + "/" + id + "/cancel"), annS, null).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
        send(post(casesUrl + "/" + id + "/vote"), annS, "{\"choice\":\"FREEZE\"}").andExpect(status().isBadRequest());   // ended
    }

    @Test
    void theFoundersOwnFreezeClosesAnOpenFlag() throws Exception {
        String id = flag(bobS, cat);
        send(post(url + "/" + cat + "/freeze"), annS, null).andExpect(status().isOk());
        listAs(annS).andExpect(jsonPath("$[0].state").value("DECIDED"));
        send(post(casesUrl + "/" + id + "/respond"), catS, null).andExpect(status().isNotFound());   // frozen people are out of this room
    }
}
