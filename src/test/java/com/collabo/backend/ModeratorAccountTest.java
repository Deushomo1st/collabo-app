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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Moderator accounts: the admin creates and switches them, they sign in on their own endpoint and are not users. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:modaccounts;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ModeratorAccountTest {

    static final String PASSWORD = "Moderat0r-pass";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private ResultActions admin(MockHttpServletRequestBuilder req, String json) throws Exception {
        req.header("X-Admin-Key", "test-admin-key");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private ResultActions as(MockHttpServletRequestBuilder req, Cookie session, String json) throws Exception {
        req.cookie(XSRF).header("X-XSRF-TOKEN", "t");
        if (session != null) req.cookie(session);
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private String create(String email) throws Exception {
        String body = admin(post("/api/admin/moderators"), "{\"name\":\"Mo Derator\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private Cookie login(String email, String password) throws Exception {
        return as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private String email() { return "mod" + UUID.randomUUID().toString().substring(0, 8) + "@t.dev"; }

    @Test
    void onlyTheAdminKeyCreatesModeratorsAndDetailsAreChecked() throws Exception {
        mvc.perform(post("/api/admin/moderators").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        String email = email();
        create(email);
        admin(post("/api/admin/moderators"), "{\"name\":\"Dup\",\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isConflict());
        admin(post("/api/admin/moderators"), "{\"name\":\"X\",\"email\":\"" + email() + "\",\"password\":\"short\"}").andExpect(status().isBadRequest());
        admin(post("/api/admin/moderators"), "{\"name\":\"X\",\"email\":\"nope\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isBadRequest());
        admin(post("/api/admin/moderators"), "{\"name\":\" \",\"email\":\"" + email() + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isBadRequest());
        admin(get("/api/admin/moderators"), null).andExpect(jsonPath("$[?(@.email=='" + email + "')]", hasSize(1)));
    }

    @Test
    void aModeratorSignsInOnTheirOwnEndpointAndIsNotAUser() throws Exception {
        String email = email();
        create(email);
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"wrong-password\"}").andExpect(status().isUnauthorized());
        Cookie s = login(email, PASSWORD);
        as(get("/api/moderator/me"), s, null).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        as(get("/api/auth/me"), s, null).andExpect(status().isUnauthorized());          // not a user
        as(get("/api/notifications"), s, null).andExpect(status().isUnauthorized());     // user endpoints refuse it
        as(get("/api/moderator/me"), null, null).andExpect(status().isUnauthorized());

        User u = new User();
        String name = "usr" + UUID.randomUUID().toString().substring(0, 8);
        u.setUsername(name); u.setEmail(name + "@t.dev"); u.setRole(Role.USER); u.setPassword(encoder.encode("Passw0rd!x9")); u.setVerified(true);
        users.save(u);
        Cookie us = as(post("/api/auth/login"), null, "{\"identifier\":\"" + name + "\",\"password\":\"Passw0rd!x9\"}").andReturn().getResponse().getCookie("COLLABO_SESSION");
        as(get("/api/moderator/me"), us, null).andExpect(status().isUnauthorized());     // a user is not a moderator
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + name + "\",\"password\":\"Passw0rd!x9\"}").andExpect(status().isUnauthorized());   // and cannot sign in as one
    }

    @Test
    void deactivatingSignsTheModeratorOutAndBlocksLogin() throws Exception {
        String email = email();
        String id = create(email);
        Cookie s = login(email, PASSWORD);
        admin(patch("/api/admin/moderators/" + id + "/active"), "{\"active\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        as(get("/api/moderator/me"), s, null).andExpect(status().isUnauthorized());
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isUnauthorized());
        admin(patch("/api/admin/moderators/" + id + "/active"), "{\"active\":true}").andExpect(status().isOk());
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isOk());
        admin(patch("/api/admin/moderators/" + UUID.randomUUID() + "/active"), "{\"active\":true}").andExpect(status().isNotFound());
    }

    @Test
    void passwordsCanBeResetByTheAdminAndChangedByTheModerator() throws Exception {
        String email = email();
        String id = create(email);
        admin(put("/api/admin/moderators/" + id + "/password"), "{\"password\":\"tiny\"}").andExpect(status().isBadRequest());
        admin(put("/api/admin/moderators/" + id + "/password"), "{\"password\":\"Reset-by-admin-1\"}").andExpect(status().isNoContent());
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isUnauthorized());
        Cookie s = login(email, "Reset-by-admin-1");
        as(put("/api/moderator/password"), s, "{\"current\":\"nope\",\"password\":\"Brand-new-pass-2\"}").andExpect(status().isBadRequest());
        as(put("/api/moderator/password"), s, "{\"current\":\"Reset-by-admin-1\",\"password\":\"Brand-new-pass-2\"}").andExpect(status().isNoContent());
        as(post("/api/moderator/login"), null, "{\"identifier\":\"" + email + "\",\"password\":\"Brand-new-pass-2\"}").andExpect(status().isOk());
    }
}
