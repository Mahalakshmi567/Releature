package com.example.flags;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pushes change notifications to open dashboards over server-sent events.
 *
 * Notifications go out after the change has committed, and after the Redis cache entries for it were dropped, so a
 * dashboard that reacts by reading never sees the old state. Sending happens on one background thread, so a slow
 * browser can't hold up the request that made the change.
 *
 * Streams are kept in memory: a dashboard hears about changes handled by the same backend instance it is connected to.
 */
@Component
public class ChangeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ChangeBroadcaster.class);

    /** A stream ends after this long and the browser reconnects, which checks its token again. */
    static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;
    static final int MAX_STREAMS_PER_USER = 5;

    /** Where events are written. The real one wraps an SseEmitter; tests use a fake. */
    interface Sink {
        void send(String event, String id, Object data) throws IOException;
        void comment(String text) throws IOException;
        void complete();
    }

    record Subscriber(String username, Sink sink) {}

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "change-broadcaster");
        t.setDaemon(true);
        return t;
    });

    /** Opens a stream for a signed-in user. The first event, "ready", tells the browser it is connected. */
    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Subscriber subscriber = register(username, new EmitterSink(emitter));
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> {
            subscribers.remove(subscriber);
            emitter.complete();
        });
        emitter.onError(e -> subscribers.remove(subscriber));
        submit(() -> send(subscriber, "ready", null, Map.of("serverTime", Instant.now().toString())));
        return emitter;
    }

    /** Adds a stream, closing the user's oldest one if they already have the maximum open (for example many tabs). */
    Subscriber register(String username, Sink sink) {
        List<Subscriber> mine = subscribers.stream().filter(s -> s.username().equals(username)).toList();
        for (int i = 0; i <= mine.size() - MAX_STREAMS_PER_USER; i++) {
            subscribers.remove(mine.get(i));
            mine.get(i).sink().complete();
        }
        Subscriber subscriber = new Subscriber(username, sink);
        subscribers.add(subscriber);
        return subscriber;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(Integer.MAX_VALUE) // after the cache eviction listener
    public void onChange(ChangeNotification notification) {
        submit(() -> deliver(notification));
    }

    void deliver(ChangeNotification notification) {
        for (Subscriber s : subscribers) send(s, "flag-changed", notification.id(), notification);
    }

    /** Keeps idle streams from being closed by proxies, and finds browsers that went away. */
    @Scheduled(fixedRate = 25_000)
    void scheduledHeartbeat() { submit(this::heartbeat); }

    void heartbeat() {
        for (Subscriber s : subscribers) {
            try {
                s.sink().comment("keepalive");
            } catch (IOException | RuntimeException e) {
                drop(s);
            }
        }
    }

    int subscriberCount() { return subscribers.size(); }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
        subscribers.forEach(s -> s.sink().complete());
        subscribers.clear();
    }

    private void send(Subscriber s, String event, String id, Object data) {
        try {
            s.sink().send(event, id, data);
        } catch (IOException | RuntimeException e) {
            drop(s); // the browser closed the page or lost its connection
        }
    }

    private void drop(Subscriber s) {
        subscribers.remove(s);
        try {
            s.sink().complete();
        } catch (RuntimeException ignored) {
            // already closed
        }
    }

    private void submit(Runnable task) {
        try {
            sender.submit(task);
        } catch (RejectedExecutionException e) {
            log.debug("Not sending: the broadcaster is shutting down");
        }
    }

    private static final class EmitterSink implements Sink {
        private final SseEmitter emitter;

        EmitterSink(SseEmitter emitter) { this.emitter = emitter; }

        @Override
        public void send(String event, String id, Object data) throws IOException {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event);
            if (id != null) builder.id(id);
            emitter.send(builder.data(data, MediaType.APPLICATION_JSON));
        }

        @Override
        public void comment(String text) throws IOException { emitter.send(SseEmitter.event().comment(text)); }

        @Override
        public void complete() { emitter.complete(); }
    }
}
