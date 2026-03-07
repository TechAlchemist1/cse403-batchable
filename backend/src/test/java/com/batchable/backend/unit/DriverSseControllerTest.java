package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.batchable.backend.EventSource.DriverSseController;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.service.DriverService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class DriverSseControllerTest {

  @Mock
  private DriverService driverService;

  private DriverSseController controller;

  @BeforeEach
  void setUp() {
    controller = new DriverSseController(driverService);
  }

  // ------------------ subscribe tests ------------------

  @Test
  void subscribe_validToken_addsEmitterToMap() {
    // Given
    String token = "valid-token";
    Driver driver = new Driver(123L, 1L, "John", "555-1234", true);
    when(driverService.getDriverByToken(token)).thenReturn(driver);

    // When
    SseEmitter emitter = controller.subscribe(token);

    // Then
    Map<Long, List<SseEmitter>> emitters = controller.getEmitters();
    assertTrue(emitters.containsKey(driver.id));
    List<SseEmitter> list = emitters.get(driver.id);
    assertEquals(1, list.size());
    assertSame(emitter, list.get(0));
  }

  @Test
  void subscribe_invalidToken_throwsNotFound() {
    // Given
    String token = "invalid-token";
    when(driverService.getDriverByToken(token)).thenThrow(new IllegalArgumentException());

    // When / Then
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.subscribe(token));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertTrue(ex.getReason().contains("Driver not found"));
  }

  @Test
  void subscribe_registersOnCompletionCallbackThatRemovesEmitter() throws Exception {
    // Given
    String token = "valid-token";
    Driver driver = new Driver(123L, 1L, "John", "555-1234", true);
    when(driverService.getDriverByToken(token)).thenReturn(driver);

    // Mock the SseEmitter constructor
    try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
      // When
      SseEmitter emitter = controller.subscribe(token);

      // Get the mocked emitter instance
      SseEmitter mockEmitter = mocked.constructed().get(0);

      // Capture the Runnable passed to onCompletion
      ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
      verify(mockEmitter).onCompletion(captor.capture());
      Runnable onCompletion = captor.getValue();

      // Verify the emitter was added to the map
      Map<Long, List<SseEmitter>> emitters = controller.getEmitters();
      assertTrue(emitters.containsKey(driver.id));
      assertEquals(1, emitters.get(driver.id).size());

      // When the callback runs
      onCompletion.run();

      // Then the emitter is removed
      assertFalse(emitters.containsKey(driver.id));
    }
  }
  // ------------------ refreshDriverData tests ------------------

  @Test
  void refreshDriverData_sendsEventToAllEmitters() throws IOException {
    // Given
    long driverId = 123L;
    SseEmitter emitter1 = mock(SseEmitter.class);
    SseEmitter emitter2 = mock(SseEmitter.class);
    controller.getEmitters().put(driverId, List.of(emitter1, emitter2));

    // When
    controller.refreshDriverData(driverId);

    // Then
    verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void refreshDriverData_removesEmitterOnIOException() throws IOException {
    // Given
    long driverId = 123L;
    SseEmitter goodEmitter = mock(SseEmitter.class);
    SseEmitter badEmitter = mock(SseEmitter.class);
    doThrow(new IOException("client disconnected")).when(badEmitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    controller.getEmitters().put(driverId,
        new CopyOnWriteArrayList<>(List.of(goodEmitter, badEmitter)));

    // When
    controller.refreshDriverData(driverId);

    // Then
    verify(goodEmitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(badEmitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(badEmitter).complete();
  }

  // ------------------ findAndRemove tests ------------------

  @Test
  void findAndRemove_removesSpecifiedEmitter() {
    // Given
    long driverId = 123L;
    SseEmitter emitter1 = new SseEmitter();
    SseEmitter emitter2 = new SseEmitter();
    controller.getEmitters().put(driverId, new CopyOnWriteArrayList<>(List.of(emitter1, emitter2)));

    // When
    controller.findAndRemove(driverId, emitter1);

    // Then
    Map<Long, List<SseEmitter>> emitters = controller.getEmitters();
    assertTrue(emitters.containsKey(driverId));
    List<SseEmitter> list = emitters.get(driverId);
    assertEquals(1, list.size());
    assertSame(emitter2, list.get(0));
  }

  @Test
  void findAndRemove_removesDriverEntryWhenListEmpty() {
    // Given
    long driverId = 123L;
    SseEmitter emitter = new SseEmitter();
    controller.getEmitters().put(driverId, new CopyOnWriteArrayList<>(List.of(emitter)));

    // When
    controller.findAndRemove(driverId, emitter);

    // Then
    assertFalse(controller.getEmitters().containsKey(driverId));
  }
}
