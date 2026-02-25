package com.batchable.backend.unit;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.twilio.TwilioConfig;
import com.batchable.backend.twilio.TwilioManager;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TwilioManagerImpl, focusing on message creation, batch handling, and route URL
 * generation. Uses Mockito to mock external dependencies and static Twilio methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioManagerTest {

  // Mocked dependencies
  @Mock
  private TwilioConfig config; // Provides Twilio phone numbers

  @Mock
  private DbOrderService dbOrderService; // Provides batch and order data

  @Mock
  private DriverService driverService; // Provides driver information

  @Mock
  private MessageCreator messageCreatorMock; // Mocks Twilio Message.creator() static call

  // Argument captors to capture parameters passed to Message.creator
  @Captor
  private ArgumentCaptor<PhoneNumber> toPhoneCaptor;

  @Captor
  private ArgumentCaptor<PhoneNumber> fromPhoneCaptor;

  @Captor
  private ArgumentCaptor<String> messageBodyCaptor;

  // The class under test
  private TwilioManager twilioManager;

  /**
   * Initializes the TwilioManager before each test with mocked dependencies.
   */
  @BeforeEach
  void setUp() {
    twilioManager = new TwilioManager(config, dbOrderService, driverService);
  }

  /**
   * Verifies that handleNewBatch constructs a correct Twilio message for a batch and calls the
   * Twilio Message.creator with the right parameters.
   */
  @Test
  void handleNewBatch_Success() {
    long batchId = 123L;
    String restaurantAddress = "Lynnwood, WA";
    String driverPhoneNumber = "+1234567890";
    String fromPhoneNumber = "+0987654321";

    // Create test batch, driver, and orders
    Batch batch = new Batch(batchId, 42L, "encoded_polyline", Instant.now(), null, false);
    Driver driver = new Driver(42L, 100L, "John Doe", driverPhoneNumber, true);
    Order order1 = new Order(1L, 100L, "University of Washington", "[\"item1\"]", Instant.now(),
        null, null, Order.State.COOKED, false, batchId);
    Order order2 = new Order(2L, 100L, "Oregon", "[\"item2\"]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    List<Order> orders = List.of(order1, order2);

    // Stub dependencies
    when(dbOrderService.getBatch(batchId)).thenReturn(batch);
    when(driverService.getDriver(42L)).thenReturn(driver);
    when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);
    when(config.getPhoneNumber()).thenReturn(fromPhoneNumber);
    when(config.getDriverPhoneNumber()).thenReturn(driverPhoneNumber);

    // Mock the static Twilio Message.creator() call
    try (var mockedStatic = mockStatic(Message.class)) {
      mockedStatic
          .when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
          .thenReturn(messageCreatorMock);

      // Act
      twilioManager.handleNewBatch(batchId, restaurantAddress);

      // Verify correct parameters were used
      mockedStatic.verify(() -> Message.creator(toPhoneCaptor.capture(), fromPhoneCaptor.capture(),
          messageBodyCaptor.capture()));
    }

    // Assert captured parameters are as expected
    PhoneNumber actualTo = toPhoneCaptor.getValue();
    PhoneNumber actualFrom = fromPhoneCaptor.getValue();
    String actualMessage = messageBodyCaptor.getValue();

    assertEquals(driverPhoneNumber, actualTo.getEndpoint());
    assertEquals(fromPhoneNumber, actualFrom.getEndpoint());
    assertTrue(actualMessage.contains("Driver id 42 you have been assigned batch id 123"));
    assertTrue(actualMessage.contains(
        "Route link: https://www.google.com/maps/dir/?api=1&origin=Lynnwood%2C+WA&destination=Lynnwood%2C+WA&waypoints=University+of+Washington|Oregon"));
  }

  /**
   * Tests that getBatchRouteLink generates a correct URL when the batch has only one order.
   */
  @Test
  void getBatchRouteLink_SingleOrder() {
    long batchId = 1L;
    String restaurantAddress = "Seattle, WA";
    Order order = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    List<Order> orders = List.of(order);

    when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    String expectedUrl =
        "https://www.google.com/maps/dir/?api=1&origin=Seattle%2C+WA&destination=Seattle%2C+WA&waypoints=Bellevue%2C+WA";
    String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    assertEquals(expectedUrl, actualUrl);
  }

  /**
   * Tests that getBatchRouteLink generates a correct URL when the batch has multiple orders.
   */
  @Test
  void getBatchRouteLink_MultipleOrders() {
    long batchId = 2L;
    String restaurantAddress = "Seattle, WA";
    Order order1 = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    Order order2 = new Order(2L, 100L, "Redmond, WA", "[]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    Order order3 = new Order(3L, 100L, "Kirkland, WA", "[]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    List<Order> orders = List.of(order1, order2, order3);

    when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    String expectedUrl =
        "https://www.google.com/maps/dir/?api=1&origin=Seattle%2C+WA&destination=Seattle%2C+WA&waypoints=Bellevue%2C+WA|Redmond%2C+WA|Kirkland%2C+WA";
    String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    assertEquals(expectedUrl, actualUrl);
  }

  /**
   * Verifies that getBatchRouteLink throws an exception if the batch has no orders.
   */
  @Test
  void getBatchRouteLink_EmptyBatch_ThrowsException() {
    long batchId = 3L;
    when(dbOrderService.getBatchOrders(batchId)).thenReturn(List.of());

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> twilioManager.getBatchRouteLink(batchId, "address"));
    assertTrue(exception.getMessage().contains("Batches must be nonempty, failed for id 3"));
  }

  /**
   * Verifies that getBatchRouteLink correctly URL-encodes special characters in addresses.
   */
  @Test
  void getBatchRouteLink_EncodesAddressesCorrectly() {
    long batchId = 4L;
    String restaurantAddress = "Café & Bakery, Seattle, WA";
    Order order = new Order(1L, 100L, "Park & Shop, Bellevue, WA", "[]", Instant.now(), null, null,
        Order.State.COOKED, false, batchId);
    List<Order> orders = List.of(order);

    when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    String expectedUrl =
        "https://www.google.com/maps/dir/?api=1&origin=Caf%C3%A9+%26+Bakery%2C+Seattle%2C+WA&destination=Caf%C3%A9+%26+Bakery%2C+Seattle%2C+WA&waypoints=Park+%26+Shop%2C+Bellevue%2C+WA";
    String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    assertEquals(expectedUrl, actualUrl);
  }

  /**
   * Verifies that sendMessage calls Twilio's Message.creator with the correct to/from numbers and
   * message text.
   */
  @Test
  void sendMessage_CallsTwilioCreator() {
    String to = "+1111111111";
    String from = "+2222222222";
    String messageText = "Hello, driver!";
    when(config.getPhoneNumber()).thenReturn(from);

    try (var mockedStatic = mockStatic(Message.class)) {
      mockedStatic
          .when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
          .thenReturn(messageCreatorMock);

      twilioManager.sendMessage(to, messageText);

      // Verify the static Twilio call was made with exact parameters
      mockedStatic
          .verify(() -> Message.creator(new PhoneNumber(to), new PhoneNumber(from), messageText));
    }
  }
}
