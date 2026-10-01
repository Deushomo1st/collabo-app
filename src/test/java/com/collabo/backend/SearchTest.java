package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Search: people by username, ideas newest or most viewed, and a view counted when someone else opens a post. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:searchtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class SearchTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private Cookie signIn(String username) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private String idea(Cookie s, String title) throws Exception {
        String res = mvc.perform(post("/api/posts").cookie(XSRF, s).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"title\":\"" + title + "\",\"body\":\"A body long enough to count\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    @Test
    void peopleAreFoundByNameViewsCountOthersOnlyAndTopListsMostViewedFirst() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("ann" + tag), bob = signIn("bob" + tag);

        mvc.perform(get("/api/gaze/search/people").param("q", "bob" + tag).cookie(ann)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", contains("bob" + tag)));
        mvc.perform(get("/api/gaze/search/people").param("q", "b").cookie(ann)).andExpect(status().isBadRequest());   // too short
        mvc.perform(get("/api/gaze/search/people").param("q", "ann" + tag).cookie(ann)).andExpect(jsonPath("$", hasSize(0)));   // not yourself

        String quiet = idea(bob, "quiet" + tag), loud = idea(bob, "loud" + tag);
        mvc.perform(get("/api/posts/" + loud).cookie(bob)).andExpect(jsonPath("$.views").value(0));   // the author opening it is not a view
        mvc.perform(get("/api/posts/" + loud).cookie(ann));
        mvc.perform(get("/api/posts/" + loud).cookie(ann)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + quiet).cookie(ann));

        mvc.perform(get("/api/gaze/search").param("q", tag).param("sort", "top").cookie(ann)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(loud)).andExpect(jsonPath("$.items[0].views").value(2))
                .andExpect(jsonPath("$.items[1].id").value(quiet));
    }
}
