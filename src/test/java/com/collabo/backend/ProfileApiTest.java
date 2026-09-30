package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile fields over HTTP, against an in-memory H2 (no Postgres needed). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profiles;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class ProfileApiTest {

    static final String PASSWORD = "Passw0rd!x9";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");   // double-submit: any matching cookie + header passes

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String name, other;
    Cookie session, otherSession;

    @BeforeEach
    void accounts() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        name = "ada" + tag; other = "bo" + tag;
        session = signIn(name);
        otherSession = signIn(other);
    }

    private Cookie signIn(String username) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private MockHttpServletRequestBuilder edit(String json) {
        return patch("/api/users/me").cookie(XSRF, session).header("X-XSRF-TOKEN", "t").contentType("application/json").content(json);
    }

    @Test
    void aNewProfileHasEmptyFieldsAndEveryoneMaySeeCredentials() throws Exception {
        mvc.perform(get("/api/users/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name)).andExpect(jsonPath("$.self").value(true))
                .andExpect(jsonPath("$.preferredTitle").value("")).andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.credentialsPrivacy").value("EVERYONE"));
    }

    @Test
    void editingChangesOnlyTheFieldsSentAndOthersSeeThemWithoutThePrivacySetting() throws Exception {
        mvc.perform(edit("{\"preferredTitle\":\"Product designer\",\"bio\":\"I build calm things.\",\"credentialsPrivacy\":\"MUTUAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.credentialsPrivacy").value("MUTUAL"));
        mvc.perform(edit("{\"bio\":\"New bio\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("New bio")).andExpect(jsonPath("$.preferredTitle").value("Product designer"));

        mvc.perform(get("/api/users/" + name).cookie(otherSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.self").value(false)).andExpect(jsonPath("$.bio").value("New bio"))
                .andExpect(jsonPath("$.credentialsPrivacy").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(edit("{\"preferredTitle\":\"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.preferredTitle").value(""));
    }

    @Test
    void theBioLimitIsSixtyWordsAndExtraSpacesDontCount() throws Exception {
        String sixty = ("word ").repeat(60);
        mvc.perform(edit("{\"bio\":\"  " + sixty.replace(" ", "   ") + "\"}")).andExpect(status().isOk());
        mvc.perform(edit("{\"bio\":\"" + sixty + "one more\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your bio can be at most 60 words."));
    }

    @Test
    void badTitlesAndBiosAreRejected() throws Exception {
        mvc.perform(edit("{\"preferredTitle\":\"" + "x".repeat(41) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(edit("{\"preferredTitle\":\"two\\nlines\"}")).andExpect(status().isBadRequest());
        mvc.perform(edit("{\"bio\":\"be\\u0007ll\"}")).andExpect(status().isBadRequest());
        mvc.perform(edit("{\"bio\":\"" + "a".repeat(601) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(edit("{\"credentialsPrivacy\":\"EVERYBODY\"}")).andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder putLinks(String json) {
        return put("/api/users/me/links").cookie(XSRF, session).header("X-XSRF-TOKEN", "t").contentType("application/json").content(json);
    }

    @Test
    void linksAreReplacedInOrderAndVisibleToOthers() throws Exception {
        mvc.perform(putLinks("[{\"title\":\"Site\",\"url\":\"https://ada.dev\",\"note\":\"my work\"},{\"title\":\"Code\",\"url\":\"http://git.example/ada\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].title").value("Site")).andExpect(jsonPath("$.links[1].note").value(""));
        mvc.perform(putLinks("[{\"title\":\"Only\",\"url\":\"https://only.dev\"}]")).andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(1));
        mvc.perform(get("/api/users/" + name).cookie(otherSession)).andExpect(jsonPath("$.links[0].title").value("Only"));
        mvc.perform(putLinks("[]")).andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(0));
    }

    @Test
    void badLinksAreRejectedAndNothingIsChanged() throws Exception {
        mvc.perform(putLinks("[{\"title\":\"Keep\",\"url\":\"https://keep.dev\"}]")).andExpect(status().isOk());
        for (String url : new String[]{"javascript:alert(1)", "data:text/html,hi", "ftp://x.dev/f", "//x.dev", "https://", "not a url", ""}) {
            mvc.perform(putLinks("[{\"title\":\"Bad\",\"url\":\"" + url + "\"}]")).andExpect(status().isBadRequest());
        }
        mvc.perform(putLinks("[{\"title\":\"\",\"url\":\"https://x.dev\"}]")).andExpect(status().isBadRequest());
        mvc.perform(putLinks("[{\"title\":\"" + "x".repeat(61) + "\",\"url\":\"https://x.dev\"}]")).andExpect(status().isBadRequest());
        mvc.perform(putLinks("[{\"title\":\"T\",\"url\":\"https://x.dev\",\"note\":\"" + "n".repeat(121) + "\"}]")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").cookie(session)).andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].title").value("Keep"));
    }

    @Test
    void atMostTwelveLinks() throws Exception {
        String one = "{\"title\":\"L\",\"url\":\"https://l.dev\"}";
        mvc.perform(putLinks("[" + String.join(",", java.util.Collections.nCopies(12, one)) + "]")).andExpect(status().isOk());
        mvc.perform(putLinks("[" + String.join(",", java.util.Collections.nCopies(13, one)) + "]")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownUsersAre404AndSignedOutVisitorsAre401() throws Exception {
        mvc.perform(get("/api/users/nobody-" + name).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + name)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
