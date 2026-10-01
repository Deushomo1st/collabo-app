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

/** Workspace tasks: only the team sees them, assignees must be in the room, and the room's chat is told. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tasks;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class TaskApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, cat;
    Cookie annS, bobS, catS;
    String postId, space;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; cat = "cat" + tag;
        annS = signIn(ann); bobS = signIn(bob); catS = signIn(cat);
        String body = send(post("/api/posts"), annS, "{\"title\":\"Idea\",\"body\":\"We need a designer.\"}").andReturn().getResponse().getContentAsString();
        postId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String bobApp = apply(bobS);
        apply(catS);
        send(patch("/api/applications/" + bobApp), annS, "{\"decision\":\"ACCEPT\"}").andExpect(status().isOk());
        String made = send(post("/api/posts/" + postId + "/space"), annS, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        space = com.jayway.jsonpath.JsonPath.read(made, "$.id");
        send(post("/api/spaces/" + space + "/join"), bobS, null).andExpect(status().isOk());
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
        String body = send(post("/api/posts/" + postId + "/applications"), who, "{\"statement\":\"me\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String url() { return "/api/spaces/" + space + "/tasks"; }

    @Test
    void theTeamAddsAssignsAndFinishesAndTheRoomIsTold() throws Exception {
        String body = send(post(url()), bobS, "{\"title\":\"Draw the logo\",\"assignee\":\"" + bob + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO")).andExpect(jsonPath("$.assignee.username").value(bob)).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        send(patch(url() + "/" + id), annS, "{\"status\":\"DOING\"}").andExpect(jsonPath("$.status").value("DOING"));
        send(patch(url() + "/" + id), annS, "{\"status\":\"DONE\"}").andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.doneAt").isNotEmpty());
        send(get(url()), annS, null).andExpect(jsonPath("$", hasSize(1)));
        String thread = com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
        send(get("/api/yarns/threads/" + thread + "/yarns"), bobS, null)
                .andExpect(jsonPath("$[*].body", hasItems(containsString("added a task: Draw the logo"), containsString("finished: Draw the logo"))));
        send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andExpect(jsonPath("$[0].postId").value(postId));
    }

    @Test
    void outsidersAndMerelyAcceptedApplicantsDoNotSeeTasksAndAssigneesMustBeInTheRoom() throws Exception {
        send(get(url()), catS, null).andExpect(status().isNotFound());
        send(post(url()), catS, "{\"title\":\"x\"}").andExpect(status().isNotFound());
        send(post(url()), annS, "{\"title\":\"x\",\"assignee\":\"" + cat + "\"}").andExpect(status().isBadRequest());
        send(post(url()), annS, "{\"title\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyWhoeverAddedItOrTheOwnerRemovesATask() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(send(post(url()), annS, "{\"title\":\"Owner's task\"}").andReturn().getResponse().getContentAsString(), "$.id");
        send(delete(url() + "/" + id), bobS, null).andExpect(status().isForbidden());
        String bobs = com.jayway.jsonpath.JsonPath.read(send(post(url()), bobS, "{\"title\":\"Bob's task\"}").andReturn().getResponse().getContentAsString(), "$.id");
        send(delete(url() + "/" + bobs), annS, null).andExpect(status().isOk());
    }

    @Test
    void onlyTheOwnerOrWhoeverHoldsPostInRoomPinsAndTheTeamReadsThePins() throws Exception {
        String thread = com.jayway.jsonpath.JsonPath.read(send(get("/api/yarns/threads?tier=WORKSPACE"), annS, null).andReturn().getResponse().getContentAsString(), "$[0].id");
        String yarn = com.jayway.jsonpath.JsonPath.read(send(post("/api/yarns/threads/" + thread + "/yarns"), bobS, "{\"body\":\"Demo is Friday\"}").andReturn().getResponse().getContentAsString(), "$.id");
        String pins = "/api/spaces/" + space + "/pins/";
        send(put(pins + yarn), bobS, null).andExpect(status().isForbidden());                       // a plain member may not
        send(put(pins + yarn), catS, null).andExpect(status().isNotFound());                        // an outsider does not even see the space
        send(put(pins + yarn), annS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space + "/pins"), bobS, null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].body").value("Demo is Friday")).andExpect(jsonPath("$[0].pinned").value(true));
        send(get("/api/yarns/threads/" + thread + "/yarns"), bobS, null).andExpect(jsonPath("$[?(@.id==\"" + yarn + "\")].pinned", contains(true)));
        send(delete(pins + yarn), annS, null).andExpect(status().isOk());
        send(get("/api/spaces/" + space + "/pins"), annS, null).andExpect(jsonPath("$", hasSize(0)));
    }
}
