package com.collabo.backend;

import com.collabo.backend.yarn.*;
import com.collabo.backend.yarn.YarnDtos.Prefs;
import com.collabo.backend.yarn.YarnDtos.ThreadView;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.yarn.YarnThread.Tier;
import com.collabo.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the chat rules against an in-memory H2, so no Postgres is needed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:yarns;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dev-identity.enabled=true"})
class YarnServiceTest {

    @Autowired YarnService yarns;
    @Autowired UserRepository users;

    User ada, bo, cy;

    @BeforeEach
    void people() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        ada = user("ada" + tag); bo = user("bo" + tag); cy = user("cy" + tag);
    }

    private User user(String name) {
        User u = new User();
        u.setUsername(name); u.setEmail(name + "@t.dev"); u.setPassword("x"); u.setRole(Role.USER);
        return users.save(u);
    }

    private List<ThreadView> inbox(User u) { return yarns.list(u, false, null, null); }

    @Test
    void myspaceIsARequestUntilAccepted() {
        ThreadView t = yarns.startMySpace(ada, bo.getUsername(), "hi");
        assertEquals("PENDING", t.status());
        assertEquals(bo.getUsername(), t.name());

        assertThrows(YarnException.class, () -> yarns.send(ada, t.id(), "again"));   // one yarn until accepted
        assertThrows(YarnException.class, () -> yarns.send(bo, t.id(), "reply"));    // must accept first

        ThreadView seenByBo = inbox(bo).get(0);
        assertTrue(seenByBo.incomingRequest());
        assertEquals(1, seenByBo.unread());

        yarns.respond(bo, t.id(), true);
        yarns.send(bo, t.id(), "hello");
        assertEquals(2, yarns.history(ada, t.id(), null, 50).stream().filter(y -> y.kind().equals("USER")).count());
        assertEquals(1, inbox(ada).size());   // same pair never gets a second thread
        assertEquals(t.id(), yarns.startMySpace(ada, bo.getUsername(), "more").id());
    }

    @Test
    void declineArchivesForTheDeclinerAndStopsTheSender() {
        ThreadView t = yarns.startMySpace(ada, bo.getUsername(), "hi");
        yarns.respond(bo, t.id(), false);
        assertTrue(inbox(bo).isEmpty());
        assertEquals(1, yarns.list(bo, true, null, null).size());
        assertThrows(YarnException.class, () -> yarns.send(ada, t.id(), "please"));
    }

    @Test
    void blockingHidesTheThreadAndStopsDeliveryBothWays() {
        ThreadView t = yarns.startMySpace(ada, bo.getUsername(), "hi");
        yarns.respond(bo, t.id(), true);

        yarns.block(bo, ada.getId());
        assertTrue(inbox(bo).isEmpty());   // leaves the blocker's inbox
        assertEquals(List.of(ada.getUsername()), yarns.blocked(bo).stream().map(b -> b.username()).toList());
        assertThrows(YarnException.class, () -> yarns.send(ada, t.id(), "you there?"));
        assertThrows(YarnException.class, () -> yarns.send(bo, t.id(), "hey"));
        assertThrows(YarnException.class, () -> yarns.startMySpace(ada, bo.getUsername(), "hi"));
        assertTrue(yarns.directory(ada, bo.getUsername()).isEmpty());   // and can't be found

        yarns.unblock(bo, ada.getId());
        assertEquals(1, inbox(bo).size());
        assertDoesNotThrow(() -> yarns.send(ada, t.id(), "back"));
        assertThrows(YarnException.class, () -> yarns.block(bo, bo.getId()));
    }

    @Test
    void archiveIsPerPersonAndANewYarnBringsItBack() {
        ThreadView t = yarns.createGroup(ada, Tier.WESPACE, "Kobo", List.of(bo.getUsername(), cy.getUsername()));
        yarns.setPrefs(bo, t.id(), new Prefs(true, null, null));
        assertTrue(inbox(bo).isEmpty());
        assertEquals(1, inbox(cy).size());   // cy is untouched

        yarns.send(ada, t.id(), "decision time");
        assertEquals(1, inbox(bo).size());   // un-archived by the new yarn

        yarns.setPrefs(bo, t.id(), new Prefs(true, null, true));   // archived and muted
        yarns.send(ada, t.id(), "again");
        assertTrue(inbox(bo).isEmpty());   // muted stays put
    }

    @Test
    void pinnedThreadsSortFirstAndUnreadClearsOnRead() {
        ThreadView older = yarns.createGroup(ada, Tier.WORKSPACE, "Older", List.of(bo.getUsername()));
        ThreadView newer = yarns.createGroup(ada, Tier.WESPACE, "Newer", List.of(bo.getUsername()));
        assertEquals("Newer", inbox(bo).get(0).name());
        yarns.setPrefs(bo, older.id(), new Prefs(null, true, null));
        assertEquals("Older", inbox(bo).get(0).name());

        assertTrue(inbox(bo).get(0).unread() > 0);
        yarns.markRead(bo, older.id());
        assertEquals(0, inbox(bo).get(0).unread());
        assertEquals(1, yarns.list(bo, false, Tier.WESPACE, null).size());   // tier filter
        assertEquals(1, yarns.list(bo, false, null, "newer").size());        // search
    }

    @Test
    void strangersCannotSeeAThread() {
        ThreadView t = yarns.createGroup(ada, Tier.WESPACE, "Private", List.of(bo.getUsername()));
        assertThrows(YarnException.class, () -> yarns.history(cy, t.id(), null, 50));
        assertThrows(YarnException.class, () -> yarns.send(cy, t.id(), "hi"));
    }
}
