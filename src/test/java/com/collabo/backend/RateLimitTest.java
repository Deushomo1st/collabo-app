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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The throttle on the whole API, switched on with tiny limits. The default test profile turns it off. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:throttle;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key",
        "app.throttle.enabled=true",
        "app.throttle.auth-per-minute=3",
        "app.throttle.admin-per-minute=4",
        "app.throttle.report-per-hour=1",
        "app.throttle.write-per-minute=5",
        "app.throttle.read-per-minute=6"})
@AutoConfigureMockMvc
class RateLimitTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private Cookie signIn(String ip) throws Exception {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setUsername(name); u.setEmail(name + "@t.dev"); u.setRole(Role.USER); u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr(ip); return r; }).cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private ResultActions as(MockHttpServletRequestBuilder req, Cookie session, String ip) throws Exception {
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (session != null) req.cookie(XSRF, session).header("X-XSRF-TOKEN", "t");
        return mvc.perform(req);
    }

    @Test
    void readsAreCountedPerAccountAndAnswer429WithWhenToRetry() throws Exception {
        Cookie a = signIn("10.1.0.1"), b = signIn("10.1.0.1");   // same address, two accounts
        for (int i = 0; i < 6; i++) as(get("/api/notifications"), a, "10.1.0.1").andExpect(status().isOk());
        as(get("/api/notifications"), a, "10.1.0.1").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.retryAfterSeconds").value(greaterThan(0)))
                .andExpect(jsonPath("$.message", containsString("too fast")));
        as(get("/api/notifications"), b, "10.1.0.1").andExpect(status().isOk());   // a neighbour on the same IP is not punished
    }

    @Test
    void changesHaveTheirOwnSmallerBucket() throws Exception {
        Cookie s = signIn("10.2.0.1");
        for (int i = 0; i < 5; i++) as(post("/api/notifications/read-all"), s, "10.2.0.1").andExpect(status().is(not(429)));
        as(post("/api/notifications/read-all"), s, "10.2.0.1").andExpect(status().isTooManyRequests());
        as(get("/api/notifications"), s, "10.2.0.1").andExpect(status().isOk());   // reads are a different bucket
    }

    @Test
    void signInAndRegistrationAreThrottledByAddress() throws Exception {
        for (int i = 0; i < 3; i++)
            as(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"identifier\":\"nobody\",\"password\":\"x\"}"), null, "10.3.0.1")
                    .andExpect(status().is(not(429)));
        as(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"identifier\":\"nobody\",\"password\":\"x\"}"), null, "10.3.0.1")
                .andExpect(status().isTooManyRequests());
        as(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{\"identifier\":\"nobody\",\"password\":\"x\"}"), null, "10.3.0.2")
                .andExpect(status().is(not(429)));   // another address is untouched
    }

    @Test
    void wrongAdminKeyGuessesRunOutToo() throws Exception {
        for (int i = 0; i < 4; i++) as(get("/api/admin/status").header("X-Admin-Key", "guess" + i), null, "10.4.0.1").andExpect(status().isForbidden());
        as(get("/api/admin/status").header("X-Admin-Key", "test-admin-key"), null, "10.4.0.1").andExpect(status().isTooManyRequests());   // even the right key waits
        as(get("/api/admin/status").header("X-Admin-Key", "test-admin-key"), null, "10.4.0.9").andExpect(status().isOk());
    }

    @Test
    void reportsAreAFewPerHour() throws Exception {
        Cookie s = signIn("10.5.0.1");
        String id = UUID.randomUUID().toString();
        as(post("/api/yarns/threads/" + id + "/report").contentType("application/json").content("{\"reason\":\"x\"}"), s, "10.5.0.1").andExpect(status().isNotFound());   // allowed through, no such thread
        as(post("/api/yarns/threads/" + id + "/report").contentType("application/json").content("{\"reason\":\"x\"}"), s, "10.5.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void staticPagesAreNotThrottled() throws Exception {
        for (int i = 0; i < 12; i++) as(get("/HTML-pages/login.html"), null, "10.6.0.1").andExpect(status().isOk());
    }
}
