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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Behind Caddy the browser's Origin is https://collaboapp.pro while Spring sees http, so CORS applies even to our own pages: PUT must be allowed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:corsput;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class CorsPutTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void savingLinksFromTheLiveSiteOriginIsNotBlocked() throws Exception {
        User u = new User();
        u.setUsername("corsann"); u.setEmail("corsann@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        Cookie session = mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"corsann\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getCookie("COLLABO_SESSION");
        mvc.perform(put("/api/users/me/links").cookie(XSRF, session).header("X-XSRF-TOKEN", "t").header("Origin", "https://collaboapp.pro")
                        .contentType("application/json").content("[{\"title\":\"Site\",\"url\":\"https://example.com\",\"note\":\"\"}]"))
                .andExpect(status().isOk());
    }
}
