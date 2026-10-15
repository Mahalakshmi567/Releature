package com.example.flags;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeBroadcasterTest {

    private static class FakeSink implements ChangeBroadcaster.Sink {
        final List<String> events = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();
        int comments = 0;
        boolean completed = false;
        boolean failing = false;

        @Override public void send(String event, String id, Object data) throws IOException {
            if (failing) throw new IOException("browser went away");
            events.add(event);
            payloads.add(data);
        }
        @Override public void comment(String text) throws IOException {
            if (failing) throw new IOException("browser went away");
            comments++;
        }
        @Override public void complete() { completed = true; }
    }

    private final ChangeBroadcaster broadcaster = new ChangeBroadcaster();

    private final ChangeNotification change = new ChangeNotification("1", "new_checkout", "New Checkout",
            "production", AuditAction.ENVIRONMENT_UPDATED, "alice", "launch", Instant.now(), false);

    @Test
    void everyOpenStreamHearsAboutAChange() {
        FakeSink a = new FakeSink();
        FakeSink b = new FakeSink();
        broadcaster.register("alice", a);
        broadcaster.register("bob", b);

        broadcaster.deliver(change);

        assertEquals(List.of("flag-changed"), a.events);
        assertEquals(List.of("flag-changed"), b.events);
        assertSame(change, a.payloads.get(0));
    }

    @Test
    void aStreamThatFailsIsDroppedAndTheOthersStillGetTheEvent() {
        FakeSink broken = new FakeSink();
        broken.failing = true;
        FakeSink healthy = new FakeSink();
        broadcaster.register("alice", broken);
        broadcaster.register("bob", healthy);

        broadcaster.deliver(change);

        assertEquals(List.of("flag-changed"), healthy.events);
        assertTrue(broken.completed);
        assertEquals(1, broadcaster.subscriberCount());
    }

    @Test
    void heartbeatKeepsStreamsAliveAndRemovesDeadOnes() {
        FakeSink alive = new FakeSink();
        FakeSink dead = new FakeSink();
        dead.failing = true;
        broadcaster.register("alice", alive);
        broadcaster.register("bob", dead);

        broadcaster.heartbeat();

        assertEquals(1, alive.comments);
        assertEquals(1, broadcaster.subscriberCount());
    }

    @Test
    void aUserWithTooManyOpenStreamsLosesTheOldestOne() {
        List<FakeSink> sinks = new ArrayList<>();
        for (int i = 0; i < ChangeBroadcaster.MAX_STREAMS_PER_USER + 1; i++) {
            FakeSink s = new FakeSink();
            sinks.add(s);
            broadcaster.register("alice", s);
        }

        assertTrue(sinks.get(0).completed);
        assertFalse(sinks.get(1).completed);
        assertEquals(ChangeBroadcaster.MAX_STREAMS_PER_USER, broadcaster.subscriberCount());
    }

    @Test
    void otherUsersStreamsAreNotCountedAgainstTheLimit() {
        for (int i = 0; i < ChangeBroadcaster.MAX_STREAMS_PER_USER; i++) broadcaster.register("alice", new FakeSink());
        FakeSink bob = new FakeSink();
        broadcaster.register("bob", bob);
        assertFalse(bob.completed);
        assertEquals(ChangeBroadcaster.MAX_STREAMS_PER_USER + 1, broadcaster.subscriberCount());
    }
}
