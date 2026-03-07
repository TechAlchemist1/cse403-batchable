package com.batchable.backend.EventSource;

import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class DriverSseController {

  private final DriverService driverService;

  // Store emitters per restaurant (thread-safe)
  private final Map<Long, List<SseEmitter>> emitters =
      new ConcurrentHashMap<Long, List<SseEmitter>>();

  public DriverSseController(DriverService driverService) {
      this.driverService = driverService;
  }

  @GetMapping("/sse/orders/token/{token}")
  public SseEmitter subscribe(@PathVariable String token) {
    Driver driver;
    try {
      driver = driverService.getDriverByToken(token);
    } catch (IllegalArgumentException e) {
      // token does not correspond to a driver
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found. Given token: " + token);
    }

    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // no timeout
    emitters.computeIfAbsent(driver.id, a -> new CopyOnWriteArrayList<SseEmitter>()).add(emitter);

    // Remove emitter when completed or times out
    emitter.onCompletion(() -> findAndRemove(driver.id, emitter));
    return emitter;
  }

  // Method to broadcast refresh signal for a specific driver
  public void refreshDriverData(Long driverId) {
    List<SseEmitter> emitterList = emitters.get(driverId);
    if (emitterList == null) {
      return;
    }
    for (SseEmitter emitter : emitterList) {
      try {
        emitter.send(SseEmitter.event().name("refresh").data(""));
      } catch (IOException e) {
        emitter.complete(); // client disconnected
      }
    }
  }

  /** Removes the emitter 'emitter' specified by 'driverId' in 'emitters' */
  public void findAndRemove(long driverId, SseEmitter emitter) {
    List<SseEmitter> emitterList = emitters.get(driverId);
    emitterList.remove(emitter);
    if (emitterList.isEmpty()) {
      emitters.remove(driverId);
    }
  }

  public Map<Long, List<SseEmitter>> getEmitters() {
    return this.emitters;
  }
}
