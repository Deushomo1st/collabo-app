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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Post audience (followers / only / except) and anonymous posts. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class PostAudienceTest {

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

    private String newPost(Cookie by, String extra) throws Exception {
        String res = mvc.perform(post("/api/posts").cookie(XSRF, by).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\"" + extra + "}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return res.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void audienceDecidesWhoSeesAPost() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("ann" + tag), bob = signIn("bob" + tag), cara = signIn("cara" + tag);
        mvc.perform(put("/api/users/ann" + tag + "/follow").cookie(XSRF, bob).header("X-XSRF-TOKEN", "t")).andExpect(status().is2xxSuccessful());

        String followers = newPost(ann, ",\"audience\":\"FOLLOWERS\"");
        mvc.perform(get("/api/posts/" + followers).cookie(bob)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + followers).cookie(cara)).andExpect(status().isNotFound());

        String except = newPost(ann, ",\"audience\":\"EXCEPT\",\"audienceWith\":[\"bob" + tag + "\"]");
        mvc.perform(get("/api/posts/" + except).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + except).cookie(cara)).andExpect(status().isOk());

        mvc.perform(post("/api/posts").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"audience\":\"ONLY\",\"audienceWith\":[]}")).andExpect(status().isBadRequest());
    }

    @Test
    void anonymousPostHidesItsAuthorFromOthers() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("ann" + tag), bob = signIn("bob" + tag);
        String id = newPost(ann, ",\"anonymous\":true");
        mvc.perform(get("/api/posts/" + id).cookie(bob)).andExpect(jsonPath("$.author.username").value("Anonymous")).andExpect(jsonPath("$.anonymous").value(true));
        mvc.perform(get("/api/posts/" + id).cookie(ann)).andExpect(jsonPath("$.author.username").value("ann" + tag));
        mvc.perform(get("/api/users/ann" + tag + "/posts").cookie(bob)).andExpect(jsonPath("$.items", hasSize(0)));
    }
}
