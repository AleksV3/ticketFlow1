package com.ticketflow1.ticketing.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeEvents {
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(error -> clients.remove(emitter));
        try { emitter.send(SseEmitter.event().name("connected").data("ready")); }
        catch (IOException error) { clients.remove(emitter); }
        return emitter;
    }

    public void ticketsChanged() {
        clients.removeIf(emitter -> {
            try { emitter.send(SseEmitter.event().name("tickets-changed").data("refresh")); return false; }
            catch (IOException | IllegalStateException error) { emitter.complete(); return true; }
        });
    }
}
