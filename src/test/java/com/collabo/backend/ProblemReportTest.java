package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Report a problem: a write-up plus screenshots, read by the admin; files stay private to the reporter and the admin. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:problemreport;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class ProblemReportTest {

    static final String PASSWORD = "Passw0rd!x9", KEY = "test-admin-key";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};

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

    private String upload(Cookie s) throws Exception {
        String res = mvc.perform(post("/api/media").cookie(XSRF, s).header("X-XSRF-TOKEN", "t").contentType("image/png").content(PNG))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions report(Cookie s, String json) throws Exception {
        return mvc.perform(post("/api/reports").cookie(XSRF, s).header("X-XSRF-TOKEN", "t").contentType("application/json").content(json));
    }

    @Test
    void reportReachesTheAdminWithItsScreenshotAndNobodyElseCanOpenTheFile() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("rep" + tag), bob = signIn("oth" + tag);
        String file = upload(ann);
        String id = JsonPath.read(report(ann, "{\"summary\":\"The Gaze froze when I scrolled\",\"pageUrl\":\"/HTML-pages/gaze.html\",\"mediaIds\":[\"" + file + "\"]}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/admin/reports").header("X-Admin-Key", KEY)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].reporter", contains("rep" + tag)))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].pageUrl", contains("/HTML-pages/gaze.html")))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].media[0].id", contains(file)));
        mvc.perform(get("/api/admin/reports/" + id + "/media/" + file).header("X-Admin-Key", KEY)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mvc.perform(get("/api/media/" + file).cookie(bob)).andExpect(status().isNotFound());           // not a post's file, not bob's
        mvc.perform(get("/api/admin/reports")).andExpect(status().isForbidden());                       // no key, no list

        mvc.perform(post("/api/admin/reports/" + id + "/resolve").header("X-Admin-Key", KEY).contentType("application/json").content("{\"resolved\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/reports").header("X-Admin-Key", KEY)).andExpect(jsonPath("$[?(@.id=='" + id + "')].resolved", contains(true)));
    }

    @Test
    void shortSummariesForeignFilesTooManyFilesAndOffSitePagesAreHandled() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Cookie ann = signIn("rv" + tag), bob = signIn("ov" + tag);
        report(ann, "{\"summary\":\"bad\"}").andExpect(status().isBadRequest());
        report(ann, "{\"summary\":\"A long enough write-up\",\"mediaIds\":[\"" + upload(bob) + "\"]}").andExpect(status().isBadRequest());   // someone else's upload
        String four = String.join(",", java.util.stream.Stream.generate(() -> "\"" + UUID.randomUUID() + "\"").limit(4).toList());
        report(ann, "{\"summary\":\"A long enough write-up\",\"mediaIds\":[" + four + "]}").andExpect(status().isBadRequest());
        // a page on another host is dropped, the report itself is kept
        String id = JsonPath.read(report(ann, "{\"summary\":\"Link looked odd to me\",\"pageUrl\":\"https://evil.example/x\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/admin/reports").header("X-Admin-Key", KEY)).andExpect(jsonPath("$[?(@.id=='" + id + "')].pageUrl", contains(nullValue())));
    }

    @Test
    void sixthReportInAnHourIsRefused() throws Exception {
        Cookie ann = signIn("spam" + UUID.randomUUID().toString().substring(0, 6));
        for (int i = 0; i < 5; i++) report(ann, "{\"summary\":\"Report number " + i + " here\"}").andExpect(status().isOk());
        report(ann, "{\"summary\":\"One report too many\"}").andExpect(status().isBadRequest());
    }
}
