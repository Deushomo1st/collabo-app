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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The welcome flag: new accounts need it, legacy accounts never do, and finishing it is remembered. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:welcome;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class WelcomeFlagTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private Cookie signIn(String username, Boolean welcomed) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        if (welcomed != null) u.setWelcomed(welcomed);
        users.save(u);
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    @Test
    void aNewAccountNeedsTheWelcomeUntilItIsFinished() throws Exception {
        Cookie s = signIn("newbie", false);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(jsonPath("$.needsWelcome").value(true));
        mvc.perform(post("/api/users/me/welcomed").cookie(XSRF, s).header("X-XSRF-TOKEN", "t")).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(jsonPath("$.needsWelcome").value(false));
    }

    @Test
    void anOldAccountIsNeverSentThroughIt() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(signIn("veteran", null))).andExpect(jsonPath("$.needsWelcome").value(false));
    }
}
