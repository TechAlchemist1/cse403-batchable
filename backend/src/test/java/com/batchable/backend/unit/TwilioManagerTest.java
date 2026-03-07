package com.batchable.backend.unit;

import com.batchable.backend.EventSource.DriverSsePublisher;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioManagerTest {

    @Mock private TwilioConfig config;
    @Mock private DbOrderService dbOrderService;
    @Mock private DriverService driverService;
    @Mock private DriverSsePublisher driverSsePublisher;
    @Mock private MessageCreator messageCreatorMock;

    @Captor private ArgumentCaptor<PhoneNumber> toPhoneCaptor;
    @Captor private ArgumentCaptor<PhoneNumber> fromPhoneCaptor;
    @Captor private ArgumentCaptor<String> messageBodyCaptor;

    private TwilioManager twilioManager;

    @BeforeEach
    void setUp() {
        twilioManager = new TwilioManager(config, dbOrderService, driverService, driverSsePublisher);
    }

    @Test
    void handleNewBatch_Success() {
        long batchId = 123L;
        long driverId = 42L;
        String restaurantAddress = "Lynnwood, WA";
        String driverPhoneNumber = "+1234567890";
        String fromPhoneNumber = "+0987654321";
        String driverToken = "abc123token";

        Batch batch = new Batch(batchId, driverId, "encoded_polyline", Instant.now(), null, false);
        Driver driver = new Driver(driverId, 100L, "John Doe", driverPhoneNumber, true);
        Order order1 = new Order(1L, 100L, "University of Washington", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
        Order order2 = new Order(2L, 100L, "Oregon", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
        List<Order> orders = List.of(order1, order2);

        when(dbOrderService.getBatch(batchId)).thenReturn(batch);
        when(driverService.getDriver(driverId)).thenReturn(driver);
        when(driverService.getDriverToken(driverId)).thenReturn(driverToken);
        when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);
        when(config.getPhoneNumber()).thenReturn(fromPhoneNumber);
        when(config.getDriverPhoneNumber()).thenReturn(driverPhoneNumber);

        try (var mockedStatic = mockStatic(Message.class)) {
            mockedStatic.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                    .thenReturn(messageCreatorMock);

            twilioManager.handleNewBatch(batchId);

            // Verify SMS was sent with correct content
            mockedStatic.verify(() -> Message.creator(toPhoneCaptor.capture(), fromPhoneCaptor.capture(), messageBodyCaptor.capture()));
            assertEquals(driverPhoneNumber, toPhoneCaptor.getValue().getEndpoint());
            assertEquals(fromPhoneNumber, fromPhoneCaptor.getValue().getEndpoint());
            String expectedMessage = "Driver John Doe (id 42) you have been assigned a new batch. View here http://localhost:5173/route/" + driverToken;
            assertEquals(expectedMessage, messageBodyCaptor.getValue());

            // Verify SSE update was triggered (via handleBatchChange)
            verify(driverSsePublisher).refreshDriverData(eq(driverId));
        }
    }

    @Test
    void handleBatchChange_UpdatesSse() {
        long batchId = 123L;
        long driverId = 42L;
        String restaurantAddress = "Seattle, WA";
        Batch batch = new Batch(batchId, driverId, "polyline", Instant.now(), null, false);
        Driver driver = new Driver(driverId, 100L, "Jane Doe", "+5555555555", true);
        Order order = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
        List<Order> orders = List.of(order);

        when(dbOrderService.getBatch(batchId)).thenReturn(batch);
        when(driverService.getDriver(driverId)).thenReturn(driver);
        when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

        twilioManager.handleBatchChange(batchId);

        String expectedRouteLink = "https://www.google.com/maps/dir/?api=1&origin=Current+Location&destination=Seattle%2C+WA&waypoints=Bellevue%2C+WA";
        verify(driverSsePublisher).refreshDriverData(driverId);
    }

    // @Test
    // void getBatchRouteLink_SingleOrder() {
    //     long batchId = 1L;
    //     String restaurantAddress = "Seattle, WA";
    //     Order order = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
    //     List<Order> orders = List.of(order);

    //     when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    //     String expectedUrl = "https://www.google.com/maps/dir/?api=1&origin=Current+Location&destination=Seattle%2C+WA&waypoints=Bellevue%2C+WA";
    //     String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    //     assertEquals(expectedUrl, actualUrl);
    // }

    // @Test
    // void getBatchRouteLink_MultipleOrders() {
    //     long batchId = 2L;
    //     String restaurantAddress = "Seattle, WA";
    //     Order order1 = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
    //     Order order2 = new Order(2L, 100L, "Redmond, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
    //     Order order3 = new Order(3L, 100L, "Kirkland, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
    //     List<Order> orders = List.of(order1, order2, order3);

    //     when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    //     String expectedUrl = "https://www.google.com/maps/dir/?api=1&origin=Current+Location&destination=Seattle%2C+WA&waypoints=Bellevue%2C+WA|Redmond%2C+WA|Kirkland%2C+WA";
    //     String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    //     assertEquals(expectedUrl, actualUrl);
    // }

    // @Test
    // void getBatchRouteLink_AllOrdersDelivered_ReturnsNoWaypoints() {
    //     long batchId = 3L;
    //     String restaurantAddress = "Seattle, WA";
    //     Order order = new Order(1L, 100L, "Bellevue, WA", "[]", Instant.now(), null, null, State.DELIVERED, false, batchId);
    //     List<Order> orders = List.of(order);

    //     when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    //     String expectedUrl = "https://www.google.com/maps/dir/?api=1&origin=Current+Location&destination=Seattle%2C+WA";
    //     String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    //     assertEquals(expectedUrl, actualUrl);
    // }

    // @Test
    // void getBatchRouteLink_EncodesAddressesCorrectly() {
    //     long batchId = 4L;
    //     String restaurantAddress = "Café & Bakery, Seattle, WA";
    //     Order order = new Order(1L, 100L, "Park & Shop, Bellevue, WA", "[]", Instant.now(), null, null, State.COOKED, false, batchId);
    //     List<Order> orders = List.of(order);

    //     when(dbOrderService.getBatchOrders(batchId)).thenReturn(orders);

    //     String expectedUrl = "https://www.google.com/maps/dir/?api=1&origin=Current+Location&destination=Caf%C3%A9+%26+Bakery%2C+Seattle%2C+WA&waypoints=Park+%26+Shop%2C+Bellevue%2C+WA";
    //     String actualUrl = twilioManager.getBatchRouteLink(batchId, restaurantAddress);

    //     assertEquals(expectedUrl, actualUrl);
    // }

    @Test
    void sendMessage_CallsTwilioCreator() {
        String to = "+1111111111";
        String from = "+2222222222";
        String messageText = "Hello, driver!";
        when(config.getPhoneNumber()).thenReturn(from);

        try (var mockedStatic = mockStatic(Message.class)) {
            mockedStatic.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                    .thenReturn(messageCreatorMock);

            twilioManager.sendMessage(to, messageText);

            mockedStatic.verify(() -> Message.creator(new PhoneNumber(to), new PhoneNumber(from), messageText));
        }
    }
}