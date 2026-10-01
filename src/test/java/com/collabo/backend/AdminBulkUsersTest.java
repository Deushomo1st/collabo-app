package com.collabo.backend;

import com.collabo.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bulk account creation: rows succeed or fail one by one, blank passwords are generated and returned once. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminbulk;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class AdminBulkUsersTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void oneBadRowDoesNotSinkTheRestAndGeneratedPasswordsWork() throws Exception {
        String body = """
                {"users":[
                  {"email":"a@bulk.dev","username":"bulka"},
                  {"email":"b@bulk.dev","username":"bulkb","password":"Chosen#Pass1"},
                  {"email":"a@bulk.dev","username":"bulkdup"},
                  {"email":"nope","username":"bulkbad"}]}""";
        String json = mvc.perform(post("/api/admin/users/bulk").header("X-Admin-Key", "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ok").value(true)).andExpect(jsonPath("$[0].password", hasLength(14)))
                .andExpect(jsonPath("$[1].ok").value(true)).andExpect(jsonPath("$[1].password").doesNotExist())
                .andExpect(jsonPath("$[2].ok").value(false)).andExpect(jsonPath("$[3].ok").value(false))
                .andReturn().getResponse().getContentAsString();

        String generated = json.split("\"password\":\"")[1].split("\"")[0];
        assertTrue(encoder.matches(generated, users.findByEmail("a@bulk.dev").orElseThrow().getPassword()));
        assertTrue(encoder.matches("Chosen#Pass1", users.findByEmail("b@bulk.dev").orElseThrow().getPassword()));
        assertFalse(users.existsByUsername("bulkbad"));
        assertTrue(users.findByEmail("a@bulk.dev").orElseThrow().isVerified(), "admin-created accounts can sign in straight away");
    }

    @Test
    void needsTheAdminKey() throws Exception {
        mvc.perform(post("/api/admin/users/bulk").contentType(MediaType.APPLICATION_JSON).content("{\"users\":[]}"))
                .andExpect(status().isForbidden());
    }
}
