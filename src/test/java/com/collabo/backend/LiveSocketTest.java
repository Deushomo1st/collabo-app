package com.collabo.backend;

import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The live socket end to end on a real port: who may connect, what they hear, and the tick state it drives. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:livesocket;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.key=test-admin-key"})
class LiveSocketTest {

    static final String PASSWORD = "Passw0rd!x9";

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    /** One signed-in browser: its own cookie jar, plain HTTP calls with the CSRF header, and sockets on the same session. */
    class Browser {
        final CookieManager jar = new CookieManager();
        final HttpClient http = HttpClient.newBuilder().cookieHandler(jar).build();
        final String name;

        Browser(String name) throws Exception {
            this.name = name;
            User u = new User();
            u.setUsername(name); u.setEmail(name + "@t.dev"); u.setRole(Role.USER); u.setPassword(encoder.encode(PASSWORD)); u.setVerified(true);
            users.save(u);
            call("GET", "/api/auth/csrf", null);
            call("POST", "/api/auth/login", "{\"identifier\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}");
        }

        String cookies() {
            return jar.getCookieStore().getCookies().stream().map(c -> c.getName() + "=" + c.getValue()).reduce((a, b) -> a + "; " + b).orElse("");
        }

        /** A moderator's browser: signed in through the moderator door instead of the user one. */
        Browser(String moderatorEmail, String moderatorPassword) throws Exception {
            this.name = moderatorEmail;
            call("GET", "/api/auth/csrf", null);
            call("POST", "/api/moderator/login", "{\"identifier\":\"" + moderatorEmail + "\",\"password\":\"" + moderatorPassword + "\"}");
        }

        String admin(String method, String path, String json) throws Exception { return call(method, path, json, "test-admin-key"); }

        String call(String method, String path, String json) throws Exception { return call(method, path, json, null); }

        String call(String method, String path, String json, String adminKey) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (adminKey != null) b.header("X-Admin-Key", adminKey);
            jar.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
            if (json != null) b.header("Content-Type", "application/json");
            b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(r.statusCode() < 300, method + " " + path + " -> " + r.statusCode() + " " + r.body());
            return r.body();
        }

