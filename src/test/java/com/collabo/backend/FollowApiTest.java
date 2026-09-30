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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Follow rules over HTTP, against an in-memory H2. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:follows;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class FollowApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ada, bo, cy;
    Cookie adaS, boS, cyS;

    @BeforeEach
    void accounts() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ada = "ada" + tag; bo = "bo" + tag; cy = "cy" + tag;
        adaS = signIn(ada); boS = signIn(bo); cyS = signIn(cy);
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

    private ResultActions follow(Cookie who, String target) throws Exception {
        return mvc.perform(put("/api/users/" + target + "/follow").cookie(XSRF, who).header("X-XSRF-TOKEN", "t"));
    }

    private ResultActions unfollow(Cookie who, String target) throws Exception {
        return mvc.perform(delete("/api/users/" + target + "/follow").cookie(XSRF, who).header("X-XSRF-TOKEN", "t"));
    }

    private ResultActions block(Cookie who, String target) throws Exception {
        UUID id = users.findByUsername(target).orElseThrow().getId();
        return mvc.perform(put("/api/yarns/blocks/" + id).cookie(XSRF, who).header("X-XSRF-TOKEN", "t"));
    }

    @Test
    void followingUpdatesCountsAndBothSidesSeeTheRelationship() throws Exception {
        follow(adaS, bo).andExpect(status().isOk()).andExpect(jsonPath("$.follow.iFollow").value(true))
                .andExpect(jsonPath("$.follow.followers").value(1)).andExpect(jsonPath("$.follow.canFollow").value(true));
        follow(boS, ada).andExpect(status().isOk());

        mvc.perform(get("/api/users/" + ada).cookie(boS)).andExpect(jsonPath("$.follow.followsMe").value(true))
                .andExpect(jsonPath("$.follow.iFollow").value(true)).andExpect(jsonPath("$.follow.following").value(1));
        mvc.perform(get("/api/users/" + bo + "/followers").cookie(cyS)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value(ada));
        mvc.perform(get("/api/users/" + ada + "/following").cookie(cyS)).andExpect(jsonPath("$[0].username").value(bo));
    }

    @Test
    void repeatingAFollowChangesNothingAndUnfollowIsSilent() throws Exception {
        follow(adaS, bo); follow(adaS, bo).andExpect(status().isOk()).andExpect(jsonPath("$.follow.followers").value(1));
        unfollow(adaS, bo).andExpect(status().isOk()).andExpect(jsonPath("$.follow.followers").value(0));
        unfollow(adaS, bo).andExpect(status().isOk());
        unfollow(adaS, cy).andExpect(status().isOk());   // never followed
    }

    @Test
    void youCantFollowYourselfOrANobody() throws Exception {
        follow(adaS, ada).andExpect(status().isBadRequest());
        follow(adaS, "nobody-" + ada).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/" + bo + "/follow").cookie(XSRF).header("X-XSRF-TOKEN", "t")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").cookie(adaS)).andExpect(jsonPath("$.follow.canFollow").value(false));
    }

    @Test
    void aBlockEndsFollowingBothWaysAndHidesTheFollowButtonWithoutSayingWhy() throws Exception {
        follow(adaS, bo); follow(boS, ada);
        block(adaS, bo).andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/users/" + ada).cookie(boS)).andExpect(jsonPath("$.follow.canFollow").value(false))
                .andExpect(jsonPath("$.follow.iFollow").value(false)).andExpect(jsonPath("$.follow.followers").value(0));
        mvc.perform(get("/api/users/" + bo).cookie(adaS)).andExpect(jsonPath("$.follow.canFollow").value(false));

        follow(boS, ada).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("You can't follow this person."));
        follow(adaS, bo).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("You can't follow this person."));
    }

    @Test
    void listsLeaveOutPeopleWhoBlockedTheViewer() throws Exception {
        follow(boS, cy); follow(adaS, cy);
        block(boS, ada);   // bo blocked ada, so ada shouldn't see bo among cy's followers
        mvc.perform(get("/api/users/" + cy + "/followers").cookie(adaS)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value(ada));
        mvc.perform(get("/api/users/" + cy + "/followers").cookie(cyS)).andExpect(jsonPath("$.length()").value(2));
    }
}
