package com.batchable.backend.twilio;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * Service that sends SMS messages via the Twilio API.
 * Uses the configured Twilio phone number as the sender.
 */
@Service
public class SmsService {

    /** Twilio phone number used as the "from" number for outgoing SMS (from application config). */
    @Value("${twilio.phone-number}")
    private String fromNumber;

    /**
     * Sends an SMS to the given number with the given message.
     *
     * @param toNumber  recipient phone number (E.164 format recommended)
     * @param message   body of the text message
     */
    public void sendSms(String toNumber, String message) {
        Message.creator(
                new PhoneNumber(toNumber),
                new PhoneNumber(fromNumber),
                message
        ).create();
    }
}
