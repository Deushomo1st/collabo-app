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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Navigation preference follows the account, so it shows up on every device that signs in. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:navpreftest;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class NavPreferenceTest {

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
    void thePickIsSavedOnTheAccountAndComesBackFromMe() throws Exception {
        Cookie ann = signIn("ann" + UUID.randomUUID().toString().substring(0, 6));
        mvc.perform(get("/api/auth/me").cookie(ann)).andExpect(status().isOk()).andExpect(jsonPath("$.navPreference").doesNotExist());

        mvc.perform(patch("/api/users/me").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"navPreference\":\"omni-wheel\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(ann)).andExpect(jsonPath("$.navPreference").value("omni-wheel"));
    }

    @Test
    void anOddValueIsRefused() throws Exception {
        Cookie ann = signIn("bob" + UUID.randomUUID().toString().substring(0, 6));
        mvc.perform(patch("/api/users/me").cookie(XSRF, ann).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"navPreference\":\"<script>\"}"))
                .andExpect(status().isBadRequest());
    }
}
