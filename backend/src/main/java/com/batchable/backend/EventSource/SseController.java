package com.batchable.backend.EventSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class SseController {

  // Store emitters per restaurant (thread-safe)
  private final Map<Long, List<SseEmitter>> emitters =
      new ConcurrentHashMap<Long, List<SseEmitter>>();

  @GetMapping("/sse/orders/{restaurantId}")
  public SseEmitter subscribe(@PathVariable Long restaurantId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // no timeout
    emitters.computeIfAbsent(restaurantId, a -> new CopyOnWriteArrayList<SseEmitter>()).add(emitter);

    // Remove emitter when completed or times out
    emitter.onCompletion(() -> findAndRemove(restaurantId, emitter));
    return emitter;
  }

  // Method to broadcast refresh signal to a specific restaurant
  public void refreshOrderData(Long restaurantId) {
    List<SseEmitter> emitterList = emitters.get(restaurantId);
    if (emitterList == null) {
      return;
    }
    System.out.println("SSE EMITTER FOR RID " + restaurantId + " NUM EMITTERS " + emitterList.size());

    for (SseEmitter emitter : emitterList) {
      try {
        emitter.send(SseEmitter.event().name("refresh").data("")); // empty payload, just a signal
      } catch (IOException e) {
        emitter.complete(); // client disconnected
      }
    }
  }

  /** Removes the emitter 'emitter' specified by 'restaurantId' in 'emitters' */
  public void findAndRemove(long restaurantId, SseEmitter emitter) {
    List<SseEmitter> emitterList = emitters.get(restaurantId);
    emitterList.remove(emitter);
    if (emitterList.isEmpty()) {
      emitters.remove(restaurantId);
    }
  }

  public Map<Long, List<SseEmitter>> getEmitters() {
    return this.emitters;
  }
}
