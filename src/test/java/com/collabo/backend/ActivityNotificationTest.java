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

    @Test
    void aCommentTellsThePostsAuthorAndAReplyTellsTheCommentsOwnerButNeverYourself() throws Exception {
        String res = send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pid = com.jayway.jsonpath.JsonPath.read(res, "$.id");
        String c = send(post("/api/posts/" + pid + "/comments"), bobS, "{\"body\":\"Count me in\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cid = com.jayway.jsonpath.JsonPath.read(c, "$.id");
        notes(annS).andExpect(jsonPath("$[?(@.title=='New comment')].body").value(bob + " commented on \"Open call\"."));
        send(post("/api/posts/" + pid + "/comments"), annS, "{\"body\":\"My own\"}").andExpect(status().isOk());   // the author's own comment tells no one
        send(post("/api/posts/" + pid + "/comments"), catS, "{\"body\":\"Hi bob\",\"parentId\":\"" + cid + "\"}").andExpect(status().isOk());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='New reply')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.title=='New reply')].body").value(cat + " replied to your comment on \"Open call\"."));
        notes(annS).andExpect(jsonPath("$[?(@.title=='New comment')]", hasSize(2)));   // bob's and cat's, not her own
    }

    @Test
    void anApplicationTellsThePostsAuthor() throws Exception {
        String res = send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pid = com.jayway.jsonpath.JsonPath.read(res, "$.id");
        send(post("/api/posts/" + pid + "/applications"), bobS, "{\"statement\":\"I build things\"}").andExpect(status().isOk());
        notes(annS).andExpect(jsonPath("$[?(@.title=='New application')].body").value(bob + " applied to \"Open call\"."));
    }

    @Test
    void lookingAtWhatANotificationPointsToReadsIt() throws Exception {
        String res = send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pid = com.jayway.jsonpath.JsonPath.read(res, "$.id");
        send(post("/api/posts/" + pid + "/comments"), bobS, "{\"body\":\"Count me in\"}").andExpect(status().isOk());
        send(get("/api/notifications/unread-count"), annS, null).andExpect(jsonPath("$.count").value(1));
        send(post("/api/notifications/read-link?link=/HTML-pages/view-post.html?id=" + pid), annS, null).andExpect(status().isOk());
        send(get("/api/notifications/unread-count"), annS, null).andExpect(jsonPath("$.count").value(0));
        String t = send(post("/api/yarns/threads/myspace"), annS, "{\"username\":\"" + bob + "\",\"body\":\"hi\"}").andReturn().getResponse().getContentAsString();
        String thread = com.jayway.jsonpath.JsonPath.read(t, "$.id");
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(1));
        send(post("/api/yarns/threads/" + thread + "/read"), bobS, null).andExpect(status().is2xxSuccessful());   // opening the chat
        send(get("/api/notifications/unread-count"), bobS, null).andExpect(jsonPath("$.count").value(0));
    }

    private String id(ResultActions r) throws Exception { return com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"); }

    @Test
    void aLikeTellsTheAuthorOnceUnderActivityAndNeverAboutYourself() throws Exception {
        String pid = id(send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()));
        String cid = id(send(post("/api/posts/" + pid + "/comments"), bobS, "{\"body\":\"Count me in\"}").andExpect(status().isOk()));
        send(put("/api/posts/" + pid + "/like"), annS, null).andExpect(status().isOk());   // her own post tells no one
        send(put("/api/posts/" + pid + "/like"), bobS, null).andExpect(status().isOk());
        send(delete("/api/posts/" + pid + "/like"), bobS, null).andExpect(status().isOk());
        send(put("/api/posts/" + pid + "/like"), bobS, null).andExpect(status().isOk());   // like, unlike, like: still one line
        notes(annS).andExpect(jsonPath("$[?(@.body=='" + bob + " liked your post \"Open call\".')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.title=='New like')].bucket").value("ACTIVITY"));
        send(put("/api/posts/" + pid + "/comments/" + cid + "/like"), annS, null).andExpect(status().isOk());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='New like')].body").value(ann + " liked your comment on \"Open call\"."));
        send(put("/api/posts/" + pid + "/comments/" + cid + "/like"), bobS, null).andExpect(status().isOk());   // your own comment, too
        notes(bobS).andExpect(jsonPath("$[?(@.title=='New like')]", hasSize(1)));
    }

    @Test
    void withdrawingAnApplicationTellsTheAuthor() throws Exception {
        String pid = id(send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()));
        String aid = id(send(post("/api/posts/" + pid + "/applications"), bobS, "{\"statement\":\"I build things\"}").andExpect(status().isOk()));
        send(post("/api/applications/" + aid + "/withdraw"), bobS, null).andExpect(status().isOk());
        notes(annS).andExpect(jsonPath("$[?(@.title=='Application withdrawn')].body").value(bob + " withdrew their application to \"Open call\"."));
    }

    @Test
    void aCollaboratorRequestTellsTheInviteeAndTheAnswerTellsTheFounder() throws Exception {
        follow(annS, bob); follow(bobS, ann); follow(annS, cat); follow(catS, ann);
        String pid = id(send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()));
        send(post("/api/posts/" + pid + "/collaborators"), annS, "{\"username\":\"" + bob + "\"}").andExpect(status().isOk());
        send(post("/api/posts/" + pid + "/collaborators"), annS, "{\"username\":\"" + cat + "\"}").andExpect(status().isOk());
        notes(bobS).andExpect(jsonPath("$[?(@.title=='You were asked to collaborate')].body").value(ann + " asked you to co-found \"Open call\"."));
        send(post("/api/posts/" + pid + "/collaborators/accept"), bobS, null).andExpect(status().is2xxSuccessful());
        send(post("/api/posts/" + pid + "/collaborators/decline"), catS, null).andExpect(status().is2xxSuccessful());
        notes(annS).andExpect(jsonPath("$[?(@.title=='Collaborator accepted')].body").value(bob + " accepted your request to co-found \"Open call\"."))
                .andExpect(jsonPath("$[?(@.title=='Collaborator declined')].body").value(cat + " declined your request to co-found \"Open call\"."));
        send(delete("/api/posts/" + pid + "/collaborators/" + bob), bobS, null).andExpect(status().is2xxSuccessful());   // stepping down
        notes(annS).andExpect(jsonPath("$[?(@.title=='A collaborator stepped down')]", hasSize(1)));
    }

    @Test
    void aDisagreementTellsTheReviewerWhoAgreed() throws Exception {
        follow(annS, bob); follow(bobS, ann);
        String pid = id(send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()));
        send(post("/api/posts/" + pid + "/collaborators"), annS, "{\"username\":\"" + bob + "\"}").andExpect(status().isOk());
        send(post("/api/posts/" + pid + "/collaborators/accept"), bobS, null).andExpect(status().is2xxSuccessful());
        String aid = id(send(post("/api/posts/" + pid + "/applications"), catS, "{\"statement\":\"I build things\"}").andExpect(status().isOk()));
        send(patch("/api/applications/" + aid), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        send(put("/api/applications/" + aid + "/reaction"), bobS, "{\"reaction\":\"DISAGREE\"}").andExpect(status().isOk());
        notes(annS).andExpect(jsonPath("$[?(@.title=='Disagreed with your selection')].body").value(bob + " disagreed with your selection of " + cat + "."));
        notes(bobS).andExpect(jsonPath("$[?(@.title=='Disagreed with your selection')]", hasSize(0)));
    }

    @Test
    void removingACollaboratorTellsThemAndTheOthersWhoRemovedWhom() throws Exception {
        follow(annS, bob); follow(bobS, ann); follow(annS, cat); follow(catS, ann);
        String pid = id(send(post("/api/posts"), annS, "{\"title\":\"Open call\",\"body\":\"Who is in?\"}").andExpect(status().isOk()));
        for (String who : new String[] {bob, cat}) send(post("/api/posts/" + pid + "/collaborators"), annS, "{\"username\":\"" + who + "\"}").andExpect(status().isOk());
        send(post("/api/posts/" + pid + "/collaborators/accept"), bobS, null).andExpect(status().is2xxSuccessful());
        send(post("/api/posts/" + pid + "/collaborators/accept"), catS, null).andExpect(status().is2xxSuccessful());
        send(delete("/api/posts/" + pid + "/collaborators/" + cat), annS, null).andExpect(status().is2xxSuccessful());
        notes(catS).andExpect(jsonPath("$[?(@.title=='You were removed as a collaborator')].body").value(ann + " removed you from \"Open call\"."));
        notes(bobS).andExpect(jsonPath("$[?(@.title=='Someone was removed')].body").value(ann + " removed " + cat + " from \"Open call\"."));
    }
}
