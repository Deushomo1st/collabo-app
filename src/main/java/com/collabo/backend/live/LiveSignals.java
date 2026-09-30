package com.collabo.backend.live;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Change out": the one place services announce that something changed. A signal carries ids and never content: the browser
 * refetches the thing over the normal API, so permissions and privacy stay where they already are, and a missed signal corrupts nothing.
 * Sent only after the change is committed, so nobody is told about something the database might still roll back.
 */
@Component
public class LiveSignals {

    private final LiveHub hub;

    public LiveSignals(LiveHub hub) { this.hub = hub; }

    /** A yarn landed in a Yarnspace: everyone listed should refresh that thread, and acknowledge delivery. */
    public void yarn(UUID thread, UUID yarn, Collection<UUID> users) {
        send(users, "{\"t\":\"yarn\",\"thread\":\"" + thread + "\",\"yarn\":\"" + yarn + "\"}");
    }

    /** Delivered or read marks moved in a Yarnspace: the listed people should refresh their ticks. */
    public void receipt(UUID thread, Collection<UUID> users) {
        send(users, "{\"t\":\"receipt\",\"thread\":\"" + thread + "\"}");
    }

    /** Someone's notifications changed (a new one, one settled, or read elsewhere): their bell should look again. */
    public void notification(UUID user) { send(java.util.List.of(user), "{\"t\":\"notification\"}"); }

    private void send(Collection<UUID> users, String json) {
        var accounts = users.stream().map(LiveHub::userKey).collect(Collectors.toSet());
        if (accounts.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { hub.send(accounts, json); }
            });
        } else hub.send(accounts, json);
    }
}
