package com.example.flags;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventsController {

    private final ChangeBroadcaster broadcaster;

    public EventsController(ChangeBroadcaster broadcaster) { this.broadcaster = broadcaster; }

    /**
     * A server-sent event stream of flag changes for any signed-in user. The browser's EventSource can't send an
     * Authorization header, so the dashboard reads this stream with fetch instead (see changeFeed.js).
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('VIEWER')")
    public ResponseEntity<SseEmitter> stream(Authentication authentication) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no") // stops nginx from holding events back
                .body(broadcaster.subscribe(authentication.getName()));
    }
}
