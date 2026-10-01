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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who can message me: a new yarn is refused until the sender fits the setting. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:msgprivtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class MessagePrivacyTest {

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

    @Test
    void newYarnsFollowTheRecipientsSetting() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("ann" + tag), bob = signIn("bob" + tag);
        String hello = "{\"username\":\"bob" + tag + "\",\"body\":\"hi\"}";

        mvc.perform(patch("/api/users/me").cookie(XSRF, bob).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"messagePrivacy\":\"FOLLOWERS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messagePrivacy").value("FOLLOWERS"));
        mvc.perform(post("/api/yarns/threads/myspace").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t").contentType("application/json").content(hello)).andExpect(status().isForbidden());

        mvc.perform(put("/api/users/bob" + tag + "/follow").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t")).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/yarns/threads/myspace").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t").contentType("application/json").content(hello)).andExpect(status().isCreated());
    }
}
