package com.ticketflow1.ticketing.realtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

@Service
public class RealtimeEvents {
    private final Set<DeferredResult<ResponseEntity<Void>>> clients = ConcurrentHashMap.newKeySet();

    public DeferredResult<ResponseEntity<Void>> subscribe() {
        DeferredResult<ResponseEntity<Void>> result =
                new DeferredResult<>(25_000L, ResponseEntity.noContent().build());
        clients.add(result);
        result.onCompletion(() -> clients.remove(result));
        result.onTimeout(() -> clients.remove(result));
        result.onError(error -> clients.remove(result));
        return result;
    }

    public void ticketsChanged() {
        clients.forEach(result -> result.setResult(ResponseEntity.ok().build()));
        clients.clear();
    }
}
