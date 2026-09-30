package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
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

/** What the admin console reads: the status strip, the read-only table browser (secrets masked) and the users list. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminconsole;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class AdminConsoleApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private ResultActions admin(MockHttpServletRequestBuilder req) throws Exception { return mvc.perform(req.header("X-Admin-Key", "test-admin-key")); }

    private String user() {
        String name = "usr" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setUsername(name); u.setEmail(name + "@t.dev"); u.setRole(Role.USER); u.setPassword(encoder.encode("Passw0rd!x9")); u.setVerified(true);
        users.save(u);
        return name;
    }

    @Test
    void statusNeedsTheKeyAndReportsHealthFlagsAndCounts() throws Exception {
        mvc.perform(get("/api/admin/status")).andExpect(status().isForbidden());
        user();
        admin(get("/api/admin/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseOk").value(true)).andExpect(jsonPath("$.database", containsString("H2")))
                .andExpect(jsonPath("$.secureCookies").value(false)).andExpect(jsonPath("$.testAccounts").value(false))
                .andExpect(jsonPath("$.uptimeSeconds", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.counts.users", greaterThanOrEqualTo(1))).andExpect(jsonPath("$.counts.moderators").exists());
    }

    @Test
    void theBrowserListsEveryTableButOnlyUsersCanBeCleaned() throws Exception {
        admin(get("/api/admin/db/tables")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='users')].canClean", contains(true)))
                .andExpect(jsonPath("$[?(@.name=='moderator')].canClean", contains(false)))
                .andExpect(jsonPath("$[?(@.name=='appeal')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.name=~/spring_session.*/)]", hasSize(0)));   // session internals stay hidden
        admin(post("/api/admin/db/tables/moderator/truncate")).andExpect(status().isBadRequest());
        admin(post("/api/admin/db/tables/nope/truncate")).andExpect(status().isBadRequest());
    }

    @Test
    void rowsAreReadOnlyPagedAndSecretsAreMasked() throws Exception {
        String name = user();
        admin(get("/api/admin/db/tables/users/rows?limit=200")).andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasItems("email", "username", "password")))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.rows[*]", hasItem(hasItem(name))))                       // the username is there
                .andExpect(jsonPath("$.rows[*]", not(hasItem(hasItem(startsWith("$2"))))))     // no bcrypt hash anywhere
                .andExpect(jsonPath("$.rows[*]", hasItem(hasItem("••••"))));                   // the password column is masked
        admin(get("/api/admin/db/tables/users/rows?limit=1")).andExpect(jsonPath("$.rows", hasSize(1)));
        admin(get("/api/admin/db/tables/nope/rows")).andExpect(status().isNotFound());
        admin(get("/api/admin/db/tables/users;drop/rows")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/db/tables/users/rows")).andExpect(status().isForbidden());
    }

    @Test
    void theUsersListCarriesVerifiedAndPremiumForTheConsoleFilters() throws Exception {
        String name = user();
        String id = com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(admin(get("/api/admin/users")).andReturn().getResponse().getContentAsString(),
                "$[?(@.username=='" + name + "')].id").get(0);
        admin(patch("/api/admin/users/" + id + "/premium").contentType("application/json").content("{\"premium\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.premium").value(true)).andExpect(jsonPath("$.verified").value(true));
        admin(get("/api/admin/users")).andExpect(jsonPath("$[?(@.username=='" + name + "')].premium", contains(true)));
    }
}
