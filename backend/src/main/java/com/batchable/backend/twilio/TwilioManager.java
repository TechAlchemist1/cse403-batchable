package com.batchable.backend.twilio;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Twilio-backed implementation of {@link TwilioManager}.
 *
 * <p>
 * This service is responsible for sending SMS notifications via the Twilio API. It reacts to
 * batch-related events (e.g., a batch becoming active) and constructs human-readable messages,
 * including Google Maps route links, for drivers.
 *
 * <p>
 * This class should NOT control batching logic itself; it only reacts to events emitted elsewhere
 * (e.g., by a BatchingManager).
 */
@Service
public class TwilioManager {

  private final DriverService driverService;

  /** Used to fetch batch and order data needed to construct messages. */
  private final DbOrderService dbOrderService;

  /** Twilio configuration (e.g., "from" phone number). */
  private final TwilioConfig config;

  public TwilioManager(TwilioConfig config, DbOrderService dbOrderService,
      DriverService driverService) {
    this.config = config;
    this.dbOrderService = dbOrderService;
    this.driverService = driverService;
  }

  /**
   * Handler invoked when an existing batch changes (e.g., route updates).
   */
  public void handleBatchChange(long batchId, String restaurantAddress) {
    // TODO: Implement batch update notification logic
  }

  /**
   * Handler invoked when a new batch becomes active.
   *
   * <p>
   * Fetches the batch, constructs a Google Maps route for the batch, and prepares an SMS
   * notification for the assigned driver.
   *
   * @param batchId ID of the newly active batch
   * @param restaurantAddress address used as both route origin and destination
   */
  public void handleNewBatch(long batchId, String restaurantAddress) {
    Batch batch = dbOrderService.getBatch(batchId);
    Driver driver = driverService.getDriver(batch.driverId);

    // TODO: eventually replace with driver.phoneNumber
    String driverPhoneNumber = config.getDriverPhoneNumber();

    String batchRouteLink = getBatchRouteLink(batchId, restaurantAddress);
    String message = "Driver id " + batch.driverId + " you have been assigned batch id " + batchId
        + ".\n Route link: " + batchRouteLink;

    // Temporary logging for debugging / verification
    System.out.println(
        "BATCH ACTIVATED TWILIO MESSAGE, would be sent to " + driverPhoneNumber + ": " + message);
    sendMessage(driverPhoneNumber, message);
  }

  /**
   * Constructs a Google Maps directions link for a batch. Starts at the restaurant Visits each
   * order destination in batch order Returns to the restaurant
   *
   * @param batchId ID of the batch
   * @param restaurantAddress starting and ending address
   * @return a Google Maps directions URL
   * @throws IllegalStateException if the batch has no orders
   */
  public String getBatchRouteLink(long batchId, String restaurantAddress) {
    List<Order> orders = dbOrderService.getBatchOrders(batchId);

    // A batch without orders is considered a logic error
    if (orders == null || orders.isEmpty()) {
      throw new IllegalStateException("Batches must be nonempty, failed for id " + batchId);
    }

    StringBuilder linkBuilder = new StringBuilder("https://www.google.com/maps/dir/?api=1");
    linkBuilder.append("&origin=").append(urlEncodeAddress(restaurantAddress));
    linkBuilder.append("&destination=").append(urlEncodeAddress(restaurantAddress));
    linkBuilder.append("&waypoints=").append(urlEncodeAddress(orders.get(0).destination));

    for (int i = 1; i < orders.size(); i++) {
      linkBuilder.append("|").append(urlEncodeAddress(orders.get(i).destination));
    }
    return linkBuilder.toString();
  }

  /**
   * URL-encodes an address string using UTF-8 so it is safe to include as a query parameter in a
   * Google Maps URL.
   */
  private String urlEncodeAddress(String address) {
    return URLEncoder.encode(address, StandardCharsets.UTF_8);
  }

  /**
   * Sends an SMS message via the Twilio API.
   *
   * @param toPhoneNumber recipient phone number
   * @param message message body
   */
  public void sendMessage(String toPhoneNumber, String message) {
    Message
        .creator(new PhoneNumber(toPhoneNumber), new PhoneNumber(config.getPhoneNumber()), message)
        .create();
  }
}
