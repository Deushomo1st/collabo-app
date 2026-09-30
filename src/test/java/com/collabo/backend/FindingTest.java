package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Findings: the assigned moderator reads the reported Yarnspace read-only and reports back with screenshots; the admin closes. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:findings;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
@AutoConfigureMockMvc
class FindingTest {

    static final String PASSWORD = "Passw0rd!x9", MOD_PASSWORD = "long-enough-pw";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String ann, bob, dan, dm, inv, mod, other;
    Cookie annS, bobS, danS, modS, otherS;

    @BeforeEach
    void setUp() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        ann = "ann" + tag; bob = "bob" + tag; dan = "dan" + tag;
        annS = signIn(ann); bobS = signIn(bob); danS = signIn(dan);
        dm = JsonPath.read(send(post("/api/yarns/threads/myspace"), annS, "{\"username\":\"" + bob + "\",\"body\":\"hey bob\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        inv = JsonPath.read(send(post("/api/yarns/threads/" + dm + "/report"), bobS, "{\"reason\":\"threats\"}").andReturn().getResponse().getContentAsString(), "$.id");
        mod = newModerator("mo" + tag + "@t.dev");
        other = newModerator("ot" + tag + "@t.dev");
        modS = moderatorLogin("mo" + tag + "@t.dev"); otherS = moderatorLogin("ot" + tag + "@t.dev");
    }

    private String newModerator(String email) throws Exception {
        return JsonPath.read(admin(post("/api/admin/moderators"), "{\"name\":\"Mo\",\"email\":\"" + email + "\",\"password\":\"" + MOD_PASSWORD + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Cookie moderatorLogin(String email) throws Exception {
        return mvc.perform(post("/api/moderator/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + MOD_PASSWORD + "\"}")).andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private Cookie signIn(String username) throws Exception {
        User u = new User();
        u.setUsername(username); u.setEmail(username + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getCookie("COLLABO_SESSION");
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Cookie session, String json) throws Exception {
        req.cookie(XSRF, session).header("X-XSRF-TOKEN", "t");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private ResultActions admin(MockHttpServletRequestBuilder req, String json) throws Exception {
        req.header("X-Admin-Key", "test-admin-key");
        if (json != null) req.contentType("application/json").content(json);
        return mvc.perform(req);
    }

    private void assign(String moderator) throws Exception {
        admin(post("/api/admin/investigations/" + inv + "/assign"), "{\"moderatorId\":\"" + moderator + "\"}").andExpect(status().isOk());
    }

    private String finding(Cookie by, String text) throws Exception {
        return JsonPath.read(send(post("/api/moderator/investigations/" + inv + "/findings"), by, "{\"text\":\"" + text + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private ResultActions shot(String finding, Cookie by, String type, byte[] bytes) throws Exception {
        return mvc.perform(post("/api/moderator/investigations/" + inv + "/findings/" + finding + "/screenshots").cookie(XSRF, by)
                .header("X-XSRF-TOKEN", "t").contentType(type).content(bytes));
    }

    @Test
    void onlyTheAssignedModeratorReadsTheYarnspaceAndOnlyToRead() throws Exception {
        send(get("/api/moderator/investigations/" + inv), modS, null).andExpect(status().isNotFound());   // not assigned yet
        assign(mod);
        send(get("/api/moderator/investigations"), modS, null).andExpect(jsonPath("$[0].id").value(inv));
        send(get("/api/moderator/investigations"), otherS, null).andExpect(jsonPath("$", hasSize(0)));
        send(get("/api/moderator/investigations/" + inv), modS, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.yarns[*].body", hasItem("hey bob"))).andExpect(jsonPath("$.members", containsInAnyOrder(ann, bob)));
        send(get("/api/moderator/investigations/" + inv), otherS, null).andExpect(status().isNotFound());
        send(get("/api/moderator/investigations/" + inv), danS, null).andExpect(status().isUnauthorized());   // a user is not a moderator
        send(post("/api/yarns/threads/" + dm + "/yarns"), modS, "{\"body\":\"hi\"}").andExpect(status().isUnauthorized());   // and a moderator cannot write
        send(get("/api/yarns/threads"), modS, null).andExpect(status().isUnauthorized());
    }

    @Test
    void findingsAndScreenshotsReachTheAdmin() throws Exception {
        assign(mod);
        send(post("/api/moderator/investigations/" + inv + "/findings"), modS, "{\"text\":\"  \"}").andExpect(status().isBadRequest());
        send(post("/api/moderator/investigations/" + inv + "/findings"), modS, "{\"text\":\"x\",\"recommendation\":\"DROPS\"}").andExpect(status().isBadRequest());   // not an appeal
        String f = finding(modS, "Bob is being threatened, see the screenshot.");
        shot(f, otherS, "image/png", png()).andExpect(status().isNotFound());
        shot(f, modS, "image/png", "not an image".getBytes()).andExpect(status().isBadRequest());
        shot(f, modS, "image/jpeg", png()).andExpect(status().isBadRequest());   // says JPEG, is PNG
        shot(f, modS, "text/plain", png()).andExpect(status().isUnsupportedMediaType());
        String shotId = JsonPath.read(shot(f, modS, "image/png", png()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        for (int i = 0; i < 4; i++) shot(f, modS, "image/png", png()).andExpect(status().isCreated());
        shot(f, modS, "image/png", png()).andExpect(status().isBadRequest());   // five is the limit

        admin(get("/api/admin/investigations/" + inv), null).andExpect(jsonPath("$.investigation.status").value("REPORTED"))
                .andExpect(jsonPath("$.findings[0].moderator").value("Mo")).andExpect(jsonPath("$.findings[0].screenshots", hasSize(5)))
                .andExpect(jsonPath("$.findings[0].text", containsString("threatened")));
        admin(get("/api/admin/investigations/screenshots/" + shotId), null).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/admin/investigations/screenshots/" + shotId)).andExpect(status().isForbidden());   // needs the admin key
        admin(get("/api/admin/investigations/screenshots/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
    }

    @Test
    void theAdminClosesAReportAndTheModeratorLosesAccess() throws Exception {
        assign(mod);
        finding(modS, "Checked it.");
        admin(post("/api/admin/investigations/" + inv + "/close"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        admin(post("/api/admin/investigations/" + inv + "/close"), null).andExpect(status().isBadRequest());
        send(get("/api/moderator/investigations/" + inv), modS, null).andExpect(status().isNotFound());
        send(post("/api/moderator/investigations/" + inv + "/findings"), modS, "{\"text\":\"late\"}").andExpect(status().isNotFound());
        send(get("/api/notifications"), bobS, null).andExpect(jsonPath("$[0].title").value("Your report was reviewed"));
        admin(get("/api/admin/investigations/" + inv), null).andExpect(jsonPath("$.findings", hasSize(1)));   // the admin keeps the record
    }
}