        /** Opens the socket; frames land in the returned queue. Throws if the handshake is refused. */
        BlockingQueue<String> listen(String origin) throws Exception {
            BlockingQueue<String> got = new LinkedBlockingQueue<>();
            var b = HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", cookies());
            if (origin != null) b.header("Origin", origin);
            ws = b.buildAsync(URI.create("ws://localhost:" + port + "/ws/live"), new WebSocket.Listener() {
                @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) { got.add(data.toString()); w.request(1); return CompletableFuture.completedFuture(null); }
            }).get(5, TimeUnit.SECONDS);
            return got;
        }
        WebSocket ws;
    }

    private static String take(BlockingQueue<String> q, String type) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            String m = q.poll(200, TimeUnit.MILLISECONDS);
            if (m != null && m.contains("\"t\":\"" + type + "\"")) return m;
        }
        return fail("no " + type + " signal within 5s");
    }

    @Test
    void theSocketIsForSignedInSameOriginBrowsersOnly() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag);
        // no session: refused
        assertThrows(Exception.class, () -> HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws/live"), new WebSocket.Listener() {}).get(5, TimeUnit.SECONDS));
        // a session, but from another site's page: refused (this is what stops cross-site socket hijacking)
        assertThrows(Exception.class, () -> ann.listen("http://evil.example"));
        // a session from this site: in, and it answers pings
        var q = ann.listen("http://localhost:" + port);
        ann.ws.sendText("{\"t\":\"ping\"}", true);
        take(q, "pong");
    }

    @Test
    void aYarnReachesTheOtherSideAtOnceAndTheTicksFollowDeliveredThenRead() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag), bob = new Browser("bob" + tag);
        var annQ = ann.listen(null);
        var bobQ = bob.listen(null);

        String dm = JsonPath.read(ann.call("POST", "/api/yarns/threads/myspace", "{\"username\":\"" + bob.name + "\",\"body\":\"hello bob\"}"), "$.id");
        String signal = take(bobQ, "yarn");                                   // bob is told, without asking
        assertEquals(dm, JsonPath.read(signal, "$.thread"));
        String yarnId = JsonPath.read(signal, "$.yarn");
        assertEquals("SENT", receipt(ann, dm));                                // one tick: saved

        bob.ws.sendText("{\"t\":\"delivered\",\"thread\":\"" + dm + "\",\"yarn\":\"" + yarnId + "\"}", true);
        take(annQ, "receipt");                                                 // ann is told to look again
        assertEquals("DELIVERED", receipt(ann, dm));                           // two ticks: it reached bob's browser

        bob.call("POST", "/api/yarns/threads/" + dm + "/read", null);          // bob opens the thread
        take(annQ, "receipt");
        assertEquals("READ", receipt(ann, dm));                                // green

    }

    @Test
    void aBrowserThatWasAwayCatchesUpTheSendersTicksWhenItConnects() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag), bob = new Browser("bob" + tag);
        var annQ = ann.listen(null);
        String dm = JsonPath.read(ann.call("POST", "/api/yarns/threads/myspace", "{\"username\":\"" + bob.name + "\",\"body\":\"are you there?\"}"), "$.id");
        assertEquals("SENT", receipt(ann, dm));            // bob has no socket: one tick, honestly
        bob.listen(null);                                   // he turns up
        take(annQ, "receipt");
        assertEquals("DELIVERED", receipt(ann, dm));
    }

    @Test
    void theBellHearsWhenNotificationsChangeEvenFromAnotherTab() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser bob = new Browser("bob" + tag), stranger = new Browser("str" + tag);
        var tabOne = bob.listen(null);            // bob has two tabs open
        var tabTwo = bob.listen(null);
        var other = stranger.listen(null);
        bob.call("POST", "/api/notifications/read-all", null);   // done in one tab
        take(tabOne, "notification");                             // both tabs are told
        take(tabTwo, "notification");
        Thread.sleep(300);
        assertNull(other.poll(), "someone else's bell must stay quiet");
    }

    @Test
    void aModeratorDeskHearsWhenACaseIsAssignedSwappedAwayAndClosed() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag), bob = new Browser("bob" + tag);
        String dm = JsonPath.read(ann.call("POST", "/api/yarns/threads/myspace", "{\"username\":\"" + bob.name + "\",\"body\":\"hi\"}"), "$.id");
        String inv = JsonPath.read(ann.call("POST", "/api/yarns/threads/" + dm + "/report", "{\"reason\":\"rude\"}"), "$.id");
        String pw = "ModPassw0rd!x9";
        String first = JsonPath.read(ann.admin("POST", "/api/admin/moderators", "{\"name\":\"One\",\"email\":\"one" + tag + "@t.dev\",\"password\":\"" + pw + "\"}"), "$.id");
        String second = JsonPath.read(ann.admin("POST", "/api/admin/moderators", "{\"name\":\"Two\",\"email\":\"two" + tag + "@t.dev\",\"password\":\"" + pw + "\"}"), "$.id");
        Browser one = new Browser("one" + tag + "@t.dev", pw), two = new Browser("two" + tag + "@t.dev", pw);
        var oneQ = one.listen(null);
        var twoQ = two.listen(null);

        ann.admin("POST", "/api/admin/investigations/" + inv + "/assign", "{\"moderatorId\":\"" + first + "\"}");
        take(oneQ, "case");                                   // it lands on the first desk
        ann.admin("POST", "/api/admin/investigations/" + inv + "/assign", "{\"moderatorId\":\"" + second + "\"}");
        take(oneQ, "case");                                   // and leaves it
        take(twoQ, "case");                                   // for the second
        ann.admin("POST", "/api/admin/investigations/" + inv + "/close", null);
        take(twoQ, "case");                                   // closing clears it from the desk that held it
    }

    private BlockingQueue<String> adminSocket(String ticket) throws Exception {
        BlockingQueue<String> got = new LinkedBlockingQueue<>();
        HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws/admin" + (ticket == null ? "" : "?ticket=" + ticket)), new WebSocket.Listener() {
            @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) { got.add(data.toString()); w.request(1); return CompletableFuture.completedFuture(null); }
        }).get(5, TimeUnit.SECONDS);
        return got;
    }

    @Test
    void theAdminConsoleTradesItsKeyForAOneTimeTicketAndHearsTheQueueChange() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag), bob = new Browser("bob" + tag);
        assertThrows(Exception.class, () -> adminSocket(null));            // no ticket
        assertThrows(Exception.class, () -> adminSocket("guess"));         // wrong ticket
        assertThrows(AssertionError.class, () -> ann.call("POST", "/api/admin/live/ticket", null, "wrong-key"));   // 403: the key is needed to get one

        String ticket = JsonPath.read(ann.admin("POST", "/api/admin/live/ticket", null), "$.ticket");
        var q = adminSocket(ticket);
        assertThrows(Exception.class, () -> adminSocket(ticket));          // spent: a leaked URL is useless

        String dm = JsonPath.read(ann.call("POST", "/api/yarns/threads/myspace", "{\"username\":\"" + bob.name + "\",\"body\":\"hi\"}"), "$.id");
        ann.call("POST", "/api/yarns/threads/" + dm + "/report", "{\"reason\":\"rude\"}");
        take(q, "queue");                                                   // a new report reaches the open console
    }

    @Test
    void deliveredFramesFromStrangersOrJunkChangeNothing() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Browser ann = new Browser("ann" + tag), bob = new Browser("bob" + tag), eve = new Browser("eve" + tag);
        var bobQ = bob.listen(null);
        eve.listen(null);
        String dm = JsonPath.read(ann.call("POST", "/api/yarns/threads/myspace", "{\"username\":\"" + bob.name + "\",\"body\":\"hi\"}"), "$.id");
        String yarnId = JsonPath.read(take(bobQ, "yarn"), "$.yarn");
        eve.ws.sendText("{\"t\":\"delivered\",\"thread\":\"" + dm + "\",\"yarn\":\"" + yarnId + "\"}", true);   // eve is not in it
        bob.ws.sendText("not json at all", true);
        bob.ws.sendText("{\"t\":\"delivered\",\"thread\":\"" + UUID.randomUUID() + "\",\"yarn\":\"" + yarnId + "\"}", true);   // wrong thread
        Thread.sleep(600);
        assertEquals("SENT", receipt(ann, dm));
    }

    /** The receipt on ann's newest own yarn in the thread. */
    private String receipt(Browser ann, String thread) throws Exception {
        return JsonPath.read(ann.call("GET", "/api/yarns/threads/" + thread + "/yarns", null), "$[0].receipt");
    }
}
