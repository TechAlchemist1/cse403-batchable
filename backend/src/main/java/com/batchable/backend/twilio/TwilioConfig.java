package com.batchable.backend.twilio;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

/**
 * Configures and initializes the Twilio SDK using credentials from application properties. Active
 * for all profiles except "test" so tests do not require real Twilio credentials.
 */
@Configuration
public class TwilioConfig {

  @Value("${twilio.account-sid}")
  private String accountSid;

  @Value("${twilio.auth-token}")
  private String authToken;

  @Value("${twilio.phone-number}")
  private String phoneNumber;

  @Value("${twilio.driver-phone-number}")
  private String driverPhoneNumber;

  /** Initializes the Twilio client with account SID and auth token after the bean is created. */
  @PostConstruct
  public void init() {
    Twilio.init(accountSid, authToken);
  }

  /** Returns the configured Twilio phone number used for sending SMS. */
  public String getPhoneNumber() {
    return phoneNumber;
  }

  /** Returns the configured Twilio phone number used for receiving SMS. */
  public String getDriverPhoneNumber() {
    return driverPhoneNumber;
  }
}
