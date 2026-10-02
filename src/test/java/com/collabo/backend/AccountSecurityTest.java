package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.Space;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Account security: forgot/reset password, change password, signed-in devices, deleting the account. Mail is faked; the code is read from it. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:accountsecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class AccountSecurityTest {

    static final String PASSWORD = "Passw0rd!x9", NEXT = "Brand-new pass 42";
    static final Cookie XSRF = new Cookie("XSRF-TOKEN", "t");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SpaceRepository spaces;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailService email;

    private String account() {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setUsername(name); u.setEmail(name + "@t.dev"); u.setRole(Role.USER);
        u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
        users.save(u);
        return name;
    }

    private ResultActions login(String who, String pw) throws Exception {
        return mvc.perform(post("/api/auth/login").cookie(XSRF).header("X-XSRF-TOKEN", "t").contentType("application/json")
                .content("{\"identifier\":\"" + who + "\",\"password\":\"" + pw + "\"}"));
    }

    private Cookie device(String who) throws Exception { return login(who, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("COLLABO_SESSION"); }

    private ResultActions send(String method, String path, Cookie session, String json) throws Exception {
        var req = method.equals("DELETE") ? delete(path) : post(path);
        if (session != null) req.cookie(XSRF, session); else req.cookie(XSRF);
        return mvc.perform(req.header("X-XSRF-TOKEN", "t").contentType("application/json").content(json == null ? "" : json));
    }

    private ResultActions reset(String name, String code, String pw) throws Exception {
        return send("POST", "/api/auth/reset", null, "{\"email\":\"" + name + "@t.dev\",\"code\":\"" + code + "\",\"password\":\"" + pw + "\"}");
    }

    private String codeSentTo(String name) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendResetEmail(eq(name + "@t.dev"), eq(name), code.capture());
        return code.getValue();
    }

    @Test
    void aResetCodeSetsANewPasswordAndSignsEveryDeviceOut() throws Exception {
        String name = account();
        Cookie old = device(name);
        send("POST", "/api/auth/forgot", null, "{\"email\":\"" + name + "@t.dev\"}").andExpect(status().isOk());
        String code = codeSentTo(name);

        reset(name, "000000".equals(code) ? "111111" : "000000", NEXT).andExpect(status().isBadRequest());   // a wrong code
        reset(name, code, "short").andExpect(status().isBadRequest());   // a weak new password does not use the code up
        reset(name, code, NEXT).andExpect(status().isNoContent());

        login(name, PASSWORD).andExpect(status().isUnauthorized());
        login(name, NEXT).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(old)).andExpect(status().isUnauthorized());   // the old device is signed out
        reset(name, code, "Another pass 77").andExpect(status().isBadRequest());   // and the code only works once
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        send("POST", "/api/auth/forgot", null, "{\"email\":\"nobody" + UUID.randomUUID() + "@t.dev\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        verify(email, never()).sendResetEmail(any(), any(), any());
    }

    @Test
    void fiveWrongCodesLockTheAddressEvenForTheRightCode() throws Exception {
        String name = account();
        send("POST", "/api/auth/forgot", null, "{\"email\":\"" + name + "@t.dev\"}");
        String code = codeSentTo(name), wrong = "000000".equals(code) ? "111111" : "000000";
        for (int i = 0; i < 5; i++) reset(name, wrong, NEXT).andExpect(status().isBadRequest());
        reset(name, code, NEXT).andExpect(status().isTooManyRequests());
    }

    private String changeCodeSentTo(String name) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendChangeCodeEmail(eq(name + "@t.dev"), eq(name), code.capture());
        return code.getValue();
    }

    @Test
    void changingThePasswordNeedsAnEmailedCodeThenTheCurrentPasswordAndSignsTheOtherDevicesOut() throws Exception {
        String name = account();
        Cookie phone = device(name), laptop = device(name);
        String change = "{\"current\":\"" + PASSWORD + "\",\"password\":\"" + NEXT + "\"}";

        send("POST", "/api/account/password", laptop, change).andExpect(status().isBadRequest());   // no code yet: the right password isn't enough
        send("POST", "/api/account/password/code", laptop, null).andExpect(status().isNoContent());
        send("POST", "/api/account/password/code", laptop, null).andExpect(status().isTooManyRequests());   // one code a minute
        String code = changeCodeSentTo(name);

        send("POST", "/api/account/password/verify", laptop, "{\"code\":\"" + ("000000".equals(code) ? "111111" : "000000") + "\"}").andExpect(status().isBadRequest());
        send("POST", "/api/account/password", laptop, change).andExpect(status().isBadRequest());   // a wrong code unlocks nothing
        send("POST", "/api/account/password/verify", laptop, "{\"code\":\"" + code + "\"}").andExpect(status().isNoContent());
        send("POST", "/api/account/password", phone, change).andExpect(status().isBadRequest());   // the code unlocked the laptop, not the phone

        send("POST", "/api/account/password", laptop, "{\"current\":\"wrong\",\"password\":\"" + NEXT + "\"}").andExpect(status().isBadRequest());
        send("POST", "/api/account/password", laptop, "{\"current\":\"" + PASSWORD + "\",\"password\":\"short\"}").andExpect(status().isBadRequest());
        send("POST", "/api/account/password", laptop, change).andExpect(status().isNoContent());   // mistakes didn't use the confirmation up

        mvc.perform(get("/api/auth/me").cookie(laptop)).andExpect(status().isOk());   // the device you changed it on stays in
        mvc.perform(get("/api/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        login(name, NEXT).andExpect(status().isOk());
        verify(email).sendPasswordChangedEmail(eq(name + "@t.dev"), eq(name));
        send("POST", "/api/account/password", laptop, "{\"current\":\"" + NEXT + "\",\"password\":\"Yet another pass 5\"}").andExpect(status().isBadRequest());   // one change per code
    }

    @Test
    void fiveWrongChangeCodesLockItEvenForTheRightCode() throws Exception {
        String name = account();
        Cookie laptop = device(name);
        send("POST", "/api/account/password/code", laptop, null).andExpect(status().isNoContent());
        String code = changeCodeSentTo(name), wrong = "000000".equals(code) ? "111111" : "000000";
        for (int i = 0; i < 5; i++) send("POST", "/api/account/password/verify", laptop, "{\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        send("POST", "/api/account/password/verify", laptop, "{\"code\":\"" + code + "\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void youSeeYourDevicesAndCanSignOneOrAllOthersOut() throws Exception {
        String name = account();
        Cookie phone = device(name), laptop = device(name), tablet = device(name);

        String list = mvc.perform(get("/api/account/sessions").cookie(laptop)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.current == true)]", org.hamcrest.Matchers.hasSize(1))).andReturn().getResponse().getContentAsString();
        String other = JsonPath.<java.util.List<String>>read(list, "$[?(@.current == false)].id").get(0);
        assertEquals(16, other.length(), "an opaque handle, not a session id");

        send("DELETE", "/api/account/sessions/" + other, laptop, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/sessions").cookie(laptop)).andExpect(jsonPath("$.length()").value(2));
        send("DELETE", "/api/account/sessions/" + other, laptop, null).andExpect(status().isNotFound());

        send("POST", "/api/account/sessions/sign-out-others", laptop, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/sessions").cookie(laptop)).andExpect(jsonPath("$.length()").value(1));
        int alive = 0;
        for (Cookie c : new Cookie[]{phone, tablet}) if (mvc.perform(get("/api/auth/me").cookie(c)).andReturn().getResponse().getStatus() == 200) alive++;
        assertEquals(0, alive);
    }

    @Test
    void deletingTheAccountWipesWhoYouAreKeepsTheRowAndRemovesYourIdeas() throws Exception {
        String name = account();
        Cookie session = device(name);
        String idea = JsonPath.read(send("POST", "/api/posts", session, "{\"title\":\"Mine to lose\",\"body\":\"A body long enough to count\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        UUID id = users.findByUsername(name).orElseThrow().getId();

        send("POST", "/api/account/delete", session, "{\"password\":\"wrong\"}").andExpect(status().isBadRequest());
        send("POST", "/api/account/delete", session, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        login(name, PASSWORD).andExpect(status().isUnauthorized());
        assertFalse(posts.existsById(UUID.fromString(idea)));
        User row = users.findById(id).orElseThrow();   // kept, so anything that points at this person still resolves
        assertTrue(row.getUsername().startsWith("deleted-"));
        assertTrue(row.getEmail().endsWith("@deleted.invalid"));
        assertFalse(users.existsByUsername(name), "the name is free again");
    }

    @Test
    void aSpaceOwnerCannotDeleteTheAccountYet() throws Exception {
        String name = account();
        Cookie session = device(name);
        spaces.save(new Space(UUID.randomUUID(), users.findByUsername(name).orElseThrow().getId(), "Team " + name));
        send("POST", "/api/account/delete", session, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("own a space")));
        assertTrue(users.existsByUsername(name));
    }
}
