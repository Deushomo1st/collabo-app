package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Searching the Gaze: title, body and hashtags, case-insensitive, LIKE characters taken literally, two characters minimum. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gazesearch;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class GazeSearchTest {

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

    private void newPost(Cookie s, String title, String body) throws Exception {
        mvc.perform(post("/api/posts").cookie(XSRF, s).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}")).andExpect(status().isOk());
    }

    @Test
    void findsByTitleOrBodyIgnoringCaseAndTreatsPercentLiterally() throws Exception {
        Cookie ann = signIn("srchann"), bob = signIn("srchbob");
        newPost(ann, "Film crew in Lagos", "Need a camera operator");
        newPost(ann, "Logo help", "Looking for a DESIGNER, 100% unpaid");
        mvc.perform(get("/api/gaze/search?q=lagos").cookie(bob)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].title").value("Film crew in Lagos"));
        mvc.perform(get("/api/gaze/search?q=designer").cookie(bob)).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/gaze/search").param("q", "100%").cookie(bob)).andExpect(jsonPath("$.items", hasSize(1)));   // "%" is not a wildcard
        mvc.perform(get("/api/gaze/search?q=zzzz").cookie(bob)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void oneCharacterIsRefusedAndSignedOutIsRefused() throws Exception {
        mvc.perform(get("/api/gaze/search?q=a").cookie(signIn("srchcat"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/gaze/search?q=ab")).andExpect(status().isUnauthorized());
    }
}
